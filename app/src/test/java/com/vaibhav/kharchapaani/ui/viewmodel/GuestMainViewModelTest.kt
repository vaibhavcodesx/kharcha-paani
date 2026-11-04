package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.repository.OfflineHistoryException
import com.vaibhav.kharchapaani.data.settings.AiSettings
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.data.settings.GuestSession
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.domain.CalendarDay
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsController
import com.vaibhav.kharchapaani.ui.components.NewSubscription
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Local mode uses the real money paths; only the external AI HTTP boundary is replaced. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GuestMainViewModelTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val guest = GuestSession(UUID.randomUUID().toString())
    private val settings = AppSettings(app)
    private val preferences = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val screen = CoroutineScope(SupervisorJob() + main)
    private val requests = AtomicInteger()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private var installed = false
    private var captured = false
    private var hadEnabled = false
    private var previousEnabled = true
    private var previousConsent: Boolean? = null
    private val initialPayee = UpiPayee("sample@upi", "Local sample")
    private val cloudPayee = UpiPayee("cloud@upi", "Cloud sample")
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
            .body("""{"error":{"status":"PERMISSION_DENIED"}}""".toResponseBody("application/json".toMediaType())).build()
    }.build()

    @Before fun setup(): Unit = runBlocking {
        Dispatchers.setMain(main)
        installed = true
        hadEnabled = preferences.contains("enabled")
        previousEnabled = preferences.getBoolean("enabled", true)
        captured = true
        previousConsent = settings.aiNoticeAccepted.first()
        AiSettings(app).setEnabled(true)
        settings.setAiNoticeAccepted(true)
        settings.setUpiPayee(guest.id, cloudPayee)
        settings.setUpiPayee(guest.settingsId, initialPayee)
        settings.updateCategoryPrefs(guest.id) { it.copy(customExpense = listOf("Cloud category")) }
        settings.updateCategoryPrefs(guest.settingsId) { it.copy(customExpense = listOf("Local category")) }
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF"))
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Neha", avatarColor = "#F2D06B"))
        vm = MainViewModel(app, db, guest.id,
            geminiRepo = GeminiRepository(client = client,
                endpoint = { "https://example.invalid/models/$it:generateContent" },
                accessToken = { "test-token" }, configured = true), isLocal = true)
        store.put("main", vm)
        screen.launch { vm.people.collect {} }
        screen.launch { vm.rawGroups.collect {} }
        screen.launch { vm.groups.collect {} }
        screen.launch { vm.upiPayee.collect {} }
        screen.launch { vm.syncStatus.collect {} }
        until { vm.people.value.size == 2 && vm.upiPayee.value == initialPayee && "Local category" in vm.categoryPrefs.value.customExpense }
        assertFalse("Cloud category" in vm.categoryPrefs.value.customExpense)
    }

    private suspend fun until(ready: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            main.scheduler.runCurrent()
            ShadowLooper.idleMainLooper()
            main.scheduler.runCurrent()
            if (ready()) return@withTimeout
            delay(10)
        }
    }

    private suspend fun action(ready: suspend () -> Boolean = { true }, block: () -> Unit) {
        main.scheduler.runCurrent()
        val scope = vm.viewModelScope.coroutineContext[Job]!!
        val existing = scope.children.toSet()
        block()
        val started = mutableSetOf<Job>()
        until {
            started += scope.children.filter { it !in existing }
            started.all { it.isCompleted } && ready()
        }
    }

    private fun assertNoSyncWork() {
        val manager = WorkManager.getInstance(app)
        assertTrue(manager.getWorkInfosForUniqueWork("sync-${guest.id}").get(10, TimeUnit.SECONDS).isEmpty())
        assertTrue(manager.getWorkInfosForUniqueWork("sync-periodic-${guest.id}").get(10, TimeUnit.SECONDS).isEmpty())
    }

    @Test fun `local expense equal split IOU and subscription preserve money without scheduling sync`(): Unit = runBlocking {
        action { vm.openManualAdd(); vm.updateAmount("40"); vm.updateMerchant("Chai"); vm.saveCurrentTransaction(false) }
        assertEquals(40.0, db.transactionDao().getAllTransactionsDirect().single().amount, 0.0)
        vm.openManualAdd()
        vm.updateAmount("100")
        vm.updateMerchant("Dinner")
        vm.setIsSplit(true)
        vm.setSplitMode(SplitMode.EQUAL)
        for (id in listOf(1L, 2L)) if (!vm.shares.value.single { it.personId == id }.isSelected) vm.togglePersonShare(id)
        vm.setPaidByPersonId(-1)
        action { vm.saveCurrentTransaction(false) }
        val split = db.splitDao().getAllSplitsDirect().single()
        val shares = db.splitDao().getSharesForSplitDirect(split.id).associate { it.personId to it.shareAmount }
        assertEquals(setOf(-1L, 1L, 2L), shares.keys)
        assertEquals(100.0, shares.values.sum(), 0.0001)
        assertEquals(33.34, shares.getValue(-1), 0.0001)
        assertEquals(33.33, shares.getValue(1), 0.0001)
        assertEquals(33.33, shares.getValue(2), 0.0001)
        action { vm.recordIou(-1, 1, 12.50, "Local IOU") }
        val iou = db.settlementDao().getAllSettlementsDirect().single()
        assertTrue(iou.isIou)
        assertEquals(12.50, iou.amount, 0.0)
        assertEquals(SettlementStatus.CONFIRMED, iou.status)
        val today = LocalDate.now()
        action { vm.addSubscription(NewSubscription("Local subscription", 25.0, "Bills", "Cash", SubscriptionCycle.MONTHLY, today), true) }
        val subscription = db.subscriptionDao().getAllDirect().single()
        assertEquals(CalendarDay.toMillis(today), subscription.chargedThrough!!)
        assertEquals(1, db.transactionDao().getAllTransactionsDirect().count { it.merchant == "Local subscription" && it.amount == 25.0 })
        val balances = LedgerRepository(db).personBalances.first().associate { it.person.id to it.netBalance }
        assertEquals(45.83, balances.getValue(1), 0.0001)
        assertEquals(33.33, balances.getValue(2), 0.0001)
        // Existing sync-state classification is retained; local mode suppresses all scheduling.
        assertTrue(db.transactionDao().getAllTransactionsDirect().none { it.syncState == SyncState.FAILED })
        assertTrue(db.settlementDao().getAllSettlementsDirect().all { it.pendingResponse == null })
        assertEquals(0, vm.syncStatus.value.unsyncedChanges)
        assertEquals(0, vm.syncStatus.value.failedChanges)
        assertFalse(vm.syncStatus.value.isSyncing)
        assertEquals(0, requests.get())
        assertNoSyncWork()
    }

    @Test fun `local capture keeps all AI inputs on phone despite enabled preference and prior consent`(): Unit = runBlocking {
        assertTrue(AiSettings(app).enabledNow)
        assertTrue(settings.aiNoticeAccepted.first())
        action { vm.parseInputText("chai 40 cash") }
        assertEquals("40", vm.amount.value)
        assertFalse(vm.isAiNoticeOpen.value)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            action(ready = { vm.billPhoto.value is BillPhoto.Kept }) { vm.parseReceiptImage(bitmap) }
            assertNotNull((vm.billPhoto.value as BillPhoto.Kept).staged)
            vm.updateAmount("125")
            vm.updateMerchant("Manual receipt")
            action { vm.saveCurrentTransaction(false) }
            val tx = db.transactionDao().getAllTransactionsDirect().single()
            assertEquals(125.0, tx.amount, 0.0)
            val receipt = db.receiptDao().forTransaction(tx.id).single()
            assertTrue(ReceiptStore.forLocal(app, guest).file(receipt.fileName).exists())
            assertFalse(ReceiptStore.forAccount(app, guest.id).file(receipt.fileName).exists())
        } finally { bitmap.recycle() }
        val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray()
        action { vm.parseBankOrUpiStatement(pdf); vm.askInsights(AiPrompt.MonthInReview); vm.requestVoiceCapture() }
        assertFalse(vm.voiceCaptureRequested.value)
        assertFalse(vm.isImportStatementOpen.value)
        action { vm.createNewGroup("Local trip", "", "T", "#70C1FF", listOf(1, 2)) }
        val group = db.groupDao().getAllGroupsDirect().single()
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 10.0, category = "Food", merchant = "Trip snack", paymentMethod = "Cash", groupId = group.id))
        until { vm.groups.value.any { it.group.id == group.id && it.transactionsCount == 1 } }
        action { vm.askTripReview(group.id) }
        val changed = UpiPayee("changed@upi", "Changed local")
        action(ready = { settings.upiPayee(guest.settingsId).first() == changed }) { vm.saveUpi(changed) }
        assertEquals(changed, settings.upiPayee(guest.settingsId).first())
        assertEquals("Same UUID cloud preference must remain isolated", cloudPayee, settings.upiPayee(guest.id).first())
        action { vm.requestSync(); vm.retryFailedSync() }
        assertEquals(0, requests.get())
        assertTrue("Local mode must not overwrite the device AI preference", AiSettings(app).enabledNow)
        assertNoSyncWork()
        // Auth transport has no public test seam: this test does not claim profile-call counts.
    }

    @Test fun `local mode rejects shared creation edits and settlement answers while local groups work`(): Unit = runBlocking {
        action { vm.createNewGroup("Do not share", "", "S", "#70C1FF", emptyList(), shared = true) }
        assertTrue(db.groupDao().getAllGroupsDirect().isEmpty())
        action { vm.createNewGroup("Local group", "", "L", "#70C1FF", listOf(1, 2), shared = false) }
        assertEquals(SyncState.LOCAL_ONLY, db.groupDao().getAllGroupsDirect().single().syncState)
        // Matches full-backup restore: sharedness is SYNCED for correct GroupBalances, writes local-only.
        val importedGroup = GroupEntity(id = 90, name = "Imported shared history", syncState = SyncState.SYNCED, createdByProfileId = "former-account")
        db.groupSyncDao().insertGroup(importedGroup)
        val transaction = TransactionEntity(id = 91, type = "expense", amount = 100.0, category = "Food", merchant = "Imported dinner", paymentMethod = "Cash", isSplit = true, groupId = 90, syncState = SyncState.LOCAL_ONLY, ownerProfileId = "someone-else")
        db.transactionDao().insertTransaction(transaction)
        val split = SplitEntity(id = 92, transactionId = 91, totalAmount = 100.0, paidByPersonId = -1)
        db.groupSyncDao().insertSplit(split)
        val payment = SettlementEntity(id = 93, fromPersonId = -1, toPersonId = 1, amount = 5.0, groupId = 90, syncState = SyncState.LOCAL_ONLY, status = SettlementStatus.PENDING, recordedByPersonId = 1)
        db.groupSyncDao().insertSettlement(payment)
        until { vm.rawGroups.value.any { it.id == importedGroup.id } }
        val full = FullTransaction(transaction, split)
        assertTrue("Old payer-based edit permission must not bypass local mode", full.canEdit)
        action { vm.openEditTransaction(full) }
        assertNull(vm.editingTransactionId.value)
        assertFalse(vm.isConfirmSheetOpen.value)
        action { vm.openSettlementAnswer(93); vm.answerSettlement(true, "Must stay local") }
        val repository = LedgerRepository(db, localMode = true)
        refused { repository.deleteTransaction(91) }
        refused { repository.recordSettlement(-1, 1, 5.0, groupId = 90) }
        refused { repository.respondToSettlement(93, true) }
        refused { repository.updateGroup(importedGroup.copy(name = "Changed"), emptyList()) }
        refused { repository.setGroupArchived(90, true) }
        refused { repository.removeSharedGroupLocally(90) }
        refused { repository.createSharedGroup("Must not create") }
        val shared = SharedGroupsController(screen, repository, remote = null, selfProfileId = guest.id,
            requestSync = { fail("Local controller scheduled sync") },
            resolveText = { id, args -> app.getString(id, *args) })
        shared.openJoin("ABCD1234")
        shared.onJoinInputChange("ABCD1234")
        shared.confirmJoin()
        shared.createInvite(importedGroup)
        shared.revokeInvite()
        shared.leaveGroup(importedGroup) { fail("Local controller removed imported membership") }
        shared.setArchived(importedGroup, true)
        main.scheduler.runCurrent()
        assertNull(shared.state.value.join)
        assertNull(shared.state.value.invite)
        assertNotNull(shared.state.value.message)
        assertEquals(importedGroup, db.groupDao().getGroupByIdDirect(90))
        assertEquals(payment, db.groupSyncDao().settlementById(93))
        assertEquals(transaction, db.transactionDao().getTransactionByIdDirect(91))
        assertFalse(db.groupDao().getAllGroupsDirect().any { it.syncState == SyncState.PENDING })
        assertTrue(db.groupSyncDao().settlementsWithUnsentAnswers(100).isEmpty())
        assertNoSyncWork()
    }

    @Test fun `local deletion cannot remove a guest referenced only through a shared host share`(): Unit = runBlocking {
        // Settle the same idempotent colour maintenance the VM starts, before comparing full rows.
        LedgerRepository(db).repairPersonColours()
        val group = GroupEntity(id = 201, name = "Imported guest history", syncState = SyncState.SYNCED,
            createdByProfileId = "former-account")
        db.groupSyncDao().insertGroup(group)
        db.groupSyncDao().insertMembers(listOf(GroupMemberEntity(groupId = group.id, personId = -1)))
        val transaction = TransactionEntity(id = 202, type = "expense", amount = 100.0,
            category = "Food", merchant = "Guest dinner", paymentMethod = "Cash", isSplit = true,
            groupId = group.id, syncState = SyncState.LOCAL_ONLY, ownerProfileId = "former-account")
        db.transactionDao().insertTransaction(transaction)
        val split = SplitEntity(id = 203, transactionId = transaction.id, totalAmount = 100.0, paidByPersonId = -1)
        db.groupSyncDao().insertSplit(split)
        db.groupSyncDao().insertShares(listOf(
            SplitShareEntity(id = 204, splitId = split.id, personId = -1, shareAmount = 70.0),
            SplitShareEntity(id = 205, splitId = split.id, personId = -1, shareAmount = 30.0,
                guestName = "Neha", guestPersonId = 2),
        ))
        assertTrue("The old direct-person gatherer must miss this host-mapped guest",
            db.splitDao().getSplitsInvolvingPerson(2).isEmpty())
        assertFalse(db.groupDao().getAllGroupMembersDirect().any { it.personId == 2L })
        val friend = db.personDao().getPersonById(2)!!
        val shares = db.splitDao().getAllSplitSharesDirect().sortedBy { it.id }
        val balances = LedgerRepository(db).personBalances.first().sortedBy { it.person.id }
        assertTrue("Exercise the actual guest debt", balances.any { it.person.id == 2L && it.netBalance != 0.0 })
        refused { LedgerRepository(db, localMode = true).deletePerson(2) }
        assertEquals(friend, db.personDao().getPersonById(2))
        assertEquals(shares, db.splitDao().getAllSplitSharesDirect().sortedBy { it.id })
        assertEquals(balances, LedgerRepository(db).personBalances.first().sortedBy { it.person.id })
        assertEquals(transaction, db.transactionDao().getTransactionByIdDirect(transaction.id))
        assertEquals(group, db.groupDao().getGroupByIdDirect(group.id))
        assertNoSyncWork()
    }

    private suspend fun refused(write: suspend () -> Unit) {
        try { write(); fail("Local mode allowed a shared-history write") }
        catch (expected: OfflineHistoryException) { /* The actual repository boundary must refuse. */ }
    }

    @After fun close(): Unit = runBlocking {
        try {
            val jobs = mutableListOf<Job>()
            jobs += screen.coroutineContext[Job]!!
            screen.cancel()
            if (::vm.isInitialized) jobs += vm.viewModelScope.coroutineContext[Job]!!
            store.clear()
            until { jobs.all { it.isCompleted } && client.dispatcher.runningCallsCount() == 0 }
        } finally {
            try {
                if (::db.isInitialized) db.close()
                ReceiptStore.forLocal(app, guest).dir.deleteRecursively()
                settings.clearAccount(guest.id)
                settings.clearAccount(guest.settingsId)
                previousConsent?.let { settings.setAiNoticeAccepted(it) }
                if (captured) {
                    val edit = preferences.edit()
                    if (hadEnabled) edit.putBoolean("enabled", previousEnabled) else edit.remove("enabled")
                    assertTrue(edit.commit())
                }
            } finally { if (installed) Dispatchers.resetMain() }
        }
    }
}
