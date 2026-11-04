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
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AiSettings
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.domain.EntryDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/** Recreates actual VMs and reopens their file-backed Room DB; this is not an Android OS kill. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryDraftRecoveryTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val main = StandardTestDispatcher()
    private val userId = UUID.randomUUID().toString()
    private val databaseName = "entry-draft-recovery-$userId.db"
    private val settings = AppSettings(app)
    private val preferences = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private var captured = false
    private var hadEnabled = false
    private var previousEnabled = true
    private var previousConsent: Boolean? = null
    private var installed = false
    private val requests = AtomicInteger()
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(403).message("Unexpected AI request during draft recovery")
            .body("{}".toResponseBody()).build()
    }.build()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private var store: ViewModelStore? = null
    private var screen: CoroutineScope? = null
    private val retiredJobs = mutableListOf<Job>()

    @Before fun setup(): Unit = runBlocking {
        Dispatchers.setMain(main)
        installed = true
        hadEnabled = preferences.contains("enabled")
        previousEnabled = preferences.getBoolean("enabled", true)
        captured = true
        previousConsent = settings.aiNoticeAccepted.first()
        // Prior consent plus enabled AI prevents the no-network assertion passing just on a gate.
        AiSettings(app).setEnabled(true)
        settings.setAiNoticeAccepted(true)
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        openDatabase()
        db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF"))
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Ankit", avatarColor = "#B5EAD7"))
        db.groupDao().insertGroup(GroupEntity(id = 7, name = "Draft trip"))
        db.groupDao().insertGroupMembers(listOf(-1L, 1L, 2L).map { GroupMemberEntity(groupId = 7, personId = it) })
        openViewModel(expectedTransactions = 0)
    }

    private fun openDatabase() {
        db = Room.databaseBuilder(app, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries().build()
    }

    private suspend fun openViewModel(expectedTransactions: Int) {
        val nextStore = ViewModelStore().also { store = it }
        vm = MainViewModel(app, db, userId, geminiRepo = GeminiRepository(client = client,
            endpoint = { "https://example.invalid/models/$it:generateContent" },
            accessToken = { "test-token" }, configured = true, aiAllowed = { AiSettings(app).enabledNow }))
        nextStore.put("main", vm)
        retiredJobs += vm.viewModelScope.coroutineContext[Job]!!
        val nextScreen = CoroutineScope(SupervisorJob() + main).also { screen = it }
        retiredJobs += nextScreen.coroutineContext[Job]!!
        nextScreen.launch { vm.people.collect {} }
        nextScreen.launch { vm.rawGroups.collect {} }
        nextScreen.launch { vm.groups.collect {} }
        nextScreen.launch { vm.rawTransactions.collect {} }
        until {
            vm.people.value.size == 2 && vm.rawGroups.value.any { it.id == 7L } &&
                vm.groups.value.any { it.group.id == 7L && it.members.size == 2 } &&
                vm.rawTransactions.value.size == expectedTransactions
        }
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

    private suspend fun retireViewModel() {
        if (!installed) return
        screen?.cancel()
        store?.clear()
        until { retiredJobs.all { it.isCompleted } }
        screen = null
        store = null
    }

    private suspend fun recreate(expectedTransactions: Int) {
        retireViewModel()
        db.close()
        openDatabase()
        openViewModel(expectedTransactions)
    }

    private fun snapshot() = EntryDraft(vm.type.value, vm.amount.value, vm.merchant.value,
        vm.category.value, vm.paymentMethod.value, vm.note.value, vm.date.value, vm.groupId.value,
        vm.isSplit.value, vm.splitMode.value, vm.paidByPersonId.value, vm.shares.value)

    private suspend fun requireRecoveredSheet() {
        val opened = withTimeoutOrNull(10_000) {
            while (!vm.isConfirmSheetOpen.value) {
                pump()
                if (!vm.isConfirmSheetOpen.value) delay(10)
            }
            true
        } ?: false
        assertTrue("The saved unsaved draft must reopen the real confirmation sheet", opened)
    }

    private suspend fun assertNoAutomaticWork(expectedRows: List<TransactionEntity>) {
        assertEquals(expectedRows, db.transactionDao().getAllTransactionsDirect())
        assertFalse(vm.isParsing.value)
        assertFalse(vm.isImprovingWithAi.value)
        assertFalse(vm.isAiNoticeOpen.value)
        assertEquals("Restoring an entry must not call AI, even with consent and AI enabled", 0, requests.get())
    }

    @Test fun `typed raw fields and exact shares survive ViewModel and Room recreation`(): Unit = runBlocking {
        vm.openManualAddForGroup(7)
        vm.updateAmount("100.")
        vm.updateMerchant("  chai shop  ")
        vm.updateCategory("Food")
        vm.updatePaymentMethod("Cash")
        vm.updateNote("draft note\nkeep this line")
        vm.updateDate(1_700_000_000_123L)
        vm.setSplitMode(SplitMode.EXACT)
        vm.setPaidByPersonId(1)
        vm.updateExactShareAmount(-1, 20.0)
        vm.updateExactShareAmount(1, 35.0)
        vm.updateExactShareAmount(2, 45.0)
        val before = snapshot()
        assertEquals(SplitMode.EXACT, before.splitMode)
        assertTrue(before.shares.all { it.isSelected })
        assertEquals(mapOf(-1L to 20.0, 1L to 35.0, 2L to 45.0),
            before.shares.associate { it.personId to it.amount })
        assertTrue(vm.hasUnsavedEntry())
        pump()
        recreate(expectedTransactions = 0)
        requireRecoveredSheet()
        assertEquals(before, snapshot())
        assertNull(vm.editingTransactionId.value)
        assertNoAutomaticWork(emptyList())
        assertTrue(db.splitDao().getAllSplitsDirect().isEmpty())
        assertTrue(db.splitDao().getAllSplitSharesDirect().isEmpty())
    }

    @Test fun `percentage draft preserves participant choices payer and percentages after recreation`(): Unit = runBlocking {
        vm.openManualAdd()
        vm.updateAmount("200.00")
        vm.updateMerchant("percentage draft")
        vm.setIsSplit(true)
        // Room orders names alphabetically; select both intended people by identity.
        listOf(1L, 2L).forEach { id ->
            if (vm.shares.value.none { it.personId == id && it.isSelected }) vm.togglePersonShare(id)
        }
        vm.setSplitMode(SplitMode.PERCENTAGE)
        vm.setPaidByPersonId(2)
        vm.updateSharePercentage(-1, 25.0)
        vm.updateSharePercentage(1, 35.0)
        vm.updateSharePercentage(2, 40.0)
        val before = snapshot()
        assertTrue(before.shares.all { it.isSelected })
        assertEquals(mapOf(-1L to 25.0, 1L to 35.0, 2L to 40.0),
            before.shares.associate { it.personId to it.percentage })
        pump()
        recreate(expectedTransactions = 0)
        requireRecoveredSheet()
        assertEquals(before, snapshot())
        assertEquals(SplitMode.PERCENTAGE, vm.splitMode.value)
        assertNull(vm.editingTransactionId.value)
        assertNoAutomaticWork(emptyList())
        assertTrue(db.splitDao().getAllSplitsDirect().isEmpty())
        assertTrue(db.splitDao().getAllSplitSharesDirect().isEmpty())
    }

    @Test fun `existing edit recovers its identity and unsaved note without writing the original`(): Unit = runBlocking {
        val original = TransactionEntity(id = 41, type = "expense", amount = 80.0, category = "Food",
            merchant = "Saved tea", paymentMethod = "Cash", note = "saved note", date = 1_700_000_000_000L)
        db.transactionDao().insertTransaction(original)
        until { vm.rawTransactions.value.any { it.transaction.id == 41L } }
        vm.openEditTransaction(vm.rawTransactions.value.single())
        vm.updateNote("unfinished edit note")
        vm.updateAmount("81.25")
        val before = snapshot()
        assertEquals(41L, vm.editingTransactionId.value)
        assertTrue(vm.hasUnsavedEntry())
        pump()
        recreate(expectedTransactions = 1)
        requireRecoveredSheet()
        assertEquals(41L, vm.editingTransactionId.value)
        assertEquals(before, snapshot())
        assertTrue("The original edit baseline must survive", vm.hasUnsavedEntry())
        assertNoAutomaticWork(listOf(original))
    }

    @Test fun `no draft keeps the confirmation sheet closed after recreation`(): Unit = runBlocking {
        recreate(expectedTransactions = 0)
        assertFalse(vm.isConfirmSheetOpen.value)
        assertNoAutomaticWork(emptyList())
    }

    @Test fun `explicit discard is not recovered`(): Unit = runBlocking {
        vm.openManualAdd()
        vm.updateAmount("40")
        vm.updateNote("discard this")
        assertTrue(vm.hasUnsavedEntry())
        vm.dismissConfirmSheet()
        assertFalse(vm.isConfirmSheetOpen.value)
        pump()
        recreate(expectedTransactions = 0)
        assertFalse(vm.isConfirmSheetOpen.value)
        assertNoAutomaticWork(emptyList())
    }

    @Test fun `successful save stays closed after recreation with no duplicate row`(): Unit = runBlocking {
        vm.openManualAdd()
        vm.updateAmount("125.50")
        vm.updateMerchant("Saved draft")
        vm.updateNote("save this once")
        val existing = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        vm.saveCurrentTransaction(askForGroup = false)
        // Capture new action ownership before pumping, including a promptly completed save.
        val saving = vm.viewModelScope.coroutineContext[Job]!!.children.filter { it !in existing }.toList()
        assertTrue("The actual Save submission must start", saving.isNotEmpty())
        until { saving.all { it.isCompleted } && !vm.isConfirmSheetOpen.value &&
            db.transactionDao().getAllTransactionsDirect().size == 1 }
        val saved = db.transactionDao().getAllTransactionsDirect().single()
        assertEquals(125.50, saved.amount, 0.0)
        assertEquals("Saved draft", saved.merchant)
        recreate(expectedTransactions = 1)
        assertFalse(vm.isConfirmSheetOpen.value)
        assertNoAutomaticWork(listOf(saved))
        assertTrue(db.splitDao().getAllSplitsDirect().isEmpty())
    }

    @After fun cleanup(): Unit = runBlocking {
        try {
            retireViewModel()
        } finally {
            try {
                if (::db.isInitialized) db.close()
                app.deleteDatabase(databaseName)
                ReceiptStore.forAccount(app, userId).dir.deleteRecursively()
                previousConsent?.let { settings.setAiNoticeAccepted(it) }
                if (captured) {
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
