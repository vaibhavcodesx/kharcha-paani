package com.vaibhav.kharchapaani.data.repository

import com.vaibhav.kharchapaani.domain.AiFailure
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException
import java.time.LocalDateTime
import java.time.ZoneId

/** A spoken line sent to Gemini as audio, through the same proxy and feature as a typed one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiVoiceTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 9, 24, 21, 30).atZone(zone).toInstant().toEpochMilli()
    private val wav = ByteArray(64) { it.toByte() }

    private fun repo(respond: (Interceptor.Chain) -> Response, configured: Boolean = true) = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain -> respond(chain) }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = configured,
    )

    private fun ok(chain: Interceptor.Chain, text: String) = Response.Builder()
        .request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":${JSONObject.quote(text)}}]}}]}""".toResponseBody("application/json".toMediaType()))
        .build()

    @Test
    fun `the audio goes as inline WAV data with the quick add feature, and the entry comes back in one step`() = runTest {
        var body: JSONObject? = null
        var feature: String? = null
        var model: String? = null
        val outcome = repo({ chain ->
            feature = chain.request().header(GeminiRepository.FEATURE_HEADER)
            model = chain.request().url.encodedPath.substringAfter("models/").substringBefore(":")
            body = JSONObject(okio.Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
            ok(chain, """{"type":"expense","amount":1200,"merchant":"Dinner","category":"Food","paymentMethod":"GPay","date":"","splitWith":["Rohan"],"transcript":"dinner twelve hundred with Rohan","note":""}""")
        }).parseAudio(wav, knownFriends = listOf("Rohan"), now = now, zone = zone)

        assertEquals("quick_add", feature)
        // Voice stays on Gemini: Groq only reads typed lines (owner decision 2026-11-06).
        assertEquals("gemini-3.1-flash-lite", model)
        val parts = body!!.getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        val inline = (0 until parts.length()).map(parts::getJSONObject).single { it.has("inlineData") }.getJSONObject("inlineData")
        assertEquals("audio/wav", inline.getString("mimeType"))
        assertEquals(android.util.Base64.encodeToString(wav, android.util.Base64.NO_WRAP), inline.getString("data"))
        assertTrue("the prompt says it is audio", parts.getJSONObject(0).getString("text").contains("audio", ignoreCase = true))

        assertNull(outcome.failure)
        assertEquals(1200.0, outcome.result.amount, 0.001)
        assertEquals(listOf("Rohan"), outcome.result.splitWithPersonNames)
        // What was heard is the note, so the person can see how it was understood.
        assertEquals("dinner twelve hundred with Rohan", outcome.result.note)
    }

    @Test
    fun `offline comes back named, with nothing made up`() = runTest {
        val outcome = repo({ throw UnknownHostException("no network") }).parseAudio(wav, now = now, zone = zone)

        assertEquals(AiFailure.Offline, outcome.failure)
        assertEquals(0.0, outcome.result.amount, 0.0)
    }

    @Test
    fun `an answer that isn't JSON is unreadable, not an entry`() = runTest {
        val outcome = repo({ chain -> ok(chain, "sorry, I could not hear that") }).parseAudio(wav, now = now, zone = zone)

        assertEquals(AiFailure.Unreadable, outcome.failure)
    }

    @Test
    fun `silence or noise with no amount and nothing heard is not an entry either`() = runTest {
        val outcome = repo({ chain -> ok(chain, """{"type":"expense","amount":0,"merchant":"","category":"Other","paymentMethod":"","date":"","splitWith":[],"transcript":"","note":""}""") })
            .parseAudio(wav, now = now, zone = zone)

        assertNotNull(outcome.failure)
        assertEquals(AiFailure.Unreadable, outcome.failure)
    }

    @Test
    fun `with no AI in the build there is nothing to send to`() = runTest {
        var called = false
        val outcome = repo({ chain -> called = true; ok(chain, "{}") }, configured = false).parseAudio(wav, now = now, zone = zone)

        assertEquals(AiFailure.NoKey, outcome.failure)
        assertEquals(false, called)
    }
}
