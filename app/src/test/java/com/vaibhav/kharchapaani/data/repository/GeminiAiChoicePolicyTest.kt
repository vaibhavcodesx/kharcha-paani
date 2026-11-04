package com.vaibhav.kharchapaani.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiAiChoicePolicyTest {
    private suspend fun cancelled(action: suspend () -> Unit) {
        try { action(); fail("AI policy did not cancel the request") }
        catch (expected: CancellationException) { /* Required policy outcome, not an AI failure. */ }
    }

    @Test fun `transport denies disabled statements and checks policy again before fallback`(): Unit = runBlocking {
        val allowed = AtomicBoolean(false)
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(503).message("Unavailable")
                .body("""{"error":{"status":"UNAVAILABLE"}}""".toResponseBody("application/json".toMediaType()))
                .build()
        }.build()
        val disabled = GeminiRepository(client = client,
            endpoint = { "https://example.invalid/models/$it:generateContent" },
            accessToken = { "test-token" }, configured = true, aiAllowed = { allowed.get() })
        val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray()
        cancelled { disabled.readStatementPdf(pdf, "invented.pdf") }
        cancelled { disabled.parseText("chai 40 cash") }
        assertEquals("Neither disabled path may reach HTTP", 0, requests.get())

        val models = mutableListOf<String>()
        allowed.set(true)
        val fallback = GeminiRepository(client = client,
            endpoint = { model ->
                // Primary 503 has already returned and passed its post-response policy check.
                // Flip only when the fallback URL is built, proving that boundary rechecks policy.
                if (models.isNotEmpty()) allowed.set(false)
                models += model
                "https://example.invalid/models/$model:generateContent"
            }, accessToken = { "test-token" }, configured = true, aiAllowed = { allowed.get() })
        cancelled { fallback.parseText("chai 40 cash") }
        assertEquals("Exercise the actual fallback branch", 2, models.size)
        assertNotEquals(models[0], models[1])
        assertEquals("Only the primary request may leave; fallback sends zero requests", 1, requests.get())
    }
}
