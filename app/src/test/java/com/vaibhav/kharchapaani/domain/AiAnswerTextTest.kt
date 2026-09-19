package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things every answer must be true of, enforced after the fact because a prompt can ask for
 * a format but never guarantee one.
 */
class AiAnswerTextTest {

    // --- Currency

    @Test
    fun `the forms a model actually produces all become the rupee sign`() {
        // Every one of these came back from a real answer at some point.
        assertEquals("₹4672", AiAnswerText.rupees("Rs 4672"))
        assertEquals("₹4,672", AiAnswerText.rupees("Rs. 4,672"))
        assertEquals("₹2009.50", AiAnswerText.rupees("INR 2009.50"))
        assertEquals("₹366.68", AiAnswerText.rupees("rs366.68"))
        assertEquals("₹500", AiAnswerText.rupees("₹ 500"))
    }

    @Test
    fun `a sentence is fixed everywhere, not just at the first amount`() {
        val fixed = AiAnswerText.rupees("led by Shopping at Rs 4672 and Food at Rs 2187.")

        assertEquals("led by Shopping at ₹4672 and Food at ₹2187.", fixed)
        assertFalse(fixed.contains("Rs"))
    }

    @Test
    fun `a word that only looks like currency is left alone`() {
        // Only a form sitting directly in front of a digit is an amount.
        assertEquals("The RSVP list", AiAnswerText.rupees("The RSVP list"))
        assertEquals("Rs and pence", AiAnswerText.rupees("Rs and pence"))
    }

    // --- Truncation

    @Test
    fun `the reported truncation is trimmed back to where the answer last finished`() {
        // Verbatim from the device: the answer stopped mid-clause at the output cap.
        val raw = "Your spending in August was concentrated on specific days. The busiest day was " +
            "25 August at ₹2306, which is much higher than your average per day of ₹366.68. You also had"

        val tidied = AiAnswerText.tidy(raw, truncated = true)!!

        assertTrue(tidied.endsWith("average per day of ₹366.68."))
        assertFalse(tidied.contains("You also had"))
    }

    @Test
    fun `a decimal point is not the end of a sentence`() {
        // "₹366.68" would otherwise cut the answer in half at the full stop inside the figure.
        val text = "Food came to ₹2187.50 across eleven entries and Transport to ₹1200.25 across six"

        assertEquals(text, AiAnswerText.toLastCompleteSentence(text))
    }

    @Test
    fun `a question or an exclamation ends a sentence too`() {
        assertEquals("Where did it go? Mostly Shopping.", AiAnswerText.toLastCompleteSentence("Where did it go? Mostly Shopping. And a bit of"))
    }

    @Test
    fun `an answer that never finished a sentence is kept rather than thrown away`() {
        // A fragment still says more than an empty card.
        assertEquals("You spent most of it on", AiAnswerText.toLastCompleteSentence("You spent most of it on"))
    }

    @Test
    fun `an answer that came back whole is not trimmed`() {
        val whole = "Shopping led the month at ₹4,672. Food followed at ₹2,187"

        assertEquals(whole, AiAnswerText.tidy(whole, truncated = false))
    }

    @Test
    fun `nothing at all stays nothing, rather than becoming an empty answer on screen`() {
        // What "What to change" returned when thinking used the entire token budget.
        assertNull(AiAnswerText.tidy("", truncated = true))
        assertNull(AiAnswerText.tidy("   \n ", truncated = false))
    }

    @Test
    fun `tidying does both jobs at once`() {
        val tidied = AiAnswerText.tidy("Shopping led at Rs 4,672. Food was Rs 2,187. You also", truncated = true)!!

        assertEquals("Shopping led at ₹4,672. Food was ₹2,187.", tidied)
    }
}
