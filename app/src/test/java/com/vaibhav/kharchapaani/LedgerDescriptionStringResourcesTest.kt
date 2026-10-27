package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.ui.components.PersonLedgerContent
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
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
class LedgerDescriptionStringResourcesTest {
    @get:Rule val compose = createComposeRule()
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
    @Test fun `actual friend history preserves note data statuses and net amounts`(): Unit = runBlocking {
        db.settlementDao().insertSettlement(SettlementEntity(id = 1, fromPersonId = -1, toPersonId = 2,
            amount = 125.50, isIou = true, note = "It's 50%", status = SettlementStatus.CONFIRMED))
        db.settlementDao().insertSettlement(SettlementEntity(id = 2, fromPersonId = 2, toPersonId = -1,
            amount = 25.0, note = "Pending's 50%", status = SettlementStatus.PENDING))
        val history = vm.getPersonLedgerFlow(2).first()
        assertEquals("You lent them", history.single { it.id == "settlement_1" }.title)
        assertEquals("Pending's 50% · waiting to be confirmed", history.single { it.id == "settlement_2" }.subtitle)
        assertEquals(125.50, history.sumOf { it.netChange }, 0.0)
        compose.setContent { SplitFlowTheme {
            PersonLedgerContent(vm.people.value.single { it.id == 2L }, history, vm::openSettleUp)
        } }
        compose.onNodeWithText("You lent them").assertExists()
        compose.onNodeWithText("It's 50%", substring = true).assertExists()
        assertEquals(2, com.vaibhav.kharchapaani.data.repository.LedgerRepository(db).allSettlements.first().size)
    }
}
