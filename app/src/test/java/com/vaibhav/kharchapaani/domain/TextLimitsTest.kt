package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The app never sends text the server refuses (T2.2, migration 20261106000100_text_limits). */
class TextLimitsTest {
    @Test fun `limits match the server's checks`() {
        assertEquals(120, TextLimits.MERCHANT)
        assertEquals(1000, TextLimits.NOTE)
        assertEquals(60, TextLimits.CATEGORY)
        assertEquals(60, TextLimits.PAYMENT_METHOD)
    }

    @Test fun `text at the limit is kept whole, longer text is cut to it`() {
        assertEquals("Swiggy", TextLimits.merchant("Swiggy"))
        assertEquals(120, TextLimits.merchant("m".repeat(120)).length)
        assertEquals(120, TextLimits.merchant("m".repeat(500)).length)
        assertEquals(1000, TextLimits.note("n".repeat(5000)).length)
        assertEquals(60, TextLimits.category("c".repeat(61)).length)
        assertEquals(60, TextLimits.paymentMethod("p".repeat(99)).length)
    }
}
