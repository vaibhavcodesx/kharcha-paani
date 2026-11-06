package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.drafts.DraftLoadResult
import com.vaibhav.kharchapaani.data.drafts.EntryDraftAtomicStore
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AiSettings
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.domain.EntrySaveCheckpoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Copies a closed real ledger to a fresh store slot: no live-writer tombstone can mask recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryDraftAdversarialTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val main = StandardTestDispatcher()
    private val userId = UUID.randomUUID().toString()
    private val databaseA = "entry-adversarial-a-$userId.db"
    private val databaseB = "entry-adversarial-b-$userId.db"
    private val draftDirectory = File(app.filesDir, "entry-drafts")
    private val settings = AppSettings(app)
    private val preferences = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private var hadEnabled = false
    private var previousEnabled = true
    private var previousConsent: Boolean? = null
    private var captured = false
    private var installed = false
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private var store: ViewModelStore? = null
    private var screen: CoroutineScope? = null
    private val ownedJobs = mutableListOf<Job>()
    private val requests = AtomicInteger()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(403).message("Unexpected AI request in local draft recovery")
            .body("{}".toResponseBody()).build()
    }.build()

    @Before fun setup(): Unit = runBlocking {
        Dispatchers.setMain(main)
        installed = true
        hadEnabled = preferences.contains("enabled")
        previousEnabled = preferences.getBoolean("enabled", true)
        previousConsent = settings.aiNoticeAccepted.first()
        captured = true
        AiSettings(app).setEnabled(true)
        settings.setAiNoticeAccepted(true)
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        openDatabase(databaseA)
    }

    private fun openDatabase(name: String) {
        db = Room.databaseBuilder(app, AppDatabase::class.java, name).allowMainThreadQueries().build()
    }

    private fun pump() {
        main.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        main.scheduler.runCurrent()
    }

    private suspend fun until(ready: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            pump()
            if (ready()) return@withTimeout
            delay(10)
        }
    }

    private suspend fun openViewModel(expectedRows: Int) {
        val nextStore = ViewModelStore().also { store = it }
        vm = MainViewModel(app, db, userId, geminiRepo = GeminiRepository(client = client,
            endpoint = { "https://example.invalid/models/$it:generateContent" },
            accessToken = { "test-token" }, configured = true, aiAllowed = { AiSettings(app).enabledNow }))
        nextStore.put("main", vm)
        ownedJobs += vm.viewModelScope.coroutineContext[Job]!!
        val nextScreen = CoroutineScope(SupervisorJob() + main).also { screen = it }
        ownedJobs += nextScreen.coroutineContext[Job]!!
        nextScreen.launch { vm.rawTransactions.collect {} }
        nextScreen.launch { vm.people.collect {} }
        nextScreen.launch { vm.rawGroups.collect {} }
        until { !vm.isRestoringDraft.value && vm.rawTransactions.value.size == expectedRows }
    }

    private suspend fun retire() {
        screen?.cancel()
        store?.clear()
        until { ownedJobs.all { it.isCompleted } }
        screen = null
        store = null
    }

    @Test fun `an interrupted successful new save deleted before recovery is never resurrected`(): Unit = runBlocking {
        openViewModel(0)
        vm.openManualAdd()
        vm.updateAmount("125.50")
        vm.updateMerchant("Saved then remotely deleted")
        vm.updateNote("Preserve my recovered raw note")
        pump()
        retire()
        val sourceJournal = EntryDraftAtomicStore.open(draftDirectory, databaseA)
        val loaded = sourceJournal.load()
        assertTrue("The initial VM must produce an actual durable draft", loaded is DraftLoadResult.Loaded)
        val capturedDraft = (loaded as DraftLoadResult.Loaded).snapshot
        sourceJournal.retire().await().getOrThrow()

        // Exercise the real Save producer, including any same-transaction recovery metadata.
        openViewModel(0)
        assertTrue(vm.isConfirmSheetOpen.value)
        assertEquals(capturedDraft.entry.amount, vm.amount.value)
        vm.saveCurrentTransaction(askForGroup = false)
        until { !vm.isConfirmSheetOpen.value && db.transactionDao().getAllTransactionsDirect().size == 1 }
        val saved = db.transactionDao().getAllTransactionsDirect().single()
        assertEquals(capturedDraft.saveRemoteId, saved.remoteId)
        assertEquals(125.50, saved.amount, 0.0)
        retire()
        // Ensure the closed DB copy contains the actual producer's committed Room metadata.
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("No reader may prevent the closed-ledger snapshot", 0, cursor.getInt(0))
        }
        db.close()
        val destination = app.getDatabasePath(databaseB)
        assertFalse(destination.exists())
        app.getDatabasePath(databaseA).copyTo(destination)
        openDatabase(databaseB)
        assertEquals(listOf(saved), db.transactionDao().getAllTransactionsDirect())
        db.transactionDao().deleteTransactionById(saved.id)
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())

        // A crash left the accepted Save checkpoint on disk, not a hand-written DB marker.
        val interrupted = capturedDraft.copy(revision = capturedDraft.revision + 1,
            savingEntry = EntrySaveCheckpoint(capturedDraft.revision, capturedDraft.saveRemoteId,
                capturedDraft.entry, capturedDraft.photo, capturedDraft.edit, capturedDraft.lockedShares,
                capturedDraft.hadBillPhotoOnOpen, capturedDraft.groupChoiceIsExplicit))
        val crashJournal = EntryDraftAtomicStore.open(draftDirectory, databaseB)
        crashJournal.save(interrupted).await().getOrThrow()
        crashJournal.retire().await().getOrThrow()
        openViewModel(0)
        assertTrue(vm.isConfirmSheetOpen.value)
        assertEquals(capturedDraft.entry.amount, vm.amount.value)
        assertEquals(capturedDraft.entry.merchant, vm.merchant.value)
        assertEquals(capturedDraft.entry.note, vm.note.value)
        val owner = vm.viewModelScope.coroutineContext[Job]!!
        val prior = owner.children.toSet()
        vm.saveCurrentTransaction(askForGroup = false)
        val attemptedSave = owner.children.filter { it !in prior }.toList()
        until { attemptedSave.all { it.isCompleted } }
        assertTrue("Retry must not recreate an expense whose original Save committed before its deletion",
            db.transactionDao().getAllTransactionsDirect().isEmpty())
        assertEquals(saved.id, vm.editingTransactionId.value)
        assertEquals(capturedDraft.entry.note, vm.note.value)
        assertTrue(vm.isConfirmSheetOpen.value)
        assertEquals(0, requests.get())
    }

    @After fun cleanup(): Unit = runBlocking {
        try {
            if (installed) retire()
            for (name in listOf(databaseA, databaseB)) {
                // A fresh owner drains accepted IO before deleting this test's exact private files.
                EntryDraftAtomicStore.open(draftDirectory, name).retire().await().getOrThrow()
                for (suffix in listOf("", ".bak", ".new")) File(draftDirectory, "$name.draft.json$suffix").delete()
            }
        } finally {
            try {
                if (::db.isInitialized) db.close()
                app.deleteDatabase(databaseA)
                app.deleteDatabase(databaseB)
                ReceiptStore.forAccount(app, userId).dir.deleteRecursively()
                if (captured) {
                    previousConsent?.let { settings.setAiNoticeAccepted(it) }
                    val edit = preferences.edit()
                    if (hadEnabled) edit.putBoolean("enabled", previousEnabled) else edit.remove("enabled")
                    assertTrue(edit.commit())
                }
            } finally {
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
                if (installed) Dispatchers.resetMain()
            }
        }
    }
}
