package com.vaibhav.kharchapaani.ui.format

import com.vaibhav.kharchapaani.domain.MAX_AMOUNT_RUPEES
import com.vaibhav.kharchapaani.domain.roundPaisa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `parseInput correctly parses valid amounts, noise and Indian grouping`() {
        assertEquals(0.01, Money.parseInput("0.01")!!, 0.0)
        assertEquals(0.05, Money.parseInput("₹0.05")!!, 0.0)
        assertEquals(0.50, Money.parseInput("0.5")!!, 0.0)
        assertEquals(1.0, Money.parseInput("1")!!, 0.0)
        assertEquals(999.0, Money.parseInput("999")!!, 0.0)
        assertEquals(1000.0, Money.parseInput("1,000")!!, 0.0)
        assertEquals(100000.0, Money.parseInput("1,00,000")!!, 0.0)
        assertEquals(100000.50, Money.parseInput("₹ 1,00,000.50")!!, 0.0)
        assertEquals(1234567.89, Money.parseInput("  ₹12,34,567.89  ")!!, 0.0)
        assertEquals(150.0, Money.parseInput("₹₹150")!!, 0.0)
        assertEquals(1200.5, Money.parseInput("1,2,0,0.50")!!, 0.0)
    }

    @Test
    fun `parseInput rejects NaN, infinities, scientific notation and malformed inputs`() {
        assertNull(Money.parseInput("NaN"))
        assertNull(Money.parseInput("nan"))
        assertNull(Money.parseInput("Infinity"))
        assertNull(Money.parseInput("-Infinity"))
        assertNull(Money.parseInput("+Infinity"))
        assertNull(Money.parseInput("1e5"))
        assertNull(Money.parseInput("1e999"))
        assertNull(Money.parseInput("1E10"))
        assertNull(Money.parseInput("12.345")) // More than 2 decimal digits
        assertNull(Money.parseInput("0.001"))
        assertNull(Money.parseInput("1.2.3"))  // Multiple decimal points
        assertNull(Money.parseInput(".."))
        assertNull(Money.parseInput("."))
        assertNull(Money.parseInput(""))
        assertNull(Money.parseInput("   "))
        assertNull(Money.parseInput("-100"))   // Negative amounts
        assertNull(Money.parseInput("-0.50"))
        assertNull(Money.parseInput("abc"))
        assertNull(Money.parseInput("100 USD"))
        assertNull(Money.parseInput("$100"))
        assertNull(Money.parseInput("@100"))
    }

    @Test
    fun `parseInput respects overflow boundaries up to MAX_AMOUNT_RUPEES`() {
        assertEquals(MAX_AMOUNT_RUPEES, Money.parseInput("9999999999.99")!!, 0.0)
        assertEquals(MAX_AMOUNT_RUPEES, Money.parseInput("9,99,99,99,999.99")!!, 0.0)
        assertEquals(9999999999.0, Money.parseInput("9999999999")!!, 0.0)

        // Beyond MAX_AMOUNT_RUPEES
        assertNull(Money.parseInput("10000000000.00")) // 11 digits
        assertNull(Money.parseInput("10000000000"))
        assertNull(Money.parseInput("99999999999.99"))
        assertNull(Money.parseInput("100000000000"))
    }

    @Test
    fun `format and parseInput round trip seamlessly across amounts`() {
        val amounts = listOf(
            0.01, 0.02, 0.05, 0.10, 0.50, 0.99,
            1.00, 3.50, 9.99, 10.00, 33.33, 49.95, 99.99, 100.00,
            250.75, 999.99, 1000.00, 1234.56, 10000.00, 99999.99,
            100000.00, 1234567.89, 10000000.00, 9999999999.99
        )

        for (amt in amounts) {
            val expected = roundPaisa(amt)

            // 1. format(amt) -> parseInput
            val formatted = Money.format(amt)
            val parsedFormatted = Money.parseInput(formatted)
            assertNotNull("format($amt) produced $formatted which must be parseable", parsedFormatted)
            assertEquals("format round trip for $amt", expected, parsedFormatted!!, 0.0)

            // 2. digits(amt) -> parseInput
            val digits = Money.digits(amt)
            val parsedDigits = Money.parseInput(digits)
            assertNotNull("digits($amt) produced $digits which must be parseable", parsedDigits)
            assertEquals("digits round trip for $amt", expected, parsedDigits!!, 0.0)

            // 3. editable(amt) -> parseInput
            val editable = Money.editable(amt)
            val parsedEditable = Money.parseInput(editable)
            assertNotNull("editable($amt) produced $editable which must be parseable", parsedEditable)
            assertEquals("editable round trip for $amt", expected, parsedEditable!!, 0.0)

            // 4. input(amt) -> parseInput
            val input = Money.input(amt)
            val parsedInput = Money.parseInput(input)
            assertNotNull("input($amt) produced $input which must be parseable", parsedInput)
            assertEquals("input round trip for $amt", expected, parsedInput!!, 0.0)
        }
    }

    @Test
    fun `acceptInput validates keystrokes, accepts valid intermediate input and rejects invalid`() {
        // Valid intermediate and finished inputs
        assertEquals("", Money.acceptInput(""))
        assertEquals(".", Money.acceptInput("."))
        assertEquals("1", Money.acceptInput("1"))
        assertEquals("1.", Money.acceptInput("1."))
        assertEquals("1.5", Money.acceptInput("1.5"))
        assertEquals("1.50", Money.acceptInput("1.50"))
        assertEquals("1000", Money.acceptInput("₹1,000"))
        assertEquals("9999999999.99", Money.acceptInput("9999999999.99"))

        // Invalid keystrokes rejected (returns null)
        assertNull(Money.acceptInput("1.234")) // Third decimal place
        assertNull(Money.acceptInput("1.2."))  // Second decimal point
        assertNull(Money.acceptInput("12a"))   // Letter
        assertNull(Money.acceptInput("NaN"))
        assertNull(Money.acceptInput("10000000000")) // Exceeds MAX_AMOUNT_RUPEES
    }

    @Test
    fun `Indian grouping covers each order of magnitude accurately`() {
        assertEquals("₹1", Money.format(1.0))
        assertEquals("₹10", Money.format(10.0))
        assertEquals("₹100", Money.format(100.0))
        assertEquals("₹1,000", Money.format(1000.0))
        assertEquals("₹10,000", Money.format(10000.0))
        assertEquals("₹1,00,000", Money.format(100000.0))
        assertEquals("₹10,00,000", Money.format(1000000.0))
        assertEquals("₹1,00,00,000", Money.format(10000000.0))
        assertEquals("₹10,00,00,000", Money.format(100000000.0))
        assertEquals("₹1,00,00,00,000", Money.format(1000000000.0))
        assertEquals("₹9,99,99,99,999.99", Money.format(MAX_AMOUNT_RUPEES))
    }
}
