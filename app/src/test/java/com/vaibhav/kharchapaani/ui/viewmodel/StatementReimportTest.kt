package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.ui.components.StatementImportContent
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** Real parser response -> Room comparison -> review UI -> real import -> second review. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h1200dp")
class StatementReimportTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray()
    private var statementJson = """{"transactions":[{"date":"2026-10-01","merchant":"Swiggy","amount":250,"type":"expense","category":"Food","paymentMethod":"UPI"},{"date":"2026-10-02","merchant":"Zomato","amount":400,"type":"expense","category":"Food","paymentMethod":"UPI"}]}"""
    private val gemini = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val rows = statementJson
            val quoted = org.json.JSONObject.quote(rows)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":$quoted}]}}]}""".toResponseBody("application/json".toMediaType())).build()
        }.build(),
        endpoint = { "https://example.invalid/statement" }, accessToken = { "test-token" }, configured = true,
    )

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        AppSettings(app).setAiNoticeAccepted(false)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555", geminiRepo = gemini)
        store.put("main", vm)
    }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }

    private suspend fun read() {
        vm.dismissStatementImportDialog()
        vm.parseBankOrUpiStatement(pdf)
        vm.acceptAiNotice()
        until { vm.statementParseResult.value != null }
    }

    @After fun tearDown() = runBlocking {
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        until { job.isCompleted }
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `a matching ledger row is unticked and labelled while a new row remains selected`() = runBlocking {
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 250.0,
            category = "Food", merchant = " swiggy ", paymentMethod = "Cash",
            date = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        read()
        val result = vm.statementParseResult.value!!
        val old = result.allItems.single { it.merchant == "Swiggy" }
        assertFalse("the existing entry is still ticked", old.isSelected)
        assertTrue(old.flag.orEmpty().contains("Already in your ledger"))
        assertTrue(result.allItems.single { it.merchant == "Zomato" }.isSelected)
        compose.setContent { SplitFlowTheme { StatementImportContent(result, vm::confirmImportStatementTransactions) } }
        compose.onNodeWithText("Already in your ledger").assertExists()
        compose.onNodeWithContentDescription("Import Swiggy").assertIsOff()
        compose.onNodeWithContentDescription("Import Zomato").assertIsOn()
        assertEquals(1, db.transactionDao().getAllTransactionsDirect().size)
    }

    @Test fun `one saved payment matches only one identical row with a one day date tolerance`() = runBlocking {
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 250.0,
            category = "Food", merchant = "Swiggy", paymentMethod = "UPI",
            date = LocalDate.of(2026, 9, 30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()))
        statementJson = """{"transactions":[{"date":"2026-10-01","merchant":"Swiggy","amount":250,"type":"expense"},{"date":"2026-10-01","merchant":"Swiggy","amount":250,"type":"expense"},{"date":"2026-10-03","merchant":"Swiggy","amount":250,"type":"expense"}]}"""
        read()
        val items = vm.statementParseResult.value!!.allItems
        assertEquals(3, items.size)
        assertEquals(1, items.count { !it.isSelected })
        assertEquals(2, items.count { it.isSelected })
    }

    @Test fun `a different type amount or far away date does not untick a new purchase`() = runBlocking {
        val day = LocalDate.of(2026, 10, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        for ((type, amount, date) in listOf(Triple("income", 250.0, day), Triple("expense", 251.0, day), Triple("expense", 250.0, day - 4 * 86400000L))) {
            db.transactionDao().insertTransaction(TransactionEntity(type = type, amount = amount,
                category = "Food", merchant = "Swiggy", paymentMethod = "UPI", date = date))
        }
        read()
        assertTrue(vm.statementParseResult.value!!.allItems.all { it.isSelected })
    }

    @Test fun `importing and reading the same statement again leaves both saved rows unticked`() = runBlocking {
        read()
        val first = vm.statementParseResult.value!!.allItems
        assertTrue(first.all { it.isSelected })
        vm.confirmImportStatementTransactions(first.filter { it.isSelected })
        until { db.transactionDao().getAllTransactionsDirect().size == 2 }
        until { vm.statementParseResult.value == null }
        read()
        val second = vm.statementParseResult.value!!.allItems
        assertEquals(2, second.size)
        assertTrue("a repeat import still selects saved entries", second.none { it.isSelected })
        assertEquals(2, db.transactionDao().getAllTransactionsDirect().size)
    }
}
