package com.vaibhav.kharchapaani.data.repository

import android.graphics.Bitmap
import com.vaibhav.kharchapaani.domain.AiAskResult
import com.vaibhav.kharchapaani.domain.AiFailure
import com.vaibhav.kharchapaani.domain.AiPrompt
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Every AI path, and every way it can fail, against an interceptor that plays the server. The point
 * is that each failure comes back named — offline, out of quota, a refused key, a block — rather than
 * as the one vague "couldn't" that used to cover all of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiAiPathsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 9, 24, 21, 30).atZone(zone).toInstant().toEpochMilli()

    private fun repo(respond: (Interceptor.Chain) -> Response) = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain -> respond(chain) }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        apiKey = { "test-key" },
    )

    private fun respond(code: Int, body: String): (Interceptor.Chain) -> Response = { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun answer(text: String, finishReason: String = "STOP") =
        """{"candidates":[{"finishReason":"$finishReason","content":{"parts":[{"text":${JSONObject.quote(text)}}]}}]}"""

    private fun throwing(e: IOException): (Interceptor.Chain) -> Response = { throw e }

    private suspend fun askWith(respond: (Interceptor.Chain) -> Response): AiAskResult =
        repo(respond).askForAnswer(AiPrompt.MonthInReview, "September's", "Total spent: ₹900")

    private fun reasonOf(result: AiAskResult) = (result as AiAskResult.Failed).reason

    // --- Asking

    @Test
    fun `an answer comes back tidied into rupees`() = runTest {
        val result = askWith(respond(200, answer("Food was Rs 900, most of it on 9 September.")))

        assertEquals("Food was ₹900, most of it on 9 September.", (result as AiAskResult.Answer).text)
    }

    @Test
    fun `each failure is named for what it is`() = runTest {
        assertEquals(AiFailure.Offline, reasonOf(askWith(throwing(UnknownHostException("no network")))))
        assertEquals(AiFailure.Timeout, reasonOf(askWith(throwing(SocketTimeoutException("read timed out")))))
        assertEquals(AiFailure.RateLimited, reasonOf(askWith(respond(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}"""))))
        assertEquals(AiFailure.KeyRefused, reasonOf(askWith(respond(403, """{"error":{"status":"PERMISSION_DENIED"}}"""))))
        assertEquals(AiFailure.ServerError, reasonOf(askWith(respond(500, """{"error":{"status":"INTERNAL"}}"""))))
        assertEquals(AiFailure.Blocked, reasonOf(askWith(respond(200, """{"promptFeedback":{"blockReason":"SAFETY"}}"""))))
        assertEquals(AiFailure.Blocked, reasonOf(askWith(respond(200, """{"candidates":[{"finishReason":"SAFETY","content":{"parts":[]}}]}"""))))
        assertEquals(AiFailure.Truncated, reasonOf(askWith(respond(200, """{"candidates":[{"finishReason":"MAX_TOKENS","content":{"parts":[]}}]}"""))))
        assertEquals(AiFailure.Unreadable, reasonOf(askWith(respond(200, "not json"))))
    }

    @Test
    fun `a build without a key says so and never calls out`() = runTest {
        var called = false
        val repo = GeminiRepository(
            client = OkHttpClient.Builder().addInterceptor { called = true; throw IOException() }.build(),
            apiKey = { "" },
        )

        assertEquals(AiFailure.NoKey, reasonOf(repo.askForAnswer(AiPrompt.MonthInReview, "September's", "")))
        assertFalse(called)
    }

    @Test
    fun `the old ask still returns null on failure, for existing callers`() = runTest {
        assertNull(repo(respond(429, "{}")).ask(AiPrompt.MonthInReview, "September's", ""))
    }

    // --- Typed and spoken lines

    @Test
    fun `a typed line reads amount, date and friends, and a string amount survives`() = runTest {
        val json = """{"type":"expense","amount":"1,299","merchant":"Barbeque Nation","category":"Food",
            "paymentMethod":"GPay","date":"2026-09-23","splitWith":["Rohan"],"note":"bbq 1,299 with rohan yesterday"}"""
        val outcome = repo(respond(200, answer(json))).parseText("bbq 1,299 with rohan yesterday", listOf("Rohan"), now = now, zone = zone)

        assertNull(outcome.failure)
        assertFalse(outcome.result.guessedOnPhone)
        assertEquals(1299.0, outcome.result.amount, 0.001)
        assertEquals(listOf("Rohan"), outcome.result.splitWithPersonNames)
        assertEquals(LocalDateTime.of(2026, 9, 23, 21, 30), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(outcome.result.date), zone))
    }

    @Test
    fun `JSON wrapped in a markdown fence still parses`() = runTest {
        val fenced = "```json\n{\"type\":\"expense\",\"amount\":80,\"merchant\":\"Chai\",\"category\":\"Food\",\"paymentMethod\":\"Cash\",\"date\":\"\",\"splitWith\":[],\"note\":\"chai 80\"}\n```"
        val outcome = repo(respond(200, answer(fenced))).parseText("chai 80", now = now, zone = zone)

        assertNull(outcome.failure)
        assertEquals(80.0, outcome.result.amount, 0.001)
        assertEquals(now, outcome.result.date)
    }

    @Test
    fun `offline, the phone guesses and says why`() = runTest {
        val outcome = repo(throwing(UnknownHostException("no network"))).parseText("dinner 1,299 gpay", now = now, zone = zone)

        assertEquals(AiFailure.Offline, outcome.failure)
        assertTrue(outcome.result.guessedOnPhone)
        assertEquals(1299.0, outcome.result.amount, 0.001)
    }

    @Test
    fun `a model that misses the amount doesn't erase the one the phone found`() = runTest {
        val json = """{"type":"expense","amount":0,"merchant":"Auto","category":"Transport","paymentMethod":"Cash","date":"","splitWith":[],"note":"auto 60"}"""
        val outcome = repo(respond(200, answer(json))).parseText("auto 60", now = now, zone = zone)

        assertEquals(60.0, outcome.result.amount, 0.001)
    }

    // --- Bills and screenshots

    private val bitmap: Bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

    @Test
    fun `a bill reads its own date and total`() = runTest {
        val json = """{"type":"expense","amount":"₹1,450.00","merchant":"Starbucks","category":"Food","paymentMethod":"Credit Card","date":"2026-09-20T13:05","note":"Order 4412"}"""
        val result = repo(respond(200, answer(json))).readReceipt(bitmap, now = now, zone = zone)

        val read = result as ReceiptReadResult.Read
        assertFalse(read.missingAmount)
        assertEquals(1450.0, read.entry.amount, 0.001)
        assertEquals(LocalDateTime.of(2026, 9, 20, 13, 5), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(read.entry.date), zone))
    }

    @Test
    fun `a bill with no total found says the amount is missing, not that the photo was bad`() = runTest {
        val json = """{"type":"expense","amount":0,"merchant":"Cafe","category":"Food","paymentMethod":"","date":"","note":""}"""
        val result = repo(respond(200, answer(json))).readReceipt(bitmap, now = now, zone = zone)

        assertTrue((result as ReceiptReadResult.Read).missingAmount)
    }

    @Test
    fun `a bill that couldn't be sent says why`() = runTest {
        assertEquals(AiFailure.Offline, (repo(throwing(UnknownHostException("x"))).readReceipt(bitmap) as ReceiptReadResult.Failed).reason)
        assertEquals(AiFailure.RateLimited, (repo(respond(429, "{}")).readReceipt(bitmap) as ReceiptReadResult.Failed).reason)
    }

    @Test
    fun `the old receipt call still marks a failed read for existing callers`() = runTest {
        assertTrue(repo(respond(429, "{}")).parseReceiptOrScreenshot(bitmap).readFailed)
    }

    @Test
    fun `calls get budgets matched to what they do`() {
        assertTrue(GeminiRepository.TEXT_TIMEOUT_SECONDS < GeminiRepository.ASK_TIMEOUT_SECONDS)
        assertTrue(GeminiRepository.ASK_TIMEOUT_SECONDS < GeminiRepository.STATEMENT_TIMEOUT_SECONDS)
    }
}
