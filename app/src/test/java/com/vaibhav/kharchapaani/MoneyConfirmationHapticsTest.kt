package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.ui.components.MoneyConfirmationHaptics
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
class MoneyConfirmationHapticsTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private val feedback = mutableListOf<HapticFeedbackType>()
    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { feedback += hapticFeedbackType }
    }

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        compose.setContent { CompositionLocalProvider(LocalHapticFeedback provides haptics) {
            MoneyConfirmationHaptics(vm.moneyConfirmations)
        } }
        compose.waitForIdle()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }
    private suspend fun settled() {
        until { vm.submitting.value.isEmpty() }
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
    }
    private fun row(amount: Double) = StatementTransactionItem(date = System.currentTimeMillis(),
        dateFormatted = "27 Oct 2026", monthYearKey = "October 2026", monthSortKey = "202610",
        amount = amount, merchant = "Lunch")

    @After fun tearDown() = runBlocking {
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        until { job.isCompleted }
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `saved entry recorded payment and imported batch each confirm once after the write`() = runBlocking {
        vm.openManualAdd("expense"); vm.updateAmount("20"); vm.updateMerchant("Tea")
        vm.saveCurrentTransaction(askForGroup = false); settled()
        assertEquals(1, db.transactionDao().getAllTransactions().first().size)
        assertEquals(listOf(HapticFeedbackType.Confirm), feedback)
        vm.recordSettlementPayment(-1, 2, 30.0, "Thanks"); settled()
        assertEquals(1, db.settlementDao().getAllSettlements().first().size)
        assertEquals(2, feedback.size)
        vm.confirmImportStatementTransactions(listOf(row(40.0), row(50.0))); settled()
        assertEquals(3, db.transactionDao().getAllTransactions().first().size)
        assertEquals(List(3) { HapticFeedbackType.Confirm }, feedback)
    }

    @Test fun `invalid entry empty import invalid rows and failed payment do not confirm`() = runBlocking {
        vm.openManualAdd("expense"); vm.updateAmount("0"); vm.saveCurrentTransaction(false); settled()
        vm.confirmImportStatementTransactions(emptyList()); settled()
        vm.confirmImportStatementTransactions(listOf(row(Double.NaN))); settled()
        vm.recordSettlementPayment(-1, 2, Double.NaN, ""); settled()
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        assertTrue(db.settlementDao().getAllSettlements().first().isEmpty())
        assertTrue(feedback.isEmpty())
    }
}
