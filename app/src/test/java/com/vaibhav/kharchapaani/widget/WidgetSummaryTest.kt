package com.vaibhav.kharchapaani.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetSummaryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `amounts keep their paisa and Indian grouping`() {
        val summary = WidgetSummary.of(context, monthSpent = 18420.25, monthIncome = 100000.0, netOwedToMe = 0.0)

        assertEquals("₹18,420.25", summary.spent)
        assertEquals("₹1,00,000", summary.income)
    }

    @Test
    fun `the balance reads in the app's words, not a signed number`() {
        assertEquals("Owed ₹2,450.50", WidgetSummary.of(context, 0.0, 0.0, 2450.5).balance)
        assertEquals("You owe ₹415.50", WidgetSummary.of(context, 0.0, 0.0, -415.5).balance)
        assertEquals("All square", WidgetSummary.of(context, 0.0, 0.0, 0.004).balance)
    }

    @Test
    fun `the balance is coloured the way Home and Splits colour it`() {
        assertEquals(WidgetSummary.LIME, WidgetSummary.of(context, 0.0, 0.0, 10.0).balanceColor)
        // Coral, as "You owe" is everywhere in the app; off-white used to read as neutral here.
        assertEquals(WidgetSummary.MONEY_OUT, WidgetSummary.of(context, 0.0, 0.0, -10.0).balanceColor)
        assertEquals(WidgetSummary.MUTED, WidgetSummary.of(context, 0.0, 0.0, 0.0).balanceColor)
    }

    @Test
    fun `today is shown to the paisa, and is zero when nothing was logged`() {
        assertEquals("₹120.50", WidgetSummary.of(context, 0.0, 0.0, 0.0, todaySpent = 120.5).today)
        assertEquals("₹0", WidgetSummary.of(context, 0.0, 0.0, 0.0).today)
    }

    @Test
    fun `signed out shows no amounts and says to sign in`() {
        val summary = WidgetSummary.signedOut(context)

        assertEquals(listOf("—", "—", "—", "Sign in"), listOf(summary.spent, summary.income, summary.today, summary.balance))
        assertEquals("Sign in to start logging", summary.hint)
        assertFalse(summary.signedIn)
        assertTrue(WidgetSummary.of(context, 0.0, 0.0, 0.0).signedIn)
    }
}
