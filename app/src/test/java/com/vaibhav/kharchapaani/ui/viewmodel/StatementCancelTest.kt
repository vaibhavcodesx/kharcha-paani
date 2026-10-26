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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Back on "Reading your statement" did nothing for the 3–6 minutes a long statement takes (REL H2):
 * the dialog's dismiss cleared the import, not the reading, so the spinner stayed up.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StatementCancelTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF"
        .toByteArray(Charsets.US_ASCII)
    private val gemini = CountDownLatch(1)
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    /** A Gemini that answers with one statement row, but only once [gemini] is released. */
    private val slowGemini = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            gemini.await(10, TimeUnit.SECONDS)
            val row = """{"date":"2026-10-01","merchant":"Swiggy","amount":250,"type":"expense","category":"Food","paymentMethod":"UPI"}"""
            val text = org.json.JSONObject.quote("""{"transactions":[$row]}""")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":$text}]}}]}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        apiKey = { "test-key" },
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        // App settings outlive a test under Robolectric; every test starts before the notice.
        runBlocking { AppSettings(application).setAiNoticeAccepted(false) }
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId, geminiRepo = slowGemini)
    }

    @After
    fun tearDown() {
        gemini.countDown()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `dismissing the reading dialog stops the reading, and its answer never shows`() = runBlocking {
        viewModel.parseBankOrUpiStatement(pdf)
        // The one-time Gemini notice comes first (T1.10).
        viewModel.acceptAiNotice()
        assertTrue(viewModel.isParsingStatement.value)

        // What the dialog's Back and outside tap call.
        viewModel.dismissStatementImportDialog()

        assertFalse("the spinner is still up", viewModel.isParsingStatement.value)
        gemini.countDown()
        delay(500)
        assertNull("the cancelled statement still opened its import list", viewModel.statementParseResult.value)
        assertFalse(viewModel.isImportStatementOpen.value)
    }
}
