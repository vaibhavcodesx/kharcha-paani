package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.collectAsState
import com.vaibhav.kharchapaani.ui.components.StatementTransactionRow
import com.vaibhav.kharchapaani.domain.StatementChecks
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import androidx.compose.foundation.layout.Column
import java.time.LocalDate
import java.time.ZoneOffset
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
class StatementWarningStringResourcesTest {
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
    @Test fun `actual review warnings keep wording and unticked selections`(): Unit = runBlocking {
        val zone = ZoneOffset.UTC
        val period = "01/01/2026 to 31/01/2026"
        fun row(id: String, day: String, amount: Double = 125.50, type: String = "expense", flag: String? = null) =
            StatementTransactionItem(id = id, date = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli(),
                dateFormatted = day, monthYearKey = "January 2026", monthSortKey = "202601", type = type,
                amount = amount, merchant = "Merchant's 50% $id", flag = flag)
        val reviewed = StatementChecks.review(listOf(
            row("no-date", "2026-01-05", flag = StatementChecks.NO_DATE),
            row("future", "2026-01-12"), row("outside", "2025-12-15"),
            row("large", "2026-01-05", 100000.0, "income"),
            row("small-a", "2026-01-05", 10.0), row("small-b", "2026-01-05", 20.0),
            row("custom", "2026-01-05", flag = "Bank's 50% note")), period, LocalDate.of(2026, 1, 10), zone)
        val flagged = reviewed.filter { it.flag != null }
        assertEquals(5, flagged.size)
        assertTrue(flagged.none { it.isSelected })
        compose.setContent { SplitFlowTheme { Column {
            flagged.forEach { StatementTransactionRow(it, {}, {}, statementPeriod = period) }
        } } }
        val expected = listOf("Couldn't read its date: it would be added as today", "Dated after today",
            "Outside the statement's dates ($period)", "Much bigger than the rest of this statement", "Bank's 50% note")
        flagged.zip(expected).forEach { (item, text) ->
            compose.onNodeWithTag("statement_flag_${item.id}").assertTextEquals(text)
        }
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
    }
}
