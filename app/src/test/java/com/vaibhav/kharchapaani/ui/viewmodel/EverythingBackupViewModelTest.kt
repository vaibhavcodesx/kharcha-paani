package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.repository.*
import com.vaibhav.kharchapaani.data.settings.*
import com.vaibhav.kharchapaani.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID

/** Exercises actual VM SAF streams, Room gatherers, settings and private-journal recovery. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EverythingBackupViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settings = AppSettings(app)
    private val modePrefs = app.getSharedPreferences("ledger_mode", Context.MODE_PRIVATE)
    private val aiPrefs = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val sortPrefs = app.getSharedPreferences("group_list_preferences", Context.MODE_PRIVATE)
    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val jobs = mutableListOf<Job>()
    private val ownedVms = mutableListOf<MainViewModel>()
    private val databases = mutableListOf<AppDatabase>()
    private val files = mutableListOf<File>()
    private lateinit var guest: GuestSession
    private lateinit var db: AppDatabase
    private var savedMode: Map<String, *>? = null
    private var savedAi: Map<String, *>? = null
    private var savedSort: Map<String, *>? = null
    private var savedSettings: EverythingSettings? = null
    private var savedConsent: Boolean? = null
    private var installed = false
    private val payload = EverythingSettings(customExpenseCategories = listOf("Groceries"),
        customIncomeCategories = listOf("Freelance"), hiddenCategories = listOf("Food"),
        customPaymentMethods = listOf("My card"), hiddenPaymentMethods = listOf("Cash"),
        defaultCategory = "Groceries", defaultPaymentMethod = "My card", askBeforeDiscarding = false,
        reminderEnabled = false, reminderMinute = 601, paymentsToConfirm = false,
        quietHoursEnabled = false, quietHoursStart = 602, quietHoursEnd = 603,
        groupSort = GroupSort.Name.name, upiId = "sample@upi", upiName = "Local sample", aiEnabled = true)

    private fun newDb(): AppDatabase = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
        .allowMainThreadQueries().build().also(databases::add)

    @Before fun setup(): Unit = runBlocking {
        Dispatchers.setMain(main); installed = true
        savedMode = modePrefs.all.toMap(); savedAi = aiPrefs.all.toMap(); savedSort = sortPrefs.all.toMap()
        assertTrue(modePrefs.edit().clear().commit())
        guest = LedgerMode(app).useLocal()
        savedSettings = settings.collectEverythingSettings(guest.settingsId)
        savedConsent = settings.aiNoticeAccepted.first()
        settings.setAiNoticeAccepted(false)
        AiSettings(app).setEnabled(false)
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = newDb()
    }

    private fun vm(): MainViewModel = MainViewModel(app, db, guest.id,
        geminiRepo = GeminiRepository(client = OkHttpClient.Builder().addInterceptor {
            error("Complete local backup must not use AI transport")
        }.build(), configured = true, accessToken = { "test-token" }), isLocal = true).also {
        store.put(UUID.randomUUID().toString(), it)
        jobs += it.viewModelScope.coroutineContext[Job]!!
        ownedVms += it
    }

    private suspend fun until(ready: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            main.scheduler.runCurrent(); ShadowLooper.idleMainLooper(); main.scheduler.runCurrent()
            if (ready()) return@withTimeout
            delay(10)
        }
    }

    /** Same FileProvider content URI path used by existing share tests, through ContentResolver. */
    private fun file(text: String = ""): Pair<File, Uri> {
        val directory = File(app.cacheDir, "exports").apply { mkdirs() }
        val path = File(directory, "full-vm-${UUID.randomUUID()}.json").apply { writeText(text) }.also(files::add)
        val authority = "${app.packageName}.fileprovider"
        // Robolectric gives each method a new cache directory, while FileProvider caches by authority.
        // Attach the real manifest provider before its static URI lookup, refreshing that strategy.
        requireNotNull(app.contentResolver.acquireContentProviderClient(authority)).close()
        return path to FileProvider.getUriForFile(app, authority, path)
    }

    private suspend fun seed(database: AppDatabase, photos: ReceiptStore, due: Boolean = false) {
        database.personDao().insertPerson(PersonEntity(id = 1, name = "Friend", avatarColor = "#123456"))
        database.groupDao().insertGroup(GroupEntity(id = 5, name = "Local group"))
        database.groupSyncDao().insertMembers(listOf(GroupMemberEntity(id = 6, groupId = 5, personId = -1), GroupMemberEntity(id = 7, groupId = 5, personId = 1)))
        database.transactionDao().insertTransaction(TransactionEntity(id = 11, type = "expense", amount = 40.0, category = "Food", merchant = "Plain expense", paymentMethod = "Cash", syncState = SyncState.PENDING))
        database.transactionDao().insertTransaction(TransactionEntity(id = 12, type = "expense", amount = 100.0, category = "Food", merchant = "Split expense", paymentMethod = "Cash", isSplit = true, groupId = 5))
        database.groupSyncDao().insertSplit(SplitEntity(id = 13, transactionId = 12, totalAmount = 100.0, paidByPersonId = -1, splitType = "exact"))
        database.groupSyncDao().insertShares(listOf(SplitShareEntity(id = 14, splitId = 13, personId = -1, shareAmount = 60.0), SplitShareEntity(id = 15, splitId = 13, personId = 1, shareAmount = 40.0)))
        database.groupSyncDao().insertSettlement(SettlementEntity(id = 16, fromPersonId = -1, toPersonId = 1, amount = 7.0, isIou = true))
        database.subscriptionDao().insert(SubscriptionEntity(id = 17, name = "Subscription", amount = 25.0, category = "Bills", paymentMethod = "Cash", cycle = "MONTHLY", startDate = CalendarDay.toMillis(if (due) LocalDate.now() else LocalDate.now().plusMonths(1))))
        database.paymentAskDao().insert(PaymentAskEntity(id = 18, personId = 1, amount = 10.0))
        photos.dir.mkdirs(); File(photos.dir, "kept.jpg").writeBytes(byteArrayOf(1, 2, 3, 4))
        database.receiptDao().insert(ReceiptEntity(id = 19, transactionId = 11, fileName = "kept.jpg", width = 2, height = 2, bytes = 4))
    }

    private suspend fun archive(due: Boolean = false): EverythingBackup {
        val source = newDb()
        val photos = ReceiptStore(File(app.cacheDir, "full-source-${UUID.randomUUID()}").also { files += it })
        seed(source, photos, due)
        return EverythingBackupRepository(source, photos).collect("fixture", payload)
    }

    @Test fun `VM exports all Room kinds photos and current settings through a writable content URI`(): Unit = runBlocking {
        seed(db, ReceiptStore.forLocal(app, guest))
        settings.restoreEverythingSettings(guest.settingsId, payload)
        GroupSortPreferences(app).writeDurably(GroupSort.Name)
        val vm = vm()
        val (path, uri) = file()
        vm.openEverythingExport(); vm.exportEverything(app, uri)
        assertTrue(vm.fullBackupBusy.value)
        vm.exportEverything(app, uri) // Duplicate caller while busy must be ignored.
        until { !vm.fullBackupBusy.value && !vm.fullBackupExportOpen.value }
        val decoded = EverythingBackup.decode(path.readText())
        assertEquals(setOf(11L, 12L), decoded.transactions.map { it.id }.toSet())
        assertEquals(1, decoded.people.size); assertEquals(1, decoded.groups.size)
        assertEquals(2, decoded.members.size); assertEquals(1, decoded.splits.size); assertEquals(2, decoded.shares.size)
        assertEquals(1, decoded.settlements.size); assertEquals(1, decoded.subscriptions.size); assertEquals(1, decoded.paymentAsks.size)
        assertEquals(1, decoded.receipts.size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), android.util.Base64.decode(decoded.receipts.single().contentBase64, android.util.Base64.NO_WRAP))
        assertEquals(payload.copy(aiEnabled = false), decoded.settings)
    }

    @Test fun `VM restores complete preview into the empty current guest without enabling AI or consent`(): Unit = runBlocking {
        val backup = archive()
        val vm = vm()
        val (_, uri) = file(EverythingBackup.encode(backup))
        val asksBefore = settings.notificationPermissionAsks.first()
        vm.importLocalBackup(app, uri)
        until { !vm.fullBackupBusy.value && vm.fullBackupPreview.value != null }
        assertTrue(vm.fullBackupTargetEmpty.value)
        assertNull(vm.restorePreview.value)
        vm.confirmEverythingRestore()
        assertTrue(vm.fullBackupBusy.value)
        until { !vm.fullBackupBusy.value && vm.fullBackupPreview.value == null }
        assertEquals(setOf(11L, 12L), db.transactionDao().getAllTransactionsDirect().map { it.id }.toSet())
        assertEquals(1, db.splitDao().getAllSplitsDirect().size)
        assertEquals(2, db.splitDao().getAllSplitSharesDirect().size)
        assertEquals(1, db.settlementDao().getAllSettlementsDirect().size)
        assertEquals(1, db.subscriptionDao().getAllDirect().size)
        assertEquals(1, db.paymentAskDao().observeAll().first().size)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), ReceiptStore.forLocal(app, guest).file("kept.jpg").readBytes())
        val restored = settings.collectEverythingSettings(guest.settingsId)
        assertEquals(payload.copy(aiEnabled = true, groupSort = null), restored)
        assertEquals(GroupSort.Name, GroupSortPreferences(app).read())
        assertFalse(AiSettings(app).enabledNow)
        assertFalse(settings.aiNoticeAccepted.first())
        assertEquals(asksBefore, settings.notificationPermissionAsks.first())
        assertFalse(File(app.filesDir, "backup-recovery/local-${guest.id}.json").exists())
        assertEquals(47.0, LedgerRepository(db).personBalances.first().single().netBalance, 0.0001)
    }

    @Test fun `invalid input and newly occupied target never replace existing rows`(): Unit = runBlocking {
        val vm = vm()
        vm.importLocalBackup(app, file("""{"format":"kharcha-paani-everything","version":99,"exportedAt":1,"appVersion":"bad"}""").second)
        until { !vm.fullBackupBusy.value && vm.fullBackupReadError.value }
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())
        vm.dismissEverythingRestore()
        val backup = archive()
        vm.importLocalBackup(app, file(EverythingBackup.encode(backup)).second)
        until { !vm.fullBackupBusy.value && vm.fullBackupPreview.value != null }
        assertTrue(vm.fullBackupTargetEmpty.value)
        val keep = TransactionEntity(id = 99, type = "expense", amount = 9.0, category = "Food", merchant = "Keep this", paymentMethod = "Cash")
        db.transactionDao().insertTransaction(keep)
        vm.confirmEverythingRestore()
        until { !vm.fullBackupBusy.value }
        assertEquals(listOf(keep), db.transactionDao().getAllTransactionsDirect())
        assertTrue(db.personDao().getAllPeopleDirect().isEmpty())
        assertEquals(backup, vm.fullBackupPreview.value)
        assertFalse(vm.fullBackupTargetEmpty.value)
        assertFalse(vm.fullBackupReadError.value)
        assertFalse(File(app.filesDir, "backup-recovery/local-${guest.id}.json").exists())
    }

    @Test fun `legacy version one and two still use their original preview and additive confirmation`(): Unit = runBlocking {
        val vm = vm()
        for (version in listOf(1, 2)) {
            val legacy = LocalBackup(version = version, exportedAt = 1, appVersion = "legacy",
                transactions = listOf(BackupTransaction(type = "expense", amount = 120.0, category = "Food", merchant = "Legacy", paymentMethod = "Cash", date = 2, createdAt = 3)))
            vm.importLocalBackup(app, file(LocalBackup.encode(legacy)).second)
            until { !vm.fullBackupBusy.value && vm.restorePreview.value != null }
            assertNull(vm.fullBackupPreview.value)
            assertFalse(vm.fullBackupReadError.value)
            val before = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
            vm.confirmRestore()
            // Capture before pumping: v2 already has one row, so row count alone cannot await it.
            val started = vm.viewModelScope.coroutineContext[Job]!!.children.filter { it !in before }.toList()
            assertTrue("Legacy confirmation must own an actual launched job", started.isNotEmpty())
            until { started.all { it.isCompleted } && vm.restorePreview.value == null && db.transactionDao().getAllTransactionsDirect().size == 1 }
            assertEquals(120.0, db.transactionDao().getAllTransactionsDirect().single().amount, 0.0)
        }
    }

    @Test fun `startup preserves a broken recovery before maintenance and resumes the matching archive once repaired`(): Unit = runBlocking {
        val backup = archive(due = true)
        val journal = File(app.filesDir, "backup-recovery/local-${guest.id}.json")
        val coordinator = EverythingRestoreCoordinator(db, EverythingBackupRepository(db, ReceiptStore.forLocal(app, guest)), journal) {
            throw IOException("Simulated settings interruption")
        }
        try { coordinator.restore(backup, true); fail("Expected interrupted settings") }
        catch (expected: IOException) { }
        val original = journal.readBytes()
        val friend = db.personDao().getAllPeopleDirect().single()
        journal.writeText("broken journal")
        val first = vm()
        assertTrue(first.fullBackupBusy.value)
        until { !first.fullBackupBusy.value && first.fullBackupReadError.value }
        assertEquals(2, db.transactionDao().getAllTransactionsDirect().size) // No due subscription charge yet.
        assertEquals(friend, db.personDao().getAllPeopleDirect().single()) // No colour rewrite yet.
        first.openManualAdd(); first.updateAmount("999"); first.saveCurrentTransaction(false)
        main.scheduler.runCurrent()
        assertEquals(2, db.transactionDao().getAllTransactionsDirect().size)
        assertTrue(journal.exists())
        clearOwnedVms()
        journal.writeBytes(original)
        val resumed = vm()
        assertTrue(resumed.fullBackupBusy.value)
        until { !resumed.fullBackupBusy.value }
        assertNull(resumed.fullBackupPreview.value)
        assertFalse(journal.exists())
        assertEquals(3, db.transactionDao().getAllTransactionsDirect().size) // Exactly one due charge after recovery.
        assertEquals(1, db.transactionDao().getAllTransactionsDirect().count { it.merchant == "Subscription" })
        assertEquals("Groceries", settings.collectEverythingSettings(guest.settingsId).defaultCategory)
        assertFalse(AiSettings(app).enabledNow)
    }

    @Test fun `manual confirmation after a blocked startup also runs maintenance once`(): Unit = runBlocking {
        val backup = archive(due = true)
        val journal = File(app.filesDir, "backup-recovery/local-${guest.id}.json")
        val photos = ReceiptStore.forLocal(app, guest)
        photos.dir.mkdirs()
        val conflict = photos.file("kept.jpg").apply { writeBytes(byteArrayOf(9)) }
        try {
            EverythingRestoreCoordinator(db, EverythingBackupRepository(db, photos), journal) {}.restore(backup, true)
            fail("Expected a receipt conflict")
        } catch (expected: IllegalArgumentException) { }
        val vm = vm()
        until { !vm.fullBackupBusy.value && vm.fullBackupPreview.value != null }
        assertEquals(backup, vm.fullBackupPreview.value)
        assertFalse(vm.fullBackupReadError.value)
        assertTrue(vm.fullBackupTargetEmpty.value)
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())
        assertTrue(journal.exists())
        assertTrue(conflict.delete()) // Release the injected fault; retry the retained preview, no import.
        vm.confirmEverythingRestore()
        until { !vm.fullBackupBusy.value && vm.fullBackupPreview.value == null }
        assertEquals(3, db.transactionDao().getAllTransactionsDirect().size)
        assertEquals(1, db.transactionDao().getAllTransactionsDirect().count { it.merchant == "Subscription" })
        assertNotNull(db.subscriptionDao().getAllDirect().single().chargedThrough)
        assertFalse(journal.exists())
    }

    @Test fun `a pending legacy picker callback cannot hide the private recovery modal`(): Unit = runBlocking {
        val backup = archive()
        val journal = File(app.filesDir, "backup-recovery/local-${guest.id}.json")
        try {
            EverythingRestoreCoordinator(db, EverythingBackupRepository(db, ReceiptStore.forLocal(app, guest)), journal) {
                throw IOException("Interrupted settings")
            }.restore(backup, true)
            fail("Expected interrupted settings")
        } catch (expected: IOException) { }
        journal.writeText("broken journal")
        val bytes = journal.readBytes()
        val vm = vm()
        until { !vm.fullBackupBusy.value && vm.fullBackupReadError.value }
        val legacy = LocalBackup(exportedAt = 1, appVersion = "legacy",
            transactions = listOf(BackupTransaction(type = "expense", amount = 120.0, category = "Food", merchant = "Pending picker", paymentMethod = "Cash", date = 2, createdAt = 3)))
        val before = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        vm.importLocalBackup(app, file(LocalBackup.encode(legacy)).second)
        val started = vm.viewModelScope.coroutineContext[Job]!!.children.filter { it !in before }.toList()
        until { !vm.fullBackupBusy.value && started.all { it.isCompleted } }
        assertTrue("Pending import must preserve the recovery modal", vm.fullBackupReadError.value)
        assertNull(vm.fullBackupPreview.value)
        assertNull(vm.restorePreview.value)
        vm.dismissRestore()
        assertTrue(vm.fullBackupReadError.value)
        assertArrayEquals(bytes, journal.readBytes())
        assertEquals(setOf(11L, 12L), db.transactionDao().getAllTransactionsDirect().map { it.id }.toSet())
    }

    private suspend fun clearOwnedVms() {
        store.clear()
        until { jobs.all { it.isCompleted } }
        val closingScope = CoroutineScope(SupervisorJob() + main)
        try {
            val closing = closingScope.async { ownedVms.forEach { it.awaitSessionClosingWork() } }
            until { closing.isCompleted }
            closing.await()
        } finally { closingScope.cancel() }
    }

    private fun restorePreferences(prefs: android.content.SharedPreferences, values: Map<String, *>?) {
        if (values == null) return
        val edit = prefs.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> edit.putString(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
        } }
        assertTrue(edit.commit())
    }

    @After fun close(): Unit = runBlocking {
        try { clearOwnedVms() }
        finally {
            try {
                databases.forEach { it.close() }
                if (::guest.isInitialized) {
                    ReceiptStore.forLocal(app, guest).dir.deleteRecursively()
                    File(app.filesDir, "backup-recovery/local-${guest.id}.json").delete()
                    File(app.filesDir, "backup-recovery/local-${guest.id}.json.bak").delete()
                    savedSettings?.let { settings.restoreEverythingSettings(guest.settingsId, it) }
                    settings.clearAccount(guest.settingsId)
                }
                savedConsent?.let { settings.setAiNoticeAccepted(it) }
                restorePreferences(modePrefs, savedMode); restorePreferences(aiPrefs, savedAi); restorePreferences(sortPrefs, savedSort)
                files.forEach { if (it.isDirectory) it.deleteRecursively() else it.delete() }
            } finally { if (installed) Dispatchers.resetMain() }
        }
    }
}
