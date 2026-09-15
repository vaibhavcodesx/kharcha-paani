package com.vaibhav.kharchapaani.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSummaryTest {

    @Test
    fun `amounts keep their paisa and Indian grouping`() {
        val summary = WidgetSummary.of(monthSpent = 18420.25, monthIncome = 100000.0, netOwedToMe = 0.0)

        assertEquals("₹18,420.25", summary.spent)
        assertEquals("₹1,00,000", summary.income)
    }

    @Test
    fun `the balance reads in the app's words, not a signed number`() {
        assertEquals("Owed ₹2,450.50", WidgetSummary.of(0.0, 0.0, 2450.5).balance)
        assertEquals("You owe ₹415.50", WidgetSummary.of(0.0, 0.0, -415.5).balance)
        assertEquals("All square", WidgetSummary.of(0.0, 0.0, 0.004).balance)
    }

    @Test
    fun `signed out shows no amounts`() {
        val summary = WidgetSummary.signedOut()

        assertEquals(listOf("—", "—", "Sign in"), listOf(summary.spent, summary.income, summary.balance))
    }
}
