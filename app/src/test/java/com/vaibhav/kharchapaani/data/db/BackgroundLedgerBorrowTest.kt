package com.vaibhav.kharchapaani.data.db

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.ask.AskSentReceiver
import com.vaibhav.kharchapaani.ask.AskShare
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.data.settings.BackgroundLedger
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import com.vaibhav.kharchapaani.data.sync.*
import com.vaibhav.kharchapaani.reminder.DailyReminderWorker
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.util.NotificationHelper
import com.vaibhav.kharchapaani.widget.WidgetData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** The external remote response is fake; ledger writes, widget reads, reminders and asks use Room. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackgroundLedgerBorrowTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = executor.asCoroutineDispatcher()
    private val work = CoroutineScope(SupervisorJob() + main)
    private val files = mutableSetOf<String>()
    private val prefs get() = app.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE)
    private var previousMode: Map<String, *> = emptyMap<String, Any?>()
    private lateinit var previousReminder: ReminderSettings

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(main)
        previousMode = prefs.all.toMap()
        assertTrue(prefs.edit().clear().commit())
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        previousReminder = AppSettings(app).reminder.first()
    }

    @After fun tearDown() = runBlocking {
        try {
            work.cancel()
            withTimeout(5_000) { work.coroutineContext[Job]!!.join() }
            LedgerDatabases.close()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            files.forEach { app.deleteDatabase(it) }
            app.getSystemService(NotificationManager::class.java).cancelAll()
            AppSettings(app).setReminderEnabled(previousReminder.enabled)
            AppSettings(app).setReminderMinute(previousReminder.minuteOfDay)
        } finally {
            val edit = prefs.edit().clear()
            previousMode.forEach { (key, value) -> if (value is String) edit.putString(key, value) }
            assertTrue(edit.commit())
            Dispatchers.resetMain()
            main.close()
        }
        Unit
    }

    @Test fun `a different ledger cannot close Room while remote-success acknowledgement is still held`() =
        runBlocking { heldAcknowledgement(replaceOwner = false) }

    @Test fun `fresh owned startup for the same file cancels and drains a held sync acknowledgement`() =
        runBlocking { heldAcknowledgement(replaceOwner = true) }

    private suspend fun heldAcknowledgement(replaceOwner: Boolean) {
        val account = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(account)
        val db = LedgerDatabases.open(app, account)
        val id = db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 125.50,
            category = "Food", merchant = "Acknowledged purchase", paymentMethod = "Cash", syncState = SyncState.PENDING))
        val real = RoomTransactionSyncStore(db)
        val reachedAck = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val ackResult = CompletableDeferred<Throwable?>()
        val remoteCalls = AtomicInteger()
        val held = object : TransactionSyncStore by real {
            override suspend fun markSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long): Boolean = withContext(NonCancellable) {
                reachedAck.complete(Unit)
                release.await()
                val result = runCatching { real.markSyncedIfUnchanged(id, expectedUpdatedAt) }
                ackResult.complete(result.exceptionOrNull())
                result.getOrDefault(false)
            }
        }
        val remote = object : TransactionRemote {
            override suspend fun upsert(rows: List<TransactionUpload>) {
                assertEquals(125.50, rows.single().amount, 0.0)
                remoteCalls.incrementAndGet()
            }
            override suspend fun softDelete(remoteIds: List<String>) = Unit
            override suspend fun changedSince(since: String?, limit: Int): List<RemoteTransaction> = emptyList()
        }
        val sync = work.async {
            LedgerDatabases.withDatabase(app, account, isLocal = false) {
                TransactionSyncEngine(held, remote, account).sync()
            }
        }
        withTimeout(5_000) { reachedAck.await() }
        var newOwnerStarted = false
        val opening = withContext(main) {
            work.async(start = CoroutineStart.UNDISPATCHED) {
                if (replaceOwner) LedgerDatabases.openOwned(app, account, isLocal = false) {
                    newOwnerStarted = true
                    LedgerDatabases.CloseOwner { }
                } else {
                    val guest = LedgerMode(app).useLocal()
                    files += LedgerDatabases.localDatabaseName(guest)
                    LedgerDatabases.openLocal(app, guest)
                }
            }
        }
        try {
            withContext(main) { }
            assertTrue("A borrowed sync must retain Room through its held acknowledgement", db.isOpen)
            assertFalse("Replacement must await the background operation", opening.isCompleted)
            assertFalse(newOwnerStarted)
            release.complete(Unit)
            withTimeout(5_000) { opening.await(); sync.join() }
            assertNull("Server-success acknowledgement must not hit a closed database", ackResult.await())
            assertEquals(1, remoteCalls.get())
            val persisted = LedgerDatabases.open(app, account).transactionDao().getAllTransactionsDirect().single { it.id == id }
            assertEquals(125.50, persisted.amount, 0.0)
            assertEquals(SyncState.SYNCED, persisted.syncState)
            if (replaceOwner) assertTrue(newOwnerStarted)
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { sync.join(); opening.join() }
        }
    }

    @Test fun `widget reads and publication remain scoped until cancellation cleanup completes`() = runBlocking {
        val guest = LedgerMode(app).useLocal()
        files += LedgerDatabases.localDatabaseName(guest)
        val db = LedgerDatabases.openLocal(app, guest)
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 225.50,
            category = "Food", merchant = "Widget purchase", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val readResult = CompletableDeferred<Throwable?>()
        val rendering = work.async {
            WidgetData.withSummary(app) { summary ->
                assertEquals(Money.format(225.50), summary.spent)
                withContext(NonCancellable) {
                    entered.complete(Unit)
                    release.await()
                    readResult.complete(runCatching {
                        assertEquals(225.50, db.transactionDao().getAllTransactionsDirect().single().amount, 0.0)
                    }.exceptionOrNull())
                }
            }
        }
        withTimeout(5_000) { entered.await() }
        LedgerMode(app).useCloud()
        val account = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(account)
        val opening = withContext(main) { work.async(start = CoroutineStart.UNDISPATCHED) { LedgerDatabases.open(app, account) } }
        try {
            withContext(main) { }
            assertTrue("Widget's captured ledger must survive until its scoped publication finishes", db.isOpen)
            assertFalse(opening.isCompleted)
            release.complete(Unit)
            withTimeout(5_000) { opening.await(); rendering.join() }
            assertNull(readResult.await())
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { rendering.join(); opening.join() }
        }
    }

    @Test fun `borrow context prevents registry reentry through withContext instead of joining itself`() = runBlocking {
        val guest = LedgerMode(app).useLocal()
        files += LedgerDatabases.localDatabaseName(guest)
        val db = LedgerDatabases.openLocal(app, guest)
        db.transactionDao().getAllTransactionsDirect()
        val failure = LedgerDatabases.withDatabase(app, guest.id, isLocal = true) {
            withContext(Dispatchers.IO) { runCatching { LedgerDatabases.close() }.exceptionOrNull() }
        }
        assertTrue("Nested close must fail before acquiring the registry lock", failure is IllegalStateException)
        assertTrue(db.isOpen)
    }

    @Test fun `cloud descriptor revalidates its original resolver before touching another current ledger`() = runBlocking {
        val alice = UUID.randomUUID().toString()
        val bob = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(alice)
        files += LedgerDatabases.databaseName(bob)
        var current: String? = alice
        val stale = BackgroundLedger.current(app) { current }!!
        current = bob
        val bobs = LedgerDatabases.open(app, bob)
        bobs.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 19.25,
            category = "Food", merchant = "Only Bob", paymentMethod = "Cash"))
        val failure = runCatching { stale.withDatabase(app) { it.transactionDao().getAllTransactionsDirect() } }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue("A stale cloud descriptor must not close the new account's ledger", bobs.isOpen)
        assertEquals(19.25, bobs.transactionDao().getAllTransactionsDirect().single().amount, 0.0)
        assertFalse(app.getDatabasePath(LedgerDatabases.databaseName(alice)).exists())
    }

    @Test fun `ordinary cached scoped reads never drain the visible UI owner`() = runBlocking {
        val guest = LedgerMode(app).useLocal()
        files += LedgerDatabases.localDatabaseName(guest)
        var ownerDrains = 0
        val db = LedgerDatabases.openOwned(app, guest.id, isLocal = true) {
            LedgerDatabases.CloseOwner { ownerDrains++ }
        }
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 49.25,
            category = "Food", merchant = "Visible owner", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        repeat(3) {
            val amount = LedgerDatabases.withDatabase(app, guest.id, isLocal = true) { captured ->
                assertSame(db, captured)
                captured.transactionDao().getAllTransactionsDirect().single().amount
            }
            assertEquals(49.25, amount, 0.0)
        }
        assertEquals("A widget/reminder borrow must not retire the visible session", 0, ownerDrains)
        LedgerDatabases.close()
        assertEquals(1, ownerDrains)
    }

    @Test fun `local ask and actual reminder worker use local Room without account setup`() = runBlocking {
        val guest = LedgerMode(app).useLocal()
        files += LedgerDatabases.localDatabaseName(guest)
        val db = LedgerDatabases.openLocal(app, guest)
        val rohan = db.personDao().insertPerson(PersonEntity(name = "Rohan", avatarColor = "#FFFFFF"))
        val intent = Intent(app, AskSentReceiver::class.java).apply {
            putExtra(AskShare.EXTRA_USER_ID, guest.id)
            putExtra(AskShare.EXTRA_IS_LOCAL, true)
            putExtra(AskShare.EXTRA_PERSON_ID, rohan)
            putExtra(AskShare.EXTRA_AMOUNT, 200.0)
        }
        assertTrue(AskSentReceiver.record(app, intent, now = 5_000L))
        assertEquals(200.0, db.paymentAskDao().observeAll().first().single().amount, 0.0)
        AppSettings(app).setReminderEnabled(true)
        NotificationHelper.createChannels(app)
        val worker = TestListenableWorkerBuilder<DailyReminderWorker>(app).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        val manager = app.getSystemService(NotificationManager::class.java)
        assertTrue(manager.activeNotifications.any { it.notification.channelId == NotificationHelper.CHANNEL_DAILY_REMINDER })
        NotificationHelper.clearDailyReminder(app)
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 21.75,
            category = "Food", merchant = "Logged today", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<DailyReminderWorker>(app).build().doWork())
        assertFalse(manager.activeNotifications.any { it.notification.channelId == NotificationHelper.CHANNEL_DAILY_REMINDER })
        assertEquals(21.75, db.transactionDao().getAllTransactionsDirect().single().amount, 0.0)
        assertEquals(200.0, db.paymentAskDao().observeAll().first().single().amount, 0.0)
    }
}
