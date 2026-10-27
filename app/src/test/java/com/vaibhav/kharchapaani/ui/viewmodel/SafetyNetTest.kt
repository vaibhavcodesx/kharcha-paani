package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import com.vaibhav.kharchapaani.ui.components.NewSubscription
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plan T1.8: a save that fails in a way nobody planned for says so, and the app stays up. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SafetyNetTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `the person is told in words fit for them`() {
        assertEquals("Couldn't save. Your phone may be out of space.", Submissions.failureMessage(SQLiteFullException("disk full"), application))
        assertEquals("Couldn't save just now. Try again.", Submissions.failureMessage(IllegalStateException("closed"), application))
        assertEquals("Something went wrong and that wasn't saved. Try again.", Submissions.failureMessage(RuntimeException(), application))
    }

    @Test
    fun `a failed action is reported once, frees the action for a retry, and cancellation still cancels`() = runBlocking {
        val messages = mutableListOf<String>()
        val submissions = Submissions(this, application, onFailure = { messages += it })

        submissions.launch(Submit.SaveEntry) { throw SQLiteFullException("disk full") }?.join()
        assertEquals(listOf("Couldn't save. Your phone may be out of space."), messages)
        assertTrue("the action is free again", Submit.SaveEntry !in submissions.inFlight.value)

        val cancelled = submissions.launch(Submit.SaveEntry) { throw CancellationException("left the screen") }
        cancelled?.join()
        assertTrue(cancelled!!.isCancelled)
        assertEquals("cancellation isn't a failure to tell anyone about", 1, messages.size)
    }

    @Test
    fun `a save the ledger refuses comes back as a message instead of a crash`() = runBlocking {
        // A ₹0 subscription: the repository's guard refuses it with an exception nobody catches
        // on the way, the shape of every unplanned failure the net is for.
        viewModel.addSubscription(NewSubscription("Netflix", 0.0, "Entertainment", "UPI", SubscriptionCycle.MONTHLY, LocalDate.of(2026, 10, 1)), chargeFromStart = true)

        // onFailure publishes the message before finally releases the submission guard.
        // Await both outcomes instead of racing that final state update on the IO thread.
        withTimeout(10_000) {
            while (viewModel.statusMessage.value == null || Submit.AddSubscription in viewModel.submitting.value) delay(20)
        }
        assertEquals("Something went wrong and that wasn't saved. Try again.", viewModel.statusMessage.value)
        assertTrue("the action can be tried again", Submit.AddSubscription !in viewModel.submitting.value)
    }
}
