package com.vaibhav.kharchapaani.ui.format

import androidx.compose.ui.text.AnnotatedString
import com.vaibhav.kharchapaani.domain.MAX_AMOUNT_RUPEES
import com.vaibhav.kharchapaani.domain.isSavableAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmountInputTest {

    @Test
    fun `what toDoubleOrNull took for a number is not an amount`() {
        for (text in listOf("NaN", "Infinity", "-Infinity", "1e999", "1e3", "0x10", "12a", "1.2.3", "-50", ""))
            assertNull("\"$text\" read as an amount", Money.parseInput(text))
    }

    @Test
    fun `grouping commas, spaces and a rupee sign are ignored`() {
        assertEquals(1200.0, Money.parseInput("1,200")!!, 0.0)
        assertEquals(100000.5, Money.parseInput("₹ 1,00,000.50")!!, 0.0)
        assertEquals(0.5, Money.parseInput(".5")!!, 0.0)
        assertEquals(12.0, Money.parseInput("12.")!!, 0.0)
    }

    @Test
    fun `no more than two decimals or what the server stores`() {
        assertNull(Money.parseInput("10.005"))
        assertNull("a pasted 19-digit number", Money.parseInput("1234567890123456789"))
        assertEquals(MAX_AMOUNT_RUPEES, Money.parseInput("9999999999.99")!!, 0.0)
    }

    @Test
    fun `the amount field refuses a bad keystroke and keeps a good one`() {
        assertEquals("1200", Money.acceptInput("1,200"))
        assertEquals("12.", Money.acceptInput("12."))
        assertEquals("", Money.acceptInput(""))
        assertNull(Money.acceptInput("12.345"))
        assertNull(Money.acceptInput("1e999"))
        assertNull(Money.acceptInput("NaN"))
        assertNull(Money.acceptInput("12345678901"))
    }

    @Test
    fun `grouping keeps partial amounts and fractional text as typed`() {
        val cases = mapOf(
            "" to "",
            "." to ".",
            ".05" to ".05",
            "999" to "999",
            "1000." to "1,000.",
            "240000.50" to "2,40,000.50",
            "9999999999.99" to "9,99,99,99,999.99",
        )
        for ((input, display) in cases) {
            val transformed = AmountInputGrouping.filter(AnnotatedString(input))
            assertEquals(display, transformed.text.text)
            val offsets = transformed.offsetMapping
            for (offset in 0..input.length) {
                assertEquals(offset, offsets.transformedToOriginal(offsets.originalToTransformed(offset)))
            }
        }
    }

    @Test
    fun `both sides of each grouping comma map to the same digit boundary`() {
        val offsets = AmountInputGrouping.filter(AnnotatedString("240000")).offsetMapping
        assertEquals(1, offsets.transformedToOriginal(1))
        assertEquals(1, offsets.transformedToOriginal(2))
        assertEquals(3, offsets.transformedToOriginal(4))
        assertEquals(3, offsets.transformedToOriginal(5))
    }

    @Test
    fun `only a positive amount the server can hold is savable`() {
        assertTrue(isSavableAmount(0.01))
        assertTrue(isSavableAmount(MAX_AMOUNT_RUPEES))
        assertFalse(isSavableAmount(0.0))
        assertFalse(isSavableAmount(-1.0))
        assertFalse(isSavableAmount(Double.NaN))
        assertFalse(isSavableAmount(Double.POSITIVE_INFINITY))
        assertFalse(isSavableAmount(MAX_AMOUNT_RUPEES + 1))
    }
}
