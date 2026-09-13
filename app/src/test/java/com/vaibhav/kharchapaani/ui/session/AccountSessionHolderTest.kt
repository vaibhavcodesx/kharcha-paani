package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class AccountSessionHolderTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val alice = UUID.randomUUID().toString()
    private val bob = UUID.randomUUID().toString()

    // A real thread for Dispatchers.Main: opening a ledger hops to Dispatchers.IO, and the test
    // waits on state from this (Robolectric main) thread.
    private val mainExecutor = Executors.newSingleThreadExecutor()
    private val main = mainExecutor.asCoroutineDispatcher()
    private lateinit var holder: AccountSessionHolder

    @Before
    fun setUp() {
        // Starting a session schedules periodic sync.
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
        Dispatchers.setMain(main)
        holder = AccountSessionHolder(application)
    }

    @After
    fun tearDown() = runBlocking {
        LedgerDatabases.close()
        Dispatchers.resetMain()
        mainExecutor.shutdown()
        application.databaseList().forEach { application.deleteDatabase(it) }
    }

    @Test
    fun `switching accounts clears the previous account's view models and closes its ledger`() = runBlocking {
        val aliceSession = openSessionFor(alice)
        val aliceViewModel = ViewModelProvider(aliceSession, ViewModelProvider.NewInstanceFactory())[TrackingViewModel::class.java]

        val bobSession = openSessionFor(bob)

        assertTrue(aliceViewModel.cleared)
        assertFalse(aliceSession.database.isOpen)
        assertEquals(bob, bobSession.userId)
    }

    @Test
    fun `signing out ends the session`() = runBlocking {
        val aliceSession = openSessionFor(alice)
        val aliceViewModel = ViewModelProvider(aliceSession, ViewModelProvider.NewInstanceFactory())[TrackingViewModel::class.java]

        withContext(main) { holder.onAccountChanged(null) }
        withTimeout(5_000) { holder.state.first { it == AccountSessionState.None && !aliceSession.database.isOpen } }

        assertTrue(aliceViewModel.cleared)
    }

    private suspend fun openSessionFor(userId: String): AccountSession {
        withContext(main) { holder.onAccountChanged(userId) }
        val ready = withTimeout(5_000) {
            holder.state.first { it is AccountSessionState.Ready && it.session.userId == userId }
        }
        return (ready as AccountSessionState.Ready).session
    }

    class TrackingViewModel : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }
}
