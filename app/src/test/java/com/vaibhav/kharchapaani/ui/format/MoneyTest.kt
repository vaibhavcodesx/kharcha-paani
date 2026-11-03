package com.vaibhav.kharchapaani.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MoneyTest {

    @Test
    fun `paisa are shown when there are any, never rounded away`() {
        assertEquals("₹34.50", Money.format(34.5))
        assertEquals("₹35", Money.format(35.0))
        assertEquals("₹0.05", Money.format(0.05))
        assertEquals("₹69", Money.format(69.0))
    }

    @Test
    fun `amounts use Indian digit grouping`() {
        assertEquals("₹999", Money.format(999.0))
        assertEquals("₹1,000", Money.format(1000.0))
        assertEquals("₹1,00,000", Money.format(100000.0))
        assertEquals("₹12,34,567.89", Money.format(1234567.89))
        assertEquals("₹1,00,00,000", Money.format(10_000_000.0))
    }

    @Test
    fun `signs are explicit and zero has none`() {
        assertEquals("-₹415.50", Money.format(-415.5))
        assertEquals("+₹84.50", Money.format(84.5, showPlus = true))
        assertEquals("₹0", Money.format(0.0, showPlus = true))
        assertEquals("₹0", Money.format(-0.004))
    }

    @Test
    fun `floating point noise rounds to the nearest paisa`() {
        assertEquals("₹33.33", Money.format(100.0 / 3))
        assertEquals("₹0.30", Money.format(0.1 + 0.2))
    }

    @Test
    fun `prefilled amounts show paisa to two places and whole rupees bare`() {
        assertEquals("1250.50", Money.editable(1250.5))
        assertEquals("1250", Money.editable(1250.0))
        assertEquals("33.33", Money.editable(100.0 / 3))
        assertEquals("0.05", Money.editable(0.05))
    }

    @Test
    fun `an amount that isn't a number draws as zero instead of crashing the screen`() {
        assertEquals("₹0", Money.format(Double.POSITIVE_INFINITY))
        assertEquals("₹0", Money.format(Double.NaN))
        assertEquals("₹0", Money.format(Double.NEGATIVE_INFINITY, showPlus = true))
        assertEquals("0", Money.digits(Double.NaN))
    }

    @Test
    fun `an amount too big for a Long is still drawn`() {
        assertEquals("₹10,00,00,00,00,00,00,00,00,000", Money.format(1e20))
    }

    @Test
    fun `the device locale doesn't change the decimal separator`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("₹1,234.50", Money.format(1234.5))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a compact figure is never cut and never says more than was spent`() {
        // The Insights day strip clipped ₹1,27,366 to "1,27,36" (UI audit U1).
        assertEquals("1.27L", Money.compactDigits(1_27_366.17))
        assertEquals("1.99L", Money.compactDigits(1_99_999.0))
        assertEquals("2L", Money.compactDigits(2_00_000.0))
        assertEquals("1.5Cr", Money.compactDigits(1_50_00_000.0))
        // Whole rupees from ₹1,000, so a day like ₹1,066.67 fits the cell too.
        assertEquals("1,066", Money.compactDigits(1_066.67))
        assertEquals("99,999", Money.compactDigits(99_999.0))
        assertEquals("99,999", Money.compactDigits(99_999.6))
        // Small amounts stay exact.
        assertEquals("40", Money.compactDigits(40.0))
        assertEquals("999.50", Money.compactDigits(999.5))
        assertEquals("0", Money.compactDigits(Double.NaN))
        // Short enough for the strip's fixed cell: at most six characters.
        listOf(40.0, 999.5, 1_066.67, 99_999.0, 1_27_366.0, 9_99_99_999.0).forEach {
            assertEquals(true, Money.compactDigits(it).length <= 6)
        }
    }
}
