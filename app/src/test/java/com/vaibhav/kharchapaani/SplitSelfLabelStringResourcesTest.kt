package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.collectAsState
import com.vaibhav.kharchapaani.ui.components.SplitEditor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
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
class SplitSelfLabelStringResourcesTest {
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
        vm = MainViewModel(app, db, "53535353-2222-3333-4444-555555555555")
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
    @Test fun `self display uses the resource label without rewriting participant data`(): Unit = runBlocking {
        vm.openManualAdd("expense"); vm.updateAmount("125.50"); vm.updateMerchant("Dinner's 50%")
        vm.setIsSplit(true)
        if (!vm.shares.value.first { it.personId == 2L }.isSelected) vm.togglePersonShare(2)
        var locked by mutableStateOf(false)
        compose.setContent { SplitFlowTheme {
            val shares = vm.shares.collectAsState().value
            // A distinct source label proves the UI uses the self ID, without replacing VM flows.
            val displayInputs = shares.map { if (it.personId == -1L) it.copy(personName = "Self's 50% source") else it }
            SplitEditor(vm.amount.collectAsState().value, vm.splitMode.collectAsState().value,
                vm.paidByPersonId.collectAsState().value, displayInputs, vm::setSplitMode,
                vm::setPaidByPersonId, vm::togglePersonShare, vm::updateExactShareAmount,
                vm::updateSharePercentage, {}, splitLockedBy = if (locked) "Rohan" else null)
        } }
        compose.onAllNodesWithText("You").assertCountEquals(2)
        compose.onNodeWithText("Self's 50% source").assertDoesNotExist()
        compose.runOnIdle { locked = true }
        compose.onAllNodesWithText("You").assertCountEquals(2)
        compose.onNodeWithText("Self's 50% source").assertDoesNotExist()
        assertEquals("You", vm.shares.value.single { it.personId == -1L }.personName)
        vm.saveCurrentTransaction(false); until { vm.submitting.value.isEmpty() }
        val saved = db.transactionDao().getAllTransactions().first().single()
        assertEquals(125.50, saved.amount, 0.0)
        assertEquals("Dinner's 50%", saved.merchant)
    }
}
