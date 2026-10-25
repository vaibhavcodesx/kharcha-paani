package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatementChecksTest {

    private val utc = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 24)

    private fun row(merchant: String, amount: Double, date: LocalDate, type: String = "expense") = StatementTransactionItem(
        date = date.atStartOfDay(utc).toInstant().toEpochMilli(), dateFormatted = "", monthYearKey = "", monthSortKey = "",
        type = type, amount = amount, merchant = merchant,
    )

    private fun review(items: List<StatementTransactionItem>, period: String = "") =
        StatementChecks.review(items, period, today, utc).associateBy { it.merchant }

    @Test
    fun `ordinary rows stay ticked`() {
        val out = review(listOf(row("Swiggy", 250.0, today.minusDays(3)), row("Rent", 18_000.0, today.minusDays(10)), row("Jio", 499.0, today.minusDays(1))))

        assertTrue(out.values.all { it.isSelected && it.flag == null })
    }

    @Test
    fun `a row after today is unticked`() {
        val out = review(listOf(row("Swiggy", 250.0, today), row("Later", 250.0, today.plusDays(1))))

        assertEquals(StatementChecks.FUTURE, out.getValue("Later").flag)
        assertTrue(out.getValue("Swiggy").isSelected)
    }

    @Test
    fun `huge income among small rows is unticked, but a statement of big ones isn't`() {
        val small = (1..6).map { row("UPI $it", 200.0 + it, today.minusDays(it.toLong())) }
        val out = review(small + row("Salary", 5_00_000.0, today.minusDays(2), type = "income"))
        assertEquals(StatementChecks.LARGE, out.getValue("Salary").flag)

        // Big, but like the rest of the statement.
        val big = review(listOf(row("Rent", 25_000.0, today.minusDays(3)), row("Car EMI", 30_000.0, today.minusDays(4)), row("School", 40_000.0, today.minusDays(5))))
        assertTrue(big.values.all { it.isSelected })

        // A big expense among small ones is routine (rent), not suspicious.
        val rent = review(small + row("Rent", 25_000.0, today.minusDays(2)))
        assertTrue(rent.getValue("Rent").isSelected)

        // Ten times the median, but under ₹10,000: not worth a second look.
        val cheap = review(listOf(row("Chai", 20.0, today.minusDays(1)), row("Chai 2", 20.0, today.minusDays(2)), row("Refund", 2_500.0, today.minusDays(3), type = "income")))
        assertTrue(cheap.getValue("Refund").isSelected)
    }

    @Test
    fun `rows outside the statement's own period are unticked, with a day's grace`() {
        val period = "01/09/2026 to 30/09/2026"
        val out = review(
            listOf(
                row("In", 100.0, LocalDate.of(2026, 9, 15)),
                row("Edge", 100.0, LocalDate.of(2026, 10, 1)),
                row("Out", 100.0, LocalDate.of(2026, 8, 1)),
            ),
            period,
        )

        assertTrue(out.getValue("In").isSelected)
        assertTrue(out.getValue("Edge").isSelected)
        assertEquals(StatementChecks.outside(period), out.getValue("Out").flag)
    }

    @Test
    fun `a row already flagged while reading stays flagged and unticked`() {
        val out = review(listOf(row("Auto", 80.0, today).copy(flag = StatementChecks.NO_DATE)))

        assertEquals(StatementChecks.NO_DATE, out.getValue("Auto").flag)
        assertEquals(false, out.getValue("Auto").isSelected)
    }

    @Test
    fun `periods read in the shapes statements use, and anything else is no period`() {
        assertEquals(LocalDate.of(2024, 7, 1) to LocalDate.of(2024, 8, 31), StatementChecks.periodRange("Jul 2024 - Aug 2024"))
        assertEquals(LocalDate.of(2024, 7, 1) to LocalDate.of(2024, 8, 31), StatementChecks.periodRange("July 2024 – August 2024"))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), StatementChecks.periodRange("01/09/2026 to 30/09/2026"))
        assertEquals(LocalDate.of(2026, 9, 1) to LocalDate.of(2026, 9, 30), StatementChecks.periodRange("2026-09-01 - 2026-09-30"))
        assertNull(StatementChecks.periodRange("Aug 2024"))
        assertNull(StatementChecks.periodRange(""))
        assertNull(StatementChecks.periodRange("Aug 2024 - Jul 2024"))
    }
}
