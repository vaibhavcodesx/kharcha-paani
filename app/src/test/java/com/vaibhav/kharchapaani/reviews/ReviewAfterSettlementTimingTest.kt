package com.vaibhav.kharchapaani.reviews

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.gms.tasks.Task
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.util.concurrent.atomic.AtomicInteger

/** Actual money save -> actual composable collector -> Play boundary, while a snackbar is busy. */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [ReviewAfterSettlementTimingTest.PlayFactory::class],
    instrumentedPackages = ["com.google.android.play.core.review"])
class ReviewAfterSettlementTimingTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val compose = createComposeRule(effectContext = dispatcher)
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private lateinit var manager: CountingManager
    private var allowed by mutableStateOf(false)
    private var showCollector by mutableStateOf(true)
    private val delivered = AtomicInteger()
    private lateinit var screenLifecycle: Lifecycle

    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(dispatcher)
        app.getSharedPreferences("play_review_preferences", 0).edit().clear()
            .putLong("first_seen", System.currentTimeMillis() - 8 * 86_400_000L).commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        manager = CountingManager(app)
        PlayFactory.manager = manager
        assertSame("only the external Play boundary is replaced", manager, ReviewManagerFactory.create(app))
        val moments = vm.reviewMoments.onEach { delivered.incrementAndGet() }
        compose.setContent {
            screenLifecycle = LocalLifecycleOwner.current.lifecycle
            if (showCollector) ReviewAfterSettlement(moments, enabled = allowed)
        }
        pump()
        assertEquals(Lifecycle.State.RESUMED, screenLifecycle.currentState)
        compose.mainClock.autoAdvance = false
    }

    private fun pump() {
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
        dispatcher.scheduler.runCurrent()
        shadowOf(Looper.getMainLooper()).idle()
        dispatcher.scheduler.runCurrent()
    }

    private fun advanceUiBy(millis: Long) {
        // Composition, lifecycle collector and ViewModel deliberately share this test clock.
        compose.mainClock.advanceTimeBy(millis)
        pump()
    }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (!condition()) { pump(); delay(10) }
    }

    private suspend fun confirmPayment(amount: Double) {
        val before = delivered.get()
        vm.recordSettlementPayment(-1L, 2L, amount, "Test payment")
        until { delivered.get() > before && vm.submitting.value.isEmpty() &&
            db.settlementDao().getAllSettlements().first().any { it.amount == amount && it.isConfirmed } }
        pump()
    }

    @After fun tearDown(): Unit = runBlocking {
        if (::manager.isInitialized) {
            compose.runOnUiThread { showCollector = false }
            compose.mainClock.advanceTimeByFrame()
            pump()
        }
        val jobs = listOfNotNull(if (::vm.isInitialized) vm.viewModelScope.coroutineContext[Job] else null)
        store.clear()
        until { jobs.all { it.isCompleted } }
        if (::db.isInitialized) db.close()
        Dispatchers.resetMain()
    }

    @Test fun `the confirmed payment waits through a four second snackbar before asking Play`(): Unit = runBlocking {
        confirmPayment(100.0)
        advanceUiBy(4_000)
        assertEquals(0, manager.requests)
        assertFalse(app.getSharedPreferences("play_review_preferences", 0).contains("last_attempt"))

        compose.runOnUiThread { allowed = true }
        compose.mainClock.advanceTimeByFrame()
        // Readiness recomposes first; its snapshotFlow notification can need a later frame.
        compose.mainClock.advanceTimeUntil(timeoutMillis = 500) { manager.requests == 1 }
        pump()

        assertEquals("the success must survive its confirmation snackbar", 1, manager.requests)
        assertEquals(1, manager.launches)
        assertEquals(100.0, db.settlementDao().getAllSettlements().first().single().amount, 0.0)
    }

    @Test fun `a UI busy past the window drops the moment without consuming its cooldown`(): Unit = runBlocking {
        confirmPayment(80.0)
        advanceUiBy(8_000)
        compose.runOnUiThread { allowed = true }
        compose.mainClock.advanceTimeByFrame()
        pump()
        assertEquals(0, manager.requests)
        assertFalse(app.getSharedPreferences("play_review_preferences", 0).contains("last_attempt"))

        confirmPayment(90.0)
        advanceUiBy(750)
        assertEquals(1, manager.requests)
        assertEquals(2, db.settlementDao().getAllSettlements().first().size)
    }

    /** Fake only the external Play factory; the composable, eligibility and requester stay real. */
    @Implements(value = ReviewManagerFactory::class, isInAndroidSdk = false)
    class PlayFactory {
        companion object {
            lateinit var manager: ReviewManager
            @JvmStatic @Implementation fun create(context: Context): ReviewManager = manager
        }
    }

    private class CountingManager(app: Application) : ReviewManager {
        private val fake = FakeReviewManager(app)
        var requests = 0
        var launches = 0
        override fun requestReviewFlow(): Task<ReviewInfo> {
            requests++
            return fake.requestReviewFlow()
        }
        override fun launchReviewFlow(activity: Activity, info: ReviewInfo): Task<Void> {
            launches++
            return fake.launchReviewFlow(activity, info)
        }
    }
}
