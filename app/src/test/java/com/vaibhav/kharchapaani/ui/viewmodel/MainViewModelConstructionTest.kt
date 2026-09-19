package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.domain.AI_DAILY_CAP
import com.vaibhav.kharchapaani.domain.AskBoard
import com.vaibhav.kharchapaani.domain.AskState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds the view model. That is the whole test, and it exists because of what happened without it.
 *
 * On 2026-09-19 an `init { publishUpiIfMissing() }` block was added above
 * `private val settings = AppSettings(application)`. Kotlin runs initialisers and `init` blocks in
 * declaration order, so `settings` was still null when the block ran, and the app died with an NPE
 * on **every** launch, before a single frame. All 447 unit tests passed, because not one of them
 * constructed a `MainViewModel`. It was caught by installing the APK and starting the activity —
 * which is a good habit and a bad safety net, because it is manual and depends on remembering.
 *
 * `init` blocks now do real work here (publishing a UPI ID, repairing person colours), so the
 * ordering hazard is permanent. This closes it: anything that throws while the class is being
 * constructed fails the build instead of the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainViewModelConstructionTest {

    private lateinit var db: AppDatabase
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // The view model schedules sync work as it is built, so WorkManager has to exist before it
        // does. That it fails loudly without this is the point: constructing this class really does
        // reach the launch-path machinery, which is why constructing it is worth a test.
        WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = MainViewModel(
        application = ApplicationProvider.getApplicationContext<Application>(),
        db = db,
        userId = "11111111-2222-3333-4444-555555555555",
    )

    @Test
    fun `the view model can be constructed at all`() {
        // The one assertion that would have caught the launch crash.
        assertNotNull(viewModel())
    }

    @Test
    fun `its init blocks run without taking the app down`() = runTest(dispatcher) {
        val vm = viewModel()

        // publishUpiIfMissing and repairPersonColours are both launched from init. Draining the
        // dispatcher runs them here rather than on someone's phone. Neither has anything to do on
        // an empty database, which is exactly the state a fresh install is in.
        dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(vm.people.value)
    }

    @Test
    fun `everything the screens read has a value before anything is collected`() {
        // A screen reads these on its first composition, so a null or a throw here is a launch
        // crash wearing a different hat.
        val vm = viewModel()

        assertEquals(AskBoard(), vm.askBoard.value)
        assertEquals(AskState.Idle, vm.tripReview.value)
        assertEquals(AI_DAILY_CAP, vm.aiAsksLeft.value)
        assertEquals(emptyList<Any>(), vm.people.value)
        assertEquals(emptyList<Any>(), vm.groups.value)
        assertEquals(0, vm.currentTab.value)
        assertNotNull(vm.activityFeed.value)
        assertNotNull(vm.insightsMonth.value)
    }
}
