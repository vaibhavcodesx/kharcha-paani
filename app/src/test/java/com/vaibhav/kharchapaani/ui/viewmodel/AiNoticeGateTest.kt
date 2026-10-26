package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * Nothing reaches Gemini before the one-time notice is accepted (plan T1.10, decision D2), and
 * "Type it myself" still gets the entry made, on the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiNoticeGateTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private val calls = AtomicInteger()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    /** Counts requests and answers each with a ₹40 chai. */
    private val gemini = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            calls.incrementAndGet()
            val text = org.json.JSONObject.quote("""{"type":"expense","amount":40,"merchant":"Chai","category":"Food","paymentMethod":"Cash"}""")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":$text}]}}]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        // App settings outlive a test under Robolectric; every test starts before the notice.
        runBlocking { AppSettings(application).setAiNoticeAccepted(false) }
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId, geminiRepo = gemini)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(20) }

    @Test
    fun `a quick add waits for the notice, and Type it myself fills it in on the phone`() = runBlocking {
        viewModel.parseInputText("chai 40 cash")

        assertTrue(viewModel.isAiNoticeOpen.value)
        assertEquals("nothing goes to Gemini before the notice is answered", 0, calls.get())

        viewModel.declineAiNotice()

        assertFalse(viewModel.isAiNoticeOpen.value)
        assertTrue(viewModel.isConfirmSheetOpen.value)
        assertEquals("40", viewModel.amount.value)
        delay(300)
        assertEquals(0, calls.get())
    }

    @Test
    fun `once accepted, Gemini reads it, and the notice doesn't come back`() = runBlocking {
        viewModel.parseInputText("chai 40 cash")
        viewModel.acceptAiNotice()
        until { calls.get() == 1 && viewModel.isConfirmSheetOpen.value }
        viewModel.dismissConfirmSheet()

        // The acceptance is stored; give it a moment to land, as it would between two entries.
        delay(300)
        viewModel.parseInputText("auto 120")

        assertFalse(viewModel.isAiNoticeOpen.value)
        until { calls.get() == 2 }
    }
}
