package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.settings.BackgroundLedger
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import com.vaibhav.kharchapaani.ui.viewmodel.PendingDeletes
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
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

/** Every registry close, including background opens, must drain the account's registered work. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedLedgerCloseTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = executor.asCoroutineDispatcher()
    private val work = CoroutineScope(SupervisorJob() + main)
    private val holderStore = ViewModelStore()
    private lateinit var holder: AccountSessionHolder
    private val sessions = mutableListOf<AccountSession>()
    private val files = mutableSetOf<String>()
    private var previousMode: Map<String, *> = emptyMap<String, Any?>()
    private val prefs get() = app.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE)

    @Before fun setUp() {
        previousMode = prefs.all.toMap()
        assertTrue(prefs.edit().clear().commit())
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        Dispatchers.setMain(main)
        holder = AccountSessionHolder(app)
        holderStore.put("holder", holder)
    }

    @After fun tearDown() = runBlocking {
        try {
            withContext(main) {
                holderStore.clear()
                sessions.forEach { it.viewModelStore.clear() }
            }
            withTimeout(5_000) {
                withContext(main) {
                    sessions.forEach { it.awaitClosed() }
                    holder.awaitSessionClosingWork()
                }
                work.cancel()
                work.coroutineContext[Job]!!.join()
            }
            LedgerDatabases.close()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            files.forEach { app.deleteDatabase(it) }
        } finally {
            val edit = prefs.edit().clear()
            previousMode.forEach { (key, value) -> if (value is String) edit.putString(key, value) }
            assertTrue(edit.commit())
            Dispatchers.resetMain()
            main.close()
        }
        Unit
    }

    @Test fun `owned cached factory runs on Main and both replacement and explicit close invoke its hook`() = runBlocking {
        val account = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(account)
        val cached = LedgerDatabases.open(app, account)
        cached.transactionDao().getAllTransactionsDirect()
        val mainThread = withContext(main) { Thread.currentThread() }
        var factoryThread: Thread? = null
        var drains = 0
        val owned = withContext(Dispatchers.IO) {
            LedgerDatabases.openOwned(app, account, isLocal = false) { database ->
                factoryThread = Thread.currentThread()
                assertSame(cached, database)
                LedgerDatabases.CloseOwner {
                    assertTrue(database.isOpen)
                    drains++
                }
            }
        }
        assertSame(mainThread, factoryThread)
        assertSame(cached, owned)
        val guest = LedgerMode(app).useLocal()
        files += LedgerDatabases.localDatabaseName(guest)
        LedgerDatabases.openLocal(app, guest)
        assertEquals("Replacement must drain the cached record's owner", 1, drains)
        withContext(Dispatchers.IO) {
            LedgerDatabases.openOwned(app, guest.id, isLocal = true) { database ->
                LedgerDatabases.CloseOwner {
                    assertTrue(database.isOpen)
                    drains++
                }
            }.transactionDao().getAllTransactionsDirect()
        }
        LedgerDatabases.close()
        assertEquals("Explicit close must use the same owner barrier", 2, drains)
    }

    @Test fun `background opener cancels owned operation and waits for real Room cleanup before close`() = runBlocking {
        val old = localSession()
        val target = seed(old.database, "Canceled operation", 125.50)
        seed(old.database, "Kept", 9.25)
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Throwable?>()
        val vm = object : ViewModel() {}
        val vmJob = vm.viewModelScope.coroutineContext[Job]!!
        withContext(main) {
            old.register(vm)
            old.viewModelStore.put("owned-operation", vm)
            vm.viewModelScope.launch {
                try { started.complete(Unit); awaitCancellation() }
                finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        release.await()
                        result.complete(runCatching { old.database.transactionDao().deleteTransactionById(target) }.exceptionOrNull())
                    }
                }
            }
        }
        withTimeout(5_000) { started.await() }
        val account = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(account)
        LedgerMode(app).useCloud()
        val resolved = BackgroundLedger.current(app) { account }!!
        val opening = work.async { resolved.open(app) }
        try {
            val first = withTimeout(5_000) {
                select<String> {
                    cleanupStarted.onAwait { "cleanup" }
                    opening.onAwait { "opened" }
                }
            }
            assertEquals("Background open must start owner cleanup before returning a new ledger", "cleanup", first)
            assertTrue(old.database.isOpen)
            assertFalse(opening.isCompleted)
            release.complete(Unit)
            withTimeout(5_000) { opening.await() }
            assertTrue(vmJob.isCompleted)
            assertFalse(old.database.isOpen)
            assertNull(result.await())
            val rows = LedgerDatabases.openLocal(app, LedgerMode(app).useLocal()).transactionDao().getAllTransactionsDirect()
            assertEquals(listOf("Kept"), rows.map { it.merchant })
            assertEquals(9.25, rows.single().amount, 0.0)
        } finally {
            release.complete(Unit)
            withContext(main) { old.viewModelStore.clear() }
            withTimeout(5_000) { old.awaitClosed(); opening.join() }
        }
    }

    @Test fun `database record retains destroyed holders pending deletion work for a later background open`() = runBlocking {
        val old = localSession()
        val target = seed(old.database, "Pending delete", 75.50)
        seed(old.database, "Kept after destroy", 14.25)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Throwable?>()
        val vm = HeldDelete(old.database, main, started, release, result)
        withContext(main) {
            old.register(vm)
            old.viewModelStore.put("pending", vm)
            vm.hide(target)
            holderStore.clear()
        }
        withTimeout(5_000) { started.await() }
        LedgerMode(app).useCloud()
        val account = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(account)
        val resolved = BackgroundLedger.current(app) { account }!!
        val opening = withContext(main) { work.async(start = CoroutineStart.UNDISPATCHED) { resolved.open(app) } }
        try {
            withContext(main) { } // The opener has reached its first suspension before this checkpoint.
            assertTrue("Destroying the holder must not abandon its database's close hook", old.database.isOpen)
            assertFalse(opening.isCompleted)
            release.complete(Unit)
            withTimeout(5_000) { opening.await() }
            assertFalse(old.database.isOpen)
            assertNull(result.await())
            val rows = LedgerDatabases.openLocal(app, LedgerMode(app).useLocal()).transactionDao().getAllTransactionsDirect()
            assertEquals(listOf("Kept after destroy"), rows.map { it.merchant })
            assertEquals(14.25, rows.single().amount, 0.0)
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { withContext(main) { vm.awaitSessionClosingWork() }; opening.join() }
        }
    }

    @Test fun `fresh holder for same cached ledger waits for destroyed holders owned cleanup`() = runBlocking {
        val old = localSession()
        val target = seed(old.database, "Canceled old restore", 225.50)
        seed(old.database, "Kept on replacement", 19.25)
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val result = CompletableDeferred<Throwable?>()
        val vm = object : ViewModel() {}
        val vmJob = vm.viewModelScope.coroutineContext[Job]!!
        withContext(main) {
            old.register(vm)
            old.viewModelStore.put("old-operation", vm)
            vm.viewModelScope.launch {
                try { started.complete(Unit); awaitCancellation() }
                finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        release.await()
                        result.complete(runCatching { old.database.transactionDao().deleteTransactionById(target) }.exceptionOrNull())
                    }
                }
            }
        }
        withTimeout(5_000) { started.await() }
        val replacementStore = ViewModelStore()
        val replacement = AccountSessionHolder(app)
        replacementStore.put("replacement", replacement)
        try {
            withContext(main) { holderStore.clear() }
            withTimeout(5_000) { cleanupStarted.await() }
            withContext(main) { replacement.onLocalMode() }
            withContext(main) { } // Let a queued cached open reach the owner barrier.
            assertTrue(old.database.isOpen)
            assertTrue("A fresh owner must not publish Ready while old cleanup is held",
                replacement.state.value !is AccountSessionState.Ready)
            release.complete(Unit)
            val next = (withTimeout(5_000) {
                replacement.state.first { it is AccountSessionState.Ready }
            } as AccountSessionState.Ready).session
            sessions += next
            assertSame("Same-file ownership replacement keeps the Room handle", old.database, next.database)
            assertTrue(vmJob.isCompleted)
            assertNull(result.await())
            val rows = next.database.transactionDao().getAllTransactionsDirect()
            assertEquals(listOf("Kept on replacement"), rows.map { it.merchant })
            assertEquals(19.25, rows.single().amount, 0.0)
        } finally {
            release.complete(Unit)
            withContext(main) { replacementStore.clear(); old.viewModelStore.clear() }
            withTimeout(5_000) {
                old.awaitClosed()
                withContext(main) { replacement.awaitSessionClosingWork() }
            }
        }
    }

    @Test fun `cached owner replacements discard drained callbacks and retain the current close hook`() = runBlocking {
        val account = UUID.randomUUID().toString()
        files += LedgerDatabases.databaseName(account)
        var first = 0
        var second = 0
        var current = 0
        val original = LedgerDatabases.openOwned(app, account, isLocal = false) { database ->
            LedgerDatabases.CloseOwner { assertTrue(database.isOpen); first++ }
        }
        original.transactionDao().getAllTransactionsDirect()
        assertSame(original, LedgerDatabases.openOwned(app, account, isLocal = false) { database ->
            LedgerDatabases.CloseOwner { assertTrue(database.isOpen); second++ }
        })
        assertEquals(1, first)
        assertEquals(0, second)
        assertSame(original, LedgerDatabases.openOwned(app, account, isLocal = false) { database ->
            LedgerDatabases.CloseOwner { assertTrue(database.isOpen); current++ }
        })
        assertEquals("Completed first owner must not remain in the cached record", 1, first)
        assertEquals(1, second)
        assertEquals(0, current)
        assertSame(original, LedgerDatabases.open(app, account))
        assertEquals("Ordinary cached reads must not drain the current UI owner", 0, current)
        LedgerDatabases.close()
        assertEquals(1, first)
        assertEquals(1, second)
        assertEquals("Current owner is still retained until the actual close", 1, current)
    }

    private suspend fun localSession(): AccountSession {
        withContext(main) { holder.onLocalMode() }
        val session = (withTimeout(5_000) { holder.state.first { it is AccountSessionState.Ready } } as AccountSessionState.Ready).session
        sessions += session
        files += LedgerDatabases.localDatabaseName(LedgerMode(app).guestOrNull()!!)
        return session
    }

    private suspend fun seed(db: AppDatabase, merchant: String, amount: Double): Long =
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = amount,
            category = "Food", merchant = merchant, paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))

    private class HeldDelete(db: AppDatabase, dispatcher: CoroutineDispatcher,
        started: CompletableDeferred<Unit>, release: CompletableDeferred<Unit>, result: CompletableDeferred<Throwable?>,
    ) : ViewModel(), SessionClosingWork {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val pending = PendingDeletes(scope) { id ->
            started.complete(Unit)
            release.await()
            result.complete(runCatching { db.transactionDao().deleteTransactionById(id) }.exceptionOrNull())
        }
        fun hide(id: Long) = pending.hide(id)
        override fun onCleared() { pending.commitAll() }
        override suspend fun awaitSessionClosingWork() {
            pending.commitAll()
            scope.coroutineContext[Job]!!.children.toList().joinAll()
            scope.cancel()
        }
    }
}
