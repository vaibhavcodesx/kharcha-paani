package com.vaibhav.kharchapaani.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DATA-01: a throttled or timed-out write must stay PENDING, not be marked rejected forever. */
class SyncRetryPolicyTest {
    @Test fun `408 and 429 are retried`() {
        assertTrue(isRetryableStatus(408))
        assertTrue(isRetryableStatus(429))
    }

    @Test fun `expired session and server errors are retried`() {
        assertTrue(isRetryableStatus(401))
        assertTrue(isRetryableStatus(500))
        assertTrue(isRetryableStatus(503))
    }

    @Test fun `RLS and validation refusals are final`() {
        assertFalse(isRetryableStatus(400))
        assertFalse(isRetryableStatus(403))
        assertFalse(isRetryableStatus(404))
        assertFalse(isRetryableStatus(409))
    }

    @Test fun `a 403 on personal rows is retried only when asked`() {
        assertTrue(isRetryableStatus(403, retryForbidden = true))
        assertFalse(isRetryableStatus(403, retryForbidden = false))
    }
}
