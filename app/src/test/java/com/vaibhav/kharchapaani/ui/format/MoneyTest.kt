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
    fun `the device locale doesn't change the decimal separator`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("₹1,234.50", Money.format(1234.5))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
