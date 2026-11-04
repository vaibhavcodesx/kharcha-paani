package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import com.vaibhav.kharchapaani.ui.viewmodel.PendingDeletes
import com.vaibhav.kharchapaani.data.settings.LedgerMode
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestSessionHolderTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = executor.asCoroutineDispatcher()
    private val store = ViewModelStore()
    private lateinit var holder: AccountSessionHolder
    private val account = UUID.randomUUID().toString()
    private val files = mutableSetOf<String>()

    @Before fun setUp() {
        app.getSharedPreferences(LedgerMode.PREFS, Application.MODE_PRIVATE).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        Dispatchers.setMain(main)
        holder = AccountSessionHolder(app)
        store.put("holder", holder)
    }

    @After fun tearDown() = runBlocking {
        try {
            val session = (holder.state.value as? AccountSessionState.Ready)?.session
            val job = holder.viewModelScope.coroutineContext[Job]!!
            withContext(main) { store.clear() }
            withTimeout(5_000) {
                job.join()
                withContext(main) { holder.awaitSessionClosingWork() }
                if (session != null) withContext(main) { session.awaitClosed() }
            }
            LedgerDatabases.close()
            WorkManager.getInstance(app).cancelAllWork().result.get()
            files.forEach { app.deleteDatabase(it) }
            app.getSharedPreferences(LedgerMode.PREFS, Application.MODE_PRIVATE).edit().clear().commit()
        } finally {
            Dispatchers.resetMain()
            main.close()
        }
        Unit
    }

    @Test fun `opening local mode durably selects its isolated ledger without scheduling sync`() = runBlocking {
        withContext(main) { holder.onLocalMode() }
        val session = ready { it.isLocal }
        val guest = LedgerMode(app).guestOrNull()!!
        files += LedgerDatabases.localDatabaseName(guest)
        assertEquals(guest.id, session.userId)
        assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork("sync-${guest.id}").get().isEmpty())
        assertTrue(WorkManager.getInstance(app).getWorkInfosForUniqueWork("sync-periodic-${guest.id}").get().isEmpty())
    }

    @Test fun `switch to local awaits old view model cleanup before closing its database`() = runBlocking {
        files += LedgerDatabases.databaseName(account)
        withContext(main) { holder.onAccountChanged(account) }
        val old = ready { !it.isLocal && it.userId == account }
        old.database.personDao().getAllPeopleDirect() // Force lazy Room open.
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Boolean>()
        val vm = object : ViewModel() {}
        withContext(main) {
            old.register(vm)
            old.viewModelStore.put("cleanup", vm)
            vm.viewModelScope.launch {
                try { started.complete(Unit); awaitCancellation() }
                finally {
                    withContext(NonCancellable) {
                        yield()
                        cleaned.complete(old.database.isOpen)
                    }
                }
            }
        }
        withTimeout(5_000) { started.await() }
        withContext(main) { holder.onLocalMode() }
        val local = ready { it.isLocal }
        files += LedgerDatabases.localDatabaseName(LedgerMode(app).guestOrNull()!!)
        assertTrue("cleanup must run with its ledger still open", cleaned.await())
        assertFalse(old.database.isOpen)
        assertTrue(vm.viewModelScope.coroutineContext[Job]!!.isCompleted)
        assertTrue(local.isLocal)
    }

    @Test fun `switch waits for deletion work outside view model scope before closing real Room`() = runBlocking {
        files += LedgerDatabases.databaseName(account)
        withContext(main) { holder.onAccountChanged(account) }
        val old = ready { !it.isLocal }
        val deleted = old.database.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 75.50,
            category = "Food", merchant = "Waiting delete", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        old.database.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 9.25,
            category = "Food", merchant = "Kept", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val deletionResult = CompletableDeferred<Throwable?>()
        val vm = HeldDeletionViewModel(old.database, main, started, release, deletionResult)
        try {
            withContext(main) {
                old.register(vm)
                old.viewModelStore.put("held-delete", vm)
                vm.hide(deleted)
                holder.onLocalMode()
            }
            files += LedgerDatabases.localDatabaseName(LedgerMode(app).guestOrNull()!!)
            withTimeout(5_000) { started.await() }
            withContext(main) { } // Process the queued switch before releasing the held deletion.
            assertTrue("Room must stay open while the deletion is held", old.database.isOpen)
            assertTrue("The next ledger must wait for deletion cleanup", holder.state.value !is AccountSessionState.Ready)
            release.complete(Unit)
            val local = ready { it.isLocal }
            assertFalse(old.database.isOpen)
            assertTrue(local.isLocal)
            assertNull("Deletion must finish successfully before Room closes", deletionResult.await())
            val rows = LedgerDatabases.open(app, account).transactionDao().getAllTransactionsDirect()
            assertEquals(listOf("Kept"), rows.map { it.merchant })
            assertEquals(9.25, rows.single().amount, 0.0)
        } finally {
            release.complete(Unit)
            withTimeout(5_000) { withContext(main) { vm.awaitSessionClosingWork() } }
        }
    }

    @Test fun `actual MainViewModel pending delete commits before a switch closes its local ledger`() = runBlocking {
        withContext(main) { holder.onLocalMode() }
        val local = ready { it.isLocal }
        val guest = LedgerMode(app).guestOrNull()!!
        files += LedgerDatabases.localDatabaseName(guest)
        files += LedgerDatabases.databaseName(account)
        val deleted = local.database.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 125.50,
            category = "Food", merchant = "Deleted on close", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        local.database.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 14.25,
            category = "Food", merchant = "Still here", paymentMethod = "Cash", syncState = SyncState.LOCAL_ONLY))
        lateinit var vm: MainViewModel
        lateinit var vmJob: Job
        withContext(main) {
            vm = MainViewModel(app, local.database, guest.id, isLocal = true)
            vmJob = vm.viewModelScope.coroutineContext[Job]!!
            local.register(vm)
            local.viewModelStore.put("main", vm)
            vm.deleteTransaction(deleted)
            LedgerMode(app).useCloud()
            holder.onAccountChanged(account)
        }
        ready { !it.isLocal && it.userId == account }
        assertTrue(vmJob.isCompleted)
        assertFalse(local.database.isOpen)
        val rows = LedgerDatabases.openLocal(app, guest).transactionDao().getAllTransactionsDirect()
        assertEquals(listOf("Still here"), rows.map { it.merchant })
        assertEquals(14.25, rows.single().amount, 0.0)
    }

    private class HeldDeletionViewModel(
        db: AppDatabase,
        dispatcher: CoroutineDispatcher,
        started: CompletableDeferred<Unit>,
        release: CompletableDeferred<Unit>,
        result: CompletableDeferred<Throwable?>,
    ) : ViewModel(), SessionClosingWork {
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        private val pending = PendingDeletes(scope) { id ->
            started.complete(Unit)
            release.await()
            // Retain a closed-ledger failure as test evidence instead of leaking an uncaught
            // coroutine exception into the next test during the expected red run.
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

    private suspend fun ready(predicate: (AccountSession) -> Boolean): AccountSession =
        (withTimeout(5_000) {
            holder.state.first { it is AccountSessionState.Ready && predicate(it.session) }
        } as AccountSessionState.Ready).session
}
