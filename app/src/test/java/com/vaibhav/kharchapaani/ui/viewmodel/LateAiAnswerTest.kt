package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * REL M5's bug, written only against APIs that existed before the fix (parseInputText,
 * openEditTransaction, dismissConfirmSheet), so the same test runs on pre-fix main and fails there:
 * a quick-add reading that arrives after the person moved on rewrote their edit into a new entry,
 * and reopened a sheet they had closed. Real Room database, real MainViewModel, and a fake network
 * that holds Gemini's answer until the test lets it go.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LateAiAnswerTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val screen = CoroutineScope(Dispatchers.Unconfined + Job())
    private var mainInstalled = false

    private val aiMayAnswer = CountDownLatch(1)
    private val asked = AtomicInteger()
    private val answered = AtomicInteger()
    private val reading = """{"type":"expense","amount":2400,"merchant":"Dinner at Toit","category":"Food","paymentMethod":"UPI","date":"","splitWith":["Rohan"],"note":"dinner"}"""

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        asked.incrementAndGet()
        aiMayAnswer.await(10, TimeUnit.SECONDS)
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":${org.json.JSONObject.quote(reading)}}]}}]}""".toResponseBody("application/json".toMediaType()))
            .build()
            .also { answered.incrementAndGet() }
    }.build()

    private val gemini = GeminiRepository(
        client = client,
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mainInstalled = true
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        runBlocking { AppSettings(application).setAiNoticeAccepted(false) }
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF")) }
        viewModel = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555", geminiRepo = gemini)
        store.put("main", viewModel)
        screen.launch { viewModel.people.collect {} }
        screen.launch { viewModel.groups.collect {} }
        screen.launch { viewModel.rawGroups.collect {} }
        runBlocking { until { viewModel.people.value.size == 1 } }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        // Nothing left blocked on the fake network, then the ViewModel's own jobs stopped and
        // finished, before Room closes and Main is reset (#194's lifecycle).
        aiMayAnswer.countDown()
        screen.cancel()
        if (::viewModel.isInitialized) {
            val job = viewModel.viewModelScope.coroutineContext[Job]!!
            store.clear()
            withTimeout(10_000) { job.join() }
        }
        if (::db.isInitialized) db.close()
        if (mainInstalled) Dispatchers.resetMain()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(10) }

    /**
     * Lets the held reading through and waits until it has fully come back: the fake network has
     * answered and OkHttp has finished the call, and the pre-fix spinner (isParsing) has come down.
     * A late answer is applied on the call's own thread right after it returns, so the assertions
     * then poll briefly for the wrong state rather than sleeping a fixed time.
     */
    private suspend fun aiAnswersAndFinishes() {
        aiMayAnswer.countDown()
        until { answered.get() == 1 && client.dispatcher.runningCallsCount() == 0 && !viewModel.isParsing.value }
    }

    /** Fails as soon as [wrong] is seen within [windowMs]; a late answer would show up at once. */
    private suspend fun neverSees(message: String, windowMs: Long = 250, wrong: () -> Boolean) {
        val steps = (windowMs / 10).coerceAtLeast(1)
        repeat(steps.toInt()) {
            assertFalse(message, wrong())
            delay(10)
        }
    }

    @Test
    fun `an edit opened while the AI reads a line stays that edit`() = runBlocking {
        val chai = TransactionEntity(type = "expense", amount = 40.0, category = "Food", merchant = "Chai", paymentMethod = "Cash", date = System.currentTimeMillis())
        val chaiId = db.transactionDao().insertTransaction(chai)

        viewModel.parseInputText("dinner at toit 2400 with rohan")
        viewModel.acceptAiNotice()
        until { asked.get() == 1 }
        viewModel.openEditTransaction(FullTransaction(chai.copy(id = chaiId)))

        aiAnswersAndFinishes()

        neverSees("the late reading turned the edit into a new entry") { viewModel.editingTransactionId.value != chaiId }
        assertEquals("40", viewModel.amount.value)
        assertEquals("Chai", viewModel.merchant.value)
    }

    @Test
    fun `a sheet closed while the AI reads a line stays closed`() = runBlocking {
        viewModel.parseInputText("dinner at toit 2400 with rohan")
        viewModel.acceptAiNotice()
        until { asked.get() == 1 }
        viewModel.dismissConfirmSheet()

        aiAnswersAndFinishes()

        neverSees("the late reading reopened a closed sheet") { viewModel.isConfirmSheetOpen.value }
    }
}
