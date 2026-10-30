package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.ui.components.FriendRow
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
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
class AskStatusStringResourcesTest {
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
    @Test fun `asked status keeps its calendar wording and opens actual friend history`(): Unit = runBlocking {
        db.settlementDao().insertSettlement(SettlementEntity(fromPersonId = -1, toPersonId = 2, amount = 125.50, isIou = true))
        val balance = LedgerRepository(db).personBalances.first().single { it.person.id == 2L }
        val now = System.currentTimeMillis()
        compose.setContent { SplitFlowTheme {
            FriendRow(balance, { vm.openPersonLedger(balance.person) }, { vm.openSettleUp(balance.person) }, {},
                askedAt = now, now = now)
        } }
        compose.onNodeWithText("Asked today", substring = true).assertExists()
        compose.onNodeWithText("Rohan").performClick()
        assertEquals(balance.person, vm.selectedPersonForLedger.value)
        assertEquals(125.50, balance.netBalance, 0.0)
    }
}
