package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTest {

    private fun access(sdk: Int = 34, enabled: Boolean = false, granted: Boolean = false, asked: Int = 0, rationale: Boolean = false) =
        NotificationAccess.of(sdk, enabled, granted, asked, rationale)

    @Test
    fun `allowed whenever they'd show`() {
        assertEquals(NotificationAccess.Allowed, access(enabled = true, granted = true, asked = 2))
        assertEquals(NotificationAccess.Allowed, access(sdk = 28, enabled = true))
    }

    @Test
    fun `Android asks twice, then only settings can turn them on`() {
        assertEquals("never asked", NotificationAccess.CanAsk, access(asked = 0))
        assertEquals("refused once", NotificationAccess.CanAsk, access(asked = 1, rationale = true))
        assertEquals("refused twice", NotificationAccess.OpenSettings, access(asked = 2, rationale = false))
        assertEquals("dialog closed without a choice", NotificationAccess.CanAsk, access(asked = 1, rationale = false))
        assertEquals("asked twice, but Android would still show it", NotificationAccess.CanAsk, access(asked = 2, rationale = true))
    }

    @Test
    fun `off in settings is a settings job, on every version`() {
        assertEquals(NotificationAccess.OpenSettings, access(sdk = 28))
        assertEquals("granted, then turned off", NotificationAccess.OpenSettings, access(granted = true, asked = 1))
    }

    @Test
    fun `the app offers by itself at most twice, and only while asking can work`() {
        assertTrue(NotificationOffer.shouldOffer(NotificationAccess.CanAsk, offersShown = 0))
        assertTrue(NotificationOffer.shouldOffer(NotificationAccess.CanAsk, offersShown = 1))
        assertFalse(NotificationOffer.shouldOffer(NotificationAccess.CanAsk, offersShown = 2))
        assertFalse(NotificationOffer.shouldOffer(NotificationAccess.OpenSettings, offersShown = 0))
        assertFalse(NotificationOffer.shouldOffer(NotificationAccess.Allowed, offersShown = 0))
    }

    @Test
    fun `quiet hours run past midnight`() {
        val quiet = QuietHours()
        assertTrue("23:00", quiet.isQuietAt(23 * 60))
        assertTrue("02:00", quiet.isQuietAt(2 * 60))
        assertTrue("22:30 starts it", quiet.isQuietAt(22 * 60 + 30))
        assertFalse("08:00 ends it", quiet.isQuietAt(8 * 60))
        assertFalse("13:00", quiet.isQuietAt(13 * 60))
        assertFalse("off", quiet.copy(enabled = false).isQuietAt(23 * 60))
        assertTrue("a daytime window", QuietHours(startMinute = 13 * 60, endMinute = 14 * 60).isQuietAt(13 * 60 + 30))
    }
}
