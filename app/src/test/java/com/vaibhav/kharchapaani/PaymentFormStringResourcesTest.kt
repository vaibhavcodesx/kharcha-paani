package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.components.IouContent
import com.vaibhav.kharchapaani.ui.components.SettleUpContent
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
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
@Config(sdk = [36], qualifiers = "w411dp-h1200dp")
class PaymentFormStringResourcesTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val friend = PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF")
    private val local = SharedRecordGroups.Options(emptyList(), emptyList(), involvesGroupmates = false)
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(friend)
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
    }
    private suspend fun until(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }
    @After fun tearDown(): Unit = runBlocking {
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear(); until { job.isCompleted }; db.close(); Dispatchers.resetMain()
    }
    @Test fun `IOU resource labels keep the lent direction amount and default note`(): Unit = runBlocking {
        compose.setContent { SplitFlowTheme {
            val people by vm.people.collectAsState()
            IouContent(people, groupOptionsFor = { local }, onCreateGroup = {}, onConfirm = vm::recordIou)
        } }
        compose.waitUntil(10000) { dispatcher.scheduler.runCurrent(); compose.onAllNodesWithText("Rohan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Log an IOU").assertExists()
        compose.onNodeWithTag("iou_person_2").performClick()
        compose.onNodeWithTag("iou_amount_input").performTextInput("125.50")
        compose.onNodeWithTag("confirm_iou_button").assertTextEquals("Record IOU").performClick()
        until { vm.submitting.value.isEmpty() }
        val saved = db.settlementDao().getAllSettlements().first().single()
        assertTrue(saved.isIou)
        assertEquals(-1L, saved.fromPersonId); assertEquals(2L, saved.toPersonId)
        assertEquals(125.5, saved.amount, 0.0)
        assertEquals("Informal Loan", saved.note)
    }
    @Test fun `settlement resource names and direction still clear the real balance`(): Unit = runBlocking {
        db.transactionDao().insertTransaction(TransactionEntity(id = 1, type = "expense", amount = 200.0,
            category = "Food", merchant = "Dinner", paymentMethod = "Cash", isSplit = true))
        db.splitDao().insertSplit(SplitEntity(id = 1, transactionId = 1, totalAmount = 200.0, paidByPersonId = -1))
        db.splitDao().insertSplitShares(listOf(SplitShareEntity(splitId = 1, personId = -1, shareAmount = 0.0),
            SplitShareEntity(splitId = 1, personId = 2, shareAmount = 200.0)))
        val suggested = LedgerRepository(db).personBalances.first().single().netBalance
        compose.setContent { SplitFlowTheme {
            SettleUpContent(friend, suggested, local, onCreateGroup = {}, onConfirm = vm::recordSettlementPayment)
        } }
        compose.onNodeWithText("Settle up with Rohan").assertExists()
        compose.onNodeWithText("Rohan owes you ₹200.").assertExists()
        compose.onNodeWithTag("confirm_settle_button").assertTextEquals("Record settlement").performClick()
        until { vm.submitting.value.isEmpty() }
        val saved = db.settlementDao().getAllSettlements().first().single()
        assertFalse(saved.isIou); assertTrue(saved.isConfirmed)
        assertEquals(2L, saved.fromPersonId); assertEquals(-1L, saved.toPersonId)
        assertEquals(0.0, LedgerRepository(db).personBalances.first().single().netBalance, 0.0)
    }
}
