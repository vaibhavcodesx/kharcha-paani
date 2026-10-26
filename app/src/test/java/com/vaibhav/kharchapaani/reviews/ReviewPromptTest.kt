package com.vaibhav.kharchapaani.reviews

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.testing.FakeReviewManager
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReviewPromptTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val screen = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private lateinit var manager: CountingReviewManager
    private lateinit var eligibility: ReviewEligibility
    private lateinit var requester: ReviewRequester
    private var now = 0L
    private var allowed = true
    private val friend = PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF")

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        app.getSharedPreferences("play_review_preferences", 0).edit().clear().commit()
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(friend)
        db.groupDao().insertGroup(GroupEntity(id = 7, name = "Trip", syncState = SyncState.SYNCED))
        db.groupDao().insertGroupMembers(listOf(-1L, 2L).map { GroupMemberEntity(groupId = 7, personId = it) })
        db.settlementDao().insertSettlement(SettlementEntity(id = 50, fromPersonId = 2, toPersonId = -1, amount = 30.0))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        manager = CountingReviewManager(app)
        eligibility = ReviewEligibility(app) { now }
        requester = ReviewRequester(activity.get(), manager, eligibility) { allowed }
        screen.launch { vm.reviewMoments.collect { requester.request() } }
        dispatcher.scheduler.runCurrent()
    }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }
    private fun idle() { dispatcher.scheduler.runCurrent(); shadowOf(Looper.getMainLooper()).idle() }

    @After fun tearDown() = runBlocking {
        screen.cancel()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        until { job.isCompleted }
        activity.pause().stop().destroy()
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `first week does not prompt and an eligible attempt is claimed once`() {
        assertFalse(eligibility.claim())
        now = 7 * DAY
        assertTrue(eligibility.claim())
        assertFalse(eligibility.claim())
    }

    @Test fun `the six month cooldown survives recreation and a clock going backwards`() {
        now = 10 * DAY
        assertTrue(eligibility.claim())
        val reopened = ReviewEligibility(app) { now }
        now = 20 * DAY
        assertFalse(reopened.claim())
        now = 9 * DAY
        assertFalse(reopened.claim())
        now = 190 * DAY
        assertTrue(reopened.claim())
    }

    @Test fun `a confirmed local settlement reaches Play after it is saved`() = runBlocking {
        now = 10 * DAY
        vm.openSettleUp(friend)
        vm.recordSettlementPayment(-1, 2, 100.0, "Thanks")
        until { !vm.isSettleUpOpen.value && vm.submitting.value.isEmpty() }
        until { manager.requests == 1 }
        idle()
        assertTrue(db.settlementDao().getAllSettlements().first().any { it.amount == 100.0 && it.isConfirmed })
        assertEquals(1, manager.requests)
        assertEquals(1, manager.launches)
    }

    @Test fun `confirming another members pending payment can supply the happy moment`() = runBlocking {
        now = 10 * DAY
        db.settlementDao().insertSettlement(SettlementEntity(id = 60, fromPersonId = 2, toPersonId = -1, amount = 80.0,
            groupId = 7, status = SettlementStatus.PENDING, recordedByPersonId = 2))
        vm.openSettlementAnswer(60)
        vm.answerSettlement(true)
        until { db.groupSyncDao().settlementById(60)?.isConfirmed == true && vm.statusMessage.value?.startsWith("Confirmed") == true }
        until { manager.requests == 1 }
        idle()
        assertEquals(1, manager.requests)
    }

    @Test fun `old history pending payments IOUs and failed actions never request a review`() = runBlocking {
        now = 10 * DAY
        idle()
        assertEquals(0, manager.requests)
        vm.openSettleUp(friend)
        vm.recordSettlementPayment(-1, 2, 100.0, "", groupId = 7)
        until { !vm.isSettleUpOpen.value }
        vm.openIouDialog()
        vm.recordIou(-1, 2, 40.0, "")
        until { !vm.isIouOpen.value }
        vm.recordSettlementPayment(-1, 2, Double.NaN, "")
        until { vm.statusMessage.value != null && vm.submitting.value.isEmpty() }
        idle()
        assertEquals(0, manager.requests)
    }

    @Test fun `a busy UI and a later busy callback cannot open the review flow`() {
        now = 10 * DAY
        allowed = false
        requester.request(); idle()
        assertEquals(0, manager.requests)
        allowed = true
        requester.request()
        allowed = false
        idle()
        assertEquals(1, manager.requests)
        assertEquals(0, manager.launches)
    }

    @Test fun `Play failure is silent and consumes the cooldown instead of retrying`() {
        now = 10 * DAY
        manager.fail = true
        requester.request(); idle()
        requester.request(); idle()
        assertEquals(1, manager.requests)
        assertEquals(0, manager.launches)
    }

    private class CountingReviewManager(app: Application) : ReviewManager {
        private val real = FakeReviewManager(app)
        var requests = 0
        var launches = 0
        var fail = false
        override fun requestReviewFlow(): Task<ReviewInfo> {
            requests++
            return if (fail) Tasks.forException(IllegalStateException("Play unavailable")) else real.requestReviewFlow()
        }
        override fun launchReviewFlow(activity: Activity, info: ReviewInfo): Task<Void> {
            launches++
            return real.launchReviewFlow(activity, info)
        }
    }

    private companion object { const val DAY = 86_400_000L }
}
