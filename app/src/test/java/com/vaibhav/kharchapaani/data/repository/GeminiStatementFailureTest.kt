package com.vaibhav.kharchapaani.data.repository

import com.vaibhav.kharchapaani.domain.StatementChecks
import com.vaibhav.kharchapaani.domain.StatementPdf
import com.vaibhav.kharchapaani.domain.StatementReadFailure
import com.vaibhav.kharchapaani.domain.StatementReadResult
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException

/**
 * Statement import was reported as "does not scan or import anything". The wiring was fine; the
 * call failed and every cause came back as the same null. These pin each cause to its own answer,
 * without a network: the client's interceptor is the server.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiStatementFailureTest {

    private val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF"
        .toByteArray(Charsets.US_ASCII)

    private fun repo(respond: (Interceptor.Chain) -> Response) = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain -> respond(chain) }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = true,
    )

    private fun ok(body: String): (Interceptor.Chain) -> Response = { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun answer(text: String, finishReason: String = "STOP", thoughtFirst: Boolean = false): String {
        val parts = buildString {
            append("[")
            if (thoughtFirst) append("""{"text":"weighing the rows","thought":true},""")
            append("""{"text":${org.json.JSONObject.quote(text)}}""")
            append("]")
        }
        return """{"candidates":[{"finishReason":"$finishReason","content":{"parts":$parts}}]}"""
    }

    private val oneRow = """
        {"bankOrApp":"PhonePe UPI Statement","statementPeriod":"Aug 2024","transactions":[
          {"date":"2024-08-14T12:30:00","type":"expense","amount":250.0,"merchant":"Swiggy",
           "category":"Food","paymentMethod":"UPI","note":"UPI/4123541/swiggy@icici"}]}
    """.trimIndent()

    private fun failureOf(result: StatementReadResult): StatementReadFailure =
        (result as StatementReadResult.Failed).reason

    @Test
    fun `a statement that can't be read yields no result, never sample transactions`() = runTest {
        // An unreadable PDF used to come back as a generated sample statement (Zomato ₹450 and so on),
        // shown in the import sheet as if it were the person's own, one tap away from their ledger.
        val result = GeminiRepository().parseBankOrUpiStatementPdf(ByteArray(0), "statement.pdf")

        assertNull(result)
    }

    @Test
    fun `a timeout reads as a timeout, not as a broken statement`() = runTest {
        // The real bug: the 30s read timeout was shared with the one-line text parse, so this was
        // the only AI call that could not finish, and every failure looked identical.
        val result = repo { throw SocketTimeoutException("timeout") }.readStatementPdf(pdf)

        assertEquals(StatementReadFailure.Timeout, failureOf(result))
    }

    @Test
    fun `the statement call gets far longer than the thirty seconds the other calls get`() {
        assertTrue(GeminiRepository.STATEMENT_TIMEOUT_SECONDS >= 120L)
    }

    @Test
    fun `an API error carries its status and body into the log, not silence`() = runTest {
        val result = repo { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(429)
                .message("Too Many Requests")
                .body("""{"error":{"message":"Quota exceeded"}}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.readStatementPdf(pdf)

        val failure = result as StatementReadResult.Failed
        // Named for what it is: waiting fixes quota, and "try again in a minute" for a refused key
        // was advice that could never work.
        assertEquals(StatementReadFailure.RateLimited, failure.reason)
        assertTrue(failure.detail.orEmpty().contains("429"))
        assertTrue(failure.detail.orEmpty().contains("Quota exceeded"))
    }

    private fun httpError(code: Int, body: String): (okhttp3.Interceptor.Chain) -> Response = { chain ->
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Error")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    @Test
    fun `a refused key is named as one, and an unexplained 400 stays a rejection`() = runTest {
        val refused = repo(httpError(400, """{"error":{"status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}""")).readStatementPdf(pdf)
        val restricted = repo(httpError(403, """{"error":{"status":"PERMISSION_DENIED"}}""")).readStatementPdf(pdf)
        val other = repo(httpError(400, """{"error":{"status":"INVALID_ARGUMENT","message":"Unsupported file"}}""")).readStatementPdf(pdf)

        assertEquals(StatementReadFailure.KeyRefused, failureOf(refused))
        assertEquals(StatementReadFailure.KeyRefused, failureOf(restricted))
        assertEquals(StatementReadFailure.Rejected, failureOf(other))
    }

    @Test
    fun `a statement the model declines to read says it was blocked`() = runTest {
        val result = repo(ok("""{"promptFeedback":{"blockReason":"OTHER"}}""")).readStatementPdf(pdf)

        assertEquals(StatementReadFailure.Blocked, failureOf(result))
    }

    @Test
    fun `amounts written as strings are read, and a row with no money in it is left out`() = runTest {
        // optDouble turned "1,299.00" into 0, and that row was still listed, pre-ticked, at ₹0.
        val rows = """
            {"bankOrApp":"HDFC","statementPeriod":"Aug 2024","transactions":[
              {"date":"2024-08-14","type":"expense","amount":"1,299.00","merchant":"Croma","category":"Shopping","paymentMethod":"Debit Card","note":""},
              {"date":"2024-08-15","type":"expense","amount":0,"merchant":"Balance b/f","category":"Other","paymentMethod":"UPI","note":""}]}
        """.trimIndent()
        val result = repo(ok(answer("```json\n$rows\n```"))).readStatementPdf(pdf)

        val items = (result as StatementReadResult.Success).statement.allItems
        assertEquals(1, items.size)
        assertEquals(1299.0, items.single().amount, 0.001)
    }

    @Test
    fun `a crafted salary, an undated row and one outside the period arrive unticked, with reasons`() = runTest {
        // Every row used to arrive ticked: one tap on Import would have added a ₹5,00,000 "salary"
        // slipped into a PDF of small UPI payments (security audit, 2026-10-24).
        val rows = """
            {"bankOrApp":"PhonePe","statementPeriod":"Jul 2024 - Aug 2024","transactions":[
              {"date":"2024-08-14","type":"expense","amount":250,"merchant":"Swiggy","category":"Food","paymentMethod":"UPI","note":""},
              {"date":"2024-08-15","type":"expense","amount":120,"merchant":"Chai Point","category":"Food","paymentMethod":"UPI","note":""},
              {"date":"2024-07-02","type":"expense","amount":499,"merchant":"Jio","category":"Bills","paymentMethod":"UPI","note":""},
              {"date":"2024-08-20","type":"income","amount":500000,"merchant":"Salary","category":"Income (Salary)","paymentMethod":"NEFT","note":""},
              {"date":"sometime","type":"expense","amount":80,"merchant":"Auto","category":"Travel","paymentMethod":"UPI","note":""},
              {"date":"2023-01-05","type":"expense","amount":300,"merchant":"Old row","category":"Other","paymentMethod":"UPI","note":""}]}
        """.trimIndent()
        val result = repo(ok(answer(rows))).readStatementPdf(pdf)

        val items = (result as StatementReadResult.Success).statement.allItems.associateBy { it.merchant }
        assertEquals(listOf("Chai Point", "Jio", "Swiggy"), items.values.filter { it.isSelected }.map { it.merchant }.sorted())
        assertEquals(StatementChecks.LARGE, items.getValue("Salary").flag)
        assertEquals(StatementChecks.NO_DATE, items.getValue("Auto").flag)
        assertEquals(StatementChecks.outside("Jul 2024 - Aug 2024"), items.getValue("Old row").flag)
        assertTrue(items.values.filter { it.isSelected }.all { it.flag == null })
    }

    @Test
    fun `an answer cut off mid-array reads as truncated, not unreadable`() = runTest {
        val cutOff = """{"bankOrApp":"HDFC","transactions":[{"date":"2024-08-14","amount":25"""
        val result = repo(ok(answer(cutOff, finishReason = "MAX_TOKENS"))).readStatementPdf(pdf)

        assertEquals(StatementReadFailure.Truncated, failureOf(result))
    }

    @Test
    fun `a thinking model's thought part doesn't hide the answer behind it`() = runTest {
        // Reading parts[0].text only meant a leading thought part threw the whole statement away.
        val result = repo(ok(answer(oneRow, thoughtFirst = true))).readStatementPdf(pdf)

        val statement = (result as StatementReadResult.Success).statement
        assertEquals(1, statement.allItems.size)
        assertEquals("Swiggy", statement.allItems.first().merchant)
    }

    @Test
    fun `a document with no transactions in it says so`() = runTest {
        val result = repo(ok(answer("""{"bankOrApp":"HDFC","transactions":[]}"""))).readStatementPdf(pdf)

        assertEquals(StatementReadFailure.NothingFound, failureOf(result))
    }

    @Test
    fun `a password-protected statement is named as one before the round trip is spent`() = runTest {
        val locked = (String(pdf, Charsets.US_ASCII) + "\n<< /Encrypt 5 0 R >>").toByteArray(Charsets.US_ASCII)
        val result = repo { error("the network must not be touched") }.readStatementPdf(locked)

        assertEquals(StatementReadFailure.PasswordProtected, failureOf(result))
    }

    @Test
    fun `a file that isn't a PDF is named as one before the round trip is spent`() = runTest {
        val result = repo { error("the network must not be touched") }
            .readStatementPdf("just some text".toByteArray())

        assertEquals(StatementReadFailure.NotAPdf, failureOf(result))
    }

    @Test
    fun `a statement too big to send inline is named as one before the round trip is spent`() = runTest {
        val huge = ByteArray(StatementPdf.MAX_BYTES + 1)
        "%PDF-1.7".toByteArray(Charsets.US_ASCII).copyInto(huge)
        val result = repo { error("the network must not be touched") }.readStatementPdf(huge)

        assertEquals(StatementReadFailure.TooLarge, failureOf(result))
    }

    @Test
    fun `a build with no key says so instead of blaming the statement`() = runTest {
        val result = GeminiRepository(configured = false).readStatementPdf(pdf)

        assertEquals(StatementReadFailure.NoKey, failureOf(result))
    }

    @Test
    fun `a statement that reads cleanly comes back grouped`() = runTest {
        val result = repo(ok(answer(oneRow))).readStatementPdf(pdf)

        val statement = (result as StatementReadResult.Success).statement
        assertEquals("PhonePe UPI Statement", statement.bankOrApp)
        assertEquals(1, statement.totalTransactionsCount)
        assertEquals(250.0, statement.totalExpenseSum, 0.001)
        assertEquals(1, statement.monthGroups.size)
    }
}
