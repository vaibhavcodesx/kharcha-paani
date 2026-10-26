package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A UPI handle now arrives from *someone else's phone* — a groupmate publishes theirs so you can
 * see where to pay them. That string is interpolated into a `upi://pay` deep link, which a payment
 * app then acts on, so it is the one piece of synced data that could move money if it were wrong.
 *
 * The server has a matching check constraint (`profiles_upi_id_format`). Neither layer is relied on
 * alone: these are the client's half.
 */
class UpiLinkSafetyTest {

    private val injections = listOf(
        "evil@bank&am=99999" to "a second parameter",
        "evil@bank&pa=attacker@bank" to "a second payee",
        "evil@bank?am=1" to "a new query string",
        "evil@bank#am=1" to "a fragment",
        "evil@bank am=1" to "a space",
        "evil@bank\nam=1" to "a newline",
        "evil@bank%26am=1" to "a pre-encoded ampersand",
        "@bank" to "no handle at all",
        "a@" to "no bank at all",
        "a@1bank" to "a bank not starting with a letter",
        "" to "nothing",
        "   " to "whitespace",
    )

    @Test
    fun `nothing that could carry a second parameter is a valid UPI ID`() {
        for ((candidate, why) in injections) {
            assertNull("accepted $why: $candidate", UpiLink.cleanId(candidate))
        }
    }

    @Test
    fun `no link is built from a handle that isn't one`() {
        for ((candidate, why) in injections) {
            assertNull("built a link from $why: $candidate", UpiLink.buildOrNull(candidate, "Rohan", 500.0))
        }
    }

    @Test
    fun `a real handle still works`() {
        // A phone-number handle; the number is made up.
        assertEquals("9000000001@yespop", UpiLink.cleanId("9000000001@yespop"))
        assertEquals("rohan.mehta-1@okaxis", UpiLink.cleanId("  Rohan.Mehta-1@OKAXIS  "))
    }

    @Test
    fun `the amount in the link is the amount asked for, and appears once`() {
        val link = UpiLink.buildOrNull("rohan@okaxis", "Rohan Mehta", 2902.34)!!

        assertTrue(link.startsWith("upi://pay?"))
        assertEquals(1, Regex("(^|&)am=").findAll(link).count())
        assertTrue(link.contains("am=2902.34"))
        assertEquals(1, Regex("(^|\\?|&)pa=").findAll(link).count())
    }

    @Test
    fun `an open-amount link carries no amount at all`() {
        // The "Any amount" QR. If an amount leaked into it, someone paying a different sum would
        // silently pay this one instead.
        val link = UpiLink.buildOrNull("rohan@okaxis", "Rohan Mehta", amount = null)!!

        assertFalse(link.contains("am="))
    }

    @Test
    fun `a zero or negative amount never becomes an amount`() {
        assertFalse(UpiLink.buildOrNull("rohan@okaxis", "Rohan", 0.0)!!.contains("am="))
        assertFalse(UpiLink.buildOrNull("rohan@okaxis", "Rohan", -50.0)!!.contains("am="))
    }

    @Test
    fun `a payee name cannot break out of its parameter either`() {
        // The name is not format-checked, because it is a display name — so it has to be encoded.
        val link = UpiLink.buildOrNull("rohan@okaxis", "Rohan & Co?am=1", 100.0)!!

        assertFalse(link.contains("Rohan & Co"))
        assertTrue(link.contains("%26"))
        assertEquals(1, Regex("(^|&)am=").findAll(link).count())
    }

    @Test
    fun `a note cannot break out of its parameter either`() {
        val link = UpiLink.buildOrNull("rohan@okaxis", "Rohan", 100.0, note = "dinner&am=99999")!!

        assertEquals(1, Regex("(^|&)am=").findAll(link).count())
        assertTrue(link.contains("am=100.00"))
    }

    @Test
    fun `the client and the server agree on exactly one rule`() {
        // profiles_upi_id_format is '^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9.-]{1,64}$'.
        // A value one side accepts and the other rejects means a handle that saves and never
        // syncs, or syncs and never renders.
        val sixtyFour = "a".repeat(64)
        assertEquals("$sixtyFour@okaxis", UpiLink.cleanId("$sixtyFour@okaxis"))
        assertNull(UpiLink.cleanId("${"a".repeat(65)}@okaxis"))
    }
}
