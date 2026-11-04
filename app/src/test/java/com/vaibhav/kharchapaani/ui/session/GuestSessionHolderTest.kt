package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
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
            val job = holder.viewModelScope.coroutineContext[Job]!!
            withContext(main) { store.clear() }
            withTimeout(5_000) { job.join() }
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
        val vm = ViewModel()
        withContext(main) {
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

    private suspend fun ready(predicate: (AccountSession) -> Boolean): AccountSession =
        (withTimeout(5_000) {
            holder.state.first { it is AccountSessionState.Ready && predicate(it.session) }
        } as AccountSessionState.Ready).session
}
