package com.vaibhav.kharchapaani.data.repository

import android.graphics.Bitmap
import com.vaibhav.kharchapaani.domain.AiFailure
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.UnknownHostException

/**
 * Reading a bill's lines for a split by items: what the model answers becomes lines someone can
 * pick, junk lines are left out rather than guessed at, and a failure says which kind it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GeminiBillItemsTest {

    private val bill: Bitmap = Bitmap.createBitmap(40, 60, Bitmap.Config.ARGB_8888)
    private var sent: Request? = null

    private fun repo(respond: (Interceptor.Chain) -> Response) = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            sent = chain.request()
            respond(chain)
        }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = true,
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

    private fun answer(text: String) =
        """{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":${JSONObject.quote(text)}}]}}]}"""

    private fun bodyOf(request: Request): String = Buffer().also { request.body!!.writeTo(it) }.readUtf8()

    @Test
    fun `a bill's lines come back as items with their quantity and line total`() = runTest {
        val result = repo(respond(200, answer(
            """{"items":[{"name":"Paneer Tikka","quantity":1,"amount":420},{"name":" Butter Naan ","quantity":3,"amount":"180.00"}],"total":661.5}""",
        ))).readBillItems(bill)

        val read = result as BillItemsReadResult.Read
        assertEquals(listOf(BillItemLine("Paneer Tikka", 1, 420.0), BillItemLine("Butter Naan", 3, 180.0)), read.items)
        assertEquals(661.5, read.total!!, 0.0)
    }

    @Test
    fun `it is a bill read, sent with the picture`() = runTest {
        repo(respond(200, answer("""{"items":[],"total":0}"""))).readBillItems(bill)

        val request = sent!!
        assertEquals("bill", request.header(GeminiRepository.FEATURE_HEADER))
        assertEquals("Bearer test-token", request.header("Authorization"))
        val parts = JSONObject(bodyOf(request)).getJSONArray("contents").getJSONObject(0).getJSONArray("parts")
        assertTrue("the prompt asks for items, and to leave tax out of them", parts.getJSONObject(0).getString("text").contains("service charge"))
        assertEquals("image/jpeg", parts.getJSONObject(1).getJSONObject("inlineData").getString("mimeType"))
    }

    @Test
    fun `lines nobody could pick are left out, and odd quantities are made sensible`() = runTest {
        val result = repo(respond(200, answer(
            """{"items":[
                {"name":"","quantity":1,"amount":50},
                {"name":"Free papad","quantity":1,"amount":0},
                {"name":"Discount","quantity":1,"amount":-40},
                {"name":"Lime soda","quantity":0,"amount":120},
                {"name":"Thali","amount":250},
                {"name":"Banquet","quantity":500,"amount":90000}
            ],"total":"not shown"}""",
        ))).readBillItems(bill)

        val read = result as BillItemsReadResult.Read
        assertEquals(
            listOf(BillItemLine("Lime soda", 1, 120.0), BillItemLine("Thali", 1, 250.0), BillItemLine("Banquet", 99, 90_000.0)),
            read.items,
        )
        assertNull("a bill with no readable total has none, rather than a guess", read.total)
    }

    @Test
    fun `a bill with nothing itemised reads as no items, not as a failure`() = runTest {
        val result = repo(respond(200, answer("""{"total":250}"""))).readBillItems(bill)

        val read = result as BillItemsReadResult.Read
        assertTrue(read.items.isEmpty())
        assertEquals(250.0, read.total!!, 0.0)
    }

    @Test
    fun `each failure is named for what it is`() = runTest {
        fun reason(result: BillItemsReadResult) = (result as BillItemsReadResult.Failed).reason

        assertEquals(AiFailure.Offline, reason(repo { throw UnknownHostException("no network") }.readBillItems(bill)))
        assertEquals(AiFailure.Unreadable, reason(repo(respond(200, answer("that's a nice bill"))).readBillItems(bill)))
        assertEquals(AiFailure.Blocked, reason(repo(respond(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")).readBillItems(bill)))
        assertEquals(
            AiFailure.NoKey,
            reason(GeminiRepository(configured = false).readBillItems(bill)),
        )
    }
}
