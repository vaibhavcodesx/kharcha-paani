package com.vaibhav.kharchapaani

import android.app.Application
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
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.domain.LocalStatementParser
import com.vaibhav.kharchapaani.ui.components.StatementImportContent
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
@Config(sdk = [36], qualifiers = "w411dp-h2000dp")
class ImportStringResourcesTest {
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
    @Test fun `selection plurals change without changing which real rows are saved`(): Unit = runBlocking {
        val parsed = LocalStatementParser.parseDateToTimestamp("2026-10-27")
        fun row(name: String, amount: Double) = StatementTransactionItem(date = parsed.first,
            dateFormatted = parsed.second, monthYearKey = parsed.third, monthSortKey = "202610", merchant = name, amount = amount)
        val statement = LocalStatementParser.buildStatementGroups("My bank", "", listOf(row("Tea", 20.5), row("Bus", 30.0)))
        compose.setContent { SplitFlowTheme { StatementImportContent(statement, vm::confirmImportStatementTransactions) } }
        compose.onNodeWithTag("confirm_import_statement_button").assertTextEquals("Import 2 entries")
        compose.onNodeWithText("2 of 2").assertExists()
        compose.onNodeWithText("Untick all").performClick()
        compose.onNodeWithTag("confirm_import_statement_button").assertIsNotEnabled()
        compose.onNodeWithText("Tick entries to import").assertExists()
        compose.onNodeWithText("Tick all").performClick()
        compose.onNodeWithContentDescription("Import Tea").performClick()
        compose.onNodeWithTag("confirm_import_statement_button").assertTextEquals("Import 1 entry").performClick()
        until { vm.submitting.value.isEmpty() }
        val saved = db.transactionDao().getAllTransactions().first().single()
        assertEquals("Bus", saved.merchant)
        assertEquals(30.0, saved.amount, 0.0)
    }
}
