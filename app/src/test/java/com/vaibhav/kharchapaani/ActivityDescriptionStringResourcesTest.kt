package com.vaibhav.kharchapaani

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityDescriptionStringResourcesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val screen = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel

    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        screen.launch { vm.people.collect {} }
        until { vm.people.value.any { it.id == 2L } }
    }
    private suspend fun until(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }
    @After fun tearDown(): Unit = runBlocking {
        screen.cancel()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear(); until { job.isCompleted }
        db.close(); Dispatchers.resetMain()
    }
    @Test fun `actual activity descriptions keep waiting age and quote response data`(): Unit = runBlocking {
        db.settlementDao().insertSettlement(SettlementEntity(id = 1, fromPersonId = -1, toPersonId = 2,
            amount = 125.50, status = SettlementStatus.PENDING, recordedByPersonId = -1, createdAt = 0))
        screen.launch { vm.activityFeed.collect {} }
        until { vm.activityFeed.value.filterIsInstance<ActivityItem.Settlement>().any { it.settlement.id == 1L } }
        val item = vm.activityFeed.value.filterIsInstance<ActivityItem.Settlement>().single()
        assertEquals("You paid Rohan", item.title(app))
        assertEquals("Waiting for Rohan to confirm · 9 days. Withdraw it if it's stuck.", item.statusLine(app, 9 * 86_400_000L))
        val rejected = item.copy(settlement = item.settlement.copy(status = SettlementStatus.REJECTED,
            responseNote = "It's 50% wrong"))
        assertEquals("Rohan says this isn't right: “It's 50% wrong”", rejected.statusLine(app))
        assertEquals(125.50, db.groupSyncDao().settlementById(1)!!.amount, 0.0)
    }
}
