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
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.ExportRange
import com.vaibhav.kharchapaani.ui.components.ExportContent
import com.vaibhav.kharchapaani.ui.components.ExportScope
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExportStringResourcesTest {
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
    @After fun tearDown(): Unit = runBlocking {
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        withTimeout(10000) { while (!job.isCompleted) { dispatcher.scheduler.runCurrent(); delay(10) } }
        db.close(); Dispatchers.resetMain()
    }
    @Test fun `resource labels keep the real range count and selected export scope`(): Unit = runBlocking {
        for (day in listOf(LocalDate.of(2026, 7, 9), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2))) {
            db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 10.0,
                category = "Food", merchant = "Tea", paymentMethod = "Cash", date = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
        var exported: ExportRange? = null
        compose.setContent { SplitFlowTheme {
            val entries by vm.rawTransactions.collectAsState()
            var scope by remember { mutableStateOf(ExportScope.Month) }
            ExportContent(entries, scope, YearMonth.of(2026, 9), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 2),
                YearMonth.of(2026, 7), LocalDate.of(2026, 10, 1), onScopeChange = { scope = it },
                onMonthChange = {}, onDatesChange = { _, _ -> }, onExport = { exported = it })
        } }
        compose.waitUntil(10000) { dispatcher.scheduler.runCurrent(); compose.onAllNodesWithText("2 entries").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Export to a spreadsheet").assertExists()
        compose.onNodeWithTag("export_scope_Everything").performClick()
        compose.onNodeWithText("3 entries").assertExists()
        compose.onNodeWithTag("export_button").performClick()
        assertEquals(ExportRange.Everything, exported)
    }
}
