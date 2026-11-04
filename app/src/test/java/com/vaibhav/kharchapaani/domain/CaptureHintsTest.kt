package com.vaibhav.kharchapaani.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.home.CaptureHints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The lines Home's capture field cycles through must be ones that actually work. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureHintsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `the question comes first, then the examples, then round again`() {
        val hints = CaptureHints.sequence(prompt = "What did you spend on?", examples = listOf("a", "b"), format = { "Try: $it" })

        assertEquals(listOf("What did you spend on?", "Try: a", "Try: b"), hints)
        assertEquals(1, CaptureHints.next(0, hints.size))
        assertEquals(0, CaptureHints.next(2, hints.size))
        assertEquals(0, CaptureHints.next(5, 0))
    }

    @Test
    fun `every example is a line quick add reads with an amount, and is short enough to fit`() {
        val examples = context.resources.getStringArray(R.array.home_capture_examples).toList()

        assertTrue("several examples, to teach the different ways to say it", examples.size >= 5)
        examples.forEach { line ->
            assertTrue("'$line' has an amount", LocalHeuristicParser.parse(line, knownFriends = listOf("Rohan", "Aaru")).amount > 0)
            assertTrue("'$line' fits the line", line.length <= 30)
        }
    }

    @Test
    fun `the examples teach splitting, income and a day word as well as plain spending`() {
        val parsed = context.resources.getStringArray(R.array.home_capture_examples)
            .map { LocalHeuristicParser.parse(it, knownFriends = listOf("Rohan", "Aaru")) }

        assertTrue(parsed.any { it.splitWithPersonNames.isNotEmpty() })
        assertTrue(parsed.any { it.type == "income" })
        assertTrue(parsed.any { it.paymentMethod == "Cash" })
    }
}
