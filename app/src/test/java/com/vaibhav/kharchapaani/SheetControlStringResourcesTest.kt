package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.runtime.mutableStateOf
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
import com.vaibhav.kharchapaani.ui.components.LargeAmountDialog
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SheetControlStringResourcesTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
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
    @Test fun `large amount copy keeps every digit and confirms a real save once`(): Unit = runBlocking {
        val amount = 1234567.89
        vm.openManualAdd("expense"); vm.updateAmount(Money.input(amount)); vm.updateMerchant("Family party")
        val visible = mutableStateOf(true)
        compose.setContent { SplitFlowTheme {
            if (visible.value) LargeAmountDialog(amount,
                onConfirm = { visible.value = false; vm.saveCurrentTransaction(false) },
                onDismiss = { visible.value = false })
        } }
        compose.onNodeWithText("That's ₹12,34,567.89. Right?").assertExists()
        compose.onNodeWithText("Just checking for an extra zero.").assertExists()
        compose.onNodeWithTag("large_amount_confirm").assertTextEquals("Yes, save").performClick()
        until { vm.submitting.value.isEmpty() }
        val saved = db.transactionDao().getAllTransactions().first()
        assertEquals(1, saved.size)
        assertEquals(amount, saved.single().amount, 0.0)
    }
}
