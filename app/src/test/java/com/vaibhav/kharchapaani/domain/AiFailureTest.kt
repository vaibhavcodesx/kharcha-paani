package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFailureTest {

    @Test
    fun `http failures are named by what they are`() {
        assertEquals(AiFailure.RateLimited, AiFailure.fromHttp(429, """{"error":{"status":"RESOURCE_EXHAUSTED"}}"""))
        assertEquals(AiFailure.KeyRefused, AiFailure.fromHttp(400, """{"error":{"details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertEquals(AiFailure.KeyRefused, AiFailure.fromHttp(403, """{"error":{"status":"PERMISSION_DENIED"}}"""))
        assertEquals(AiFailure.ServerError, AiFailure.fromHttp(503, """{"error":{"status":"UNAVAILABLE"}}"""))
        assertEquals(AiFailure.Unreadable, AiFailure.fromHttp(400, """{"error":{"status":"INVALID_ARGUMENT"}}"""))
    }

    @Test
    fun `a safety stop is a block, a normal stop is not`() {
        assertTrue(AiFailure.isBlockReason("SAFETY"))
        assertFalse(AiFailure.isBlockReason("STOP"))
        assertFalse(AiFailure.isBlockReason("MAX_TOKENS"))
    }

    @Test
    fun `every reason has its own words, and offline never tells you to retake the photo`() {
        val asks = AiFailure.entries.map { it.askMessage() }
        assertEquals(asks.size, asks.toSet().size)
        assertTrue(AiFailure.entries.all { it.quickAddNotice().isNotBlank() && it.receiptNotice().isNotBlank() })
        assertFalse("clearer photo" in AiFailure.Offline.receiptNotice())
        assertNotEquals(AiFailure.Offline.quickAddNotice(), AiFailure.RateLimited.quickAddNotice())
    }
}
