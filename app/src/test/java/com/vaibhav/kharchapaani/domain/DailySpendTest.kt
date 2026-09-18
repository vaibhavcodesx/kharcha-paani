package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * The day-by-day flow chart's arithmetic. It has to agree with the month total shown above it, so
 * it uses MonthAnalytics' share rules rather than raw amounts — a figure on the chart that doesn't
 * add up to the header is worse than no chart.
 */
class DailySpendTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val month = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 18)

    private fun at(day: Int, hour: Int = 12) =
        LocalDateTime.of(2026, 9, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    /** A groupmate's expense is one with an owner profile; mine has none. */
    private fun expense(id: Long, day: Int, amount: Double, ownedByMe: Boolean = true) =
        TransactionEntity(
            id = id, type = "expense", amount = amount, category = "Food", merchant = "M",
            paymentMethod = "UPI", date = at(day),
            ownerProfileId = if (ownedByMe) null else "groupmate-profile",
        )

    private fun of(vararg items: FullTransaction) = DailySpend.of(items.toList(), month, today, zone)

    @Test
    fun `every day of the month is present, spent or not`() {
        val days = of(FullTransaction(expense(1, 3, 250.0)))

        assertEquals(30, days.size)
        assertEquals(LocalDate.of(2026, 9, 1), days.first().date)
        assertEquals(LocalDate.of(2026, 9, 30), days.last().date)
    }

    @Test
    fun `a day's spending is the sum of that day's expenses`() {
        val days = of(
            FullTransaction(expense(1, 3, 250.0)),
            FullTransaction(expense(2, 3, 99.5)),
            FullTransaction(expense(3, 4, 40.0)),
        )

        assertEquals(349.5, days[2].amount, 0.001)
        assertEquals(40.0, days[3].amount, 0.001)
        assertEquals(0.0, days[4].amount, 0.001)
    }

    @Test
    fun `income never appears on a spending chart`() {
        val salary = TransactionEntity(
            id = 9, type = "income", amount = 75000.0, category = "Income (Salary)", merchant = "Acme",
            paymentMethod = "Bank Transfer", date = at(1),
        )

        assertEquals(0.0, of(FullTransaction(salary)).first().amount, 0.001)
    }

    @Test
    fun `a split expense counts my share, not the whole bill`() {
        // Otherwise a ₹3,000 dinner split three ways puts ₹3,000 on the chart and ₹1,000 in the
        // header above it, and the two disagree on the same screen.
        val tx = expense(1, 10, 3000.0)
        val split = SplitEntity(id = 1, transactionId = 1, totalAmount = 3000.0, paidByPersonId = -1L)
        val shares = listOf(
            SplitShareEntity(splitId = 1, personId = -1L, shareAmount = 1000.0),
            SplitShareEntity(splitId = 1, personId = 2, shareAmount = 1000.0),
            SplitShareEntity(splitId = 1, personId = 3, shareAmount = 1000.0),
        )

        assertEquals(1000.0, of(FullTransaction(tx, split, shares))[9].amount, 0.001)
    }

    @Test
    fun `a groupmate's expense counts only my share of it`() {
        val tx = expense(1, 10, 900.0, ownedByMe = false)
        val split = SplitEntity(id = 1, transactionId = 1, totalAmount = 900.0, paidByPersonId = 2)
        val shares = listOf(
            SplitShareEntity(splitId = 1, personId = -1L, shareAmount = 300.0),
            SplitShareEntity(splitId = 1, personId = 2, shareAmount = 600.0),
        )

        assertEquals(300.0, of(FullTransaction(tx, split, shares))[9].amount, 0.001)
    }

    @Test
    fun `a groupmate's expense I have no share in is not mine`() {
        val tx = expense(1, 10, 900.0, ownedByMe = false)
        val split = SplitEntity(id = 1, transactionId = 1, totalAmount = 900.0, paidByPersonId = 2)
        val shares = listOf(SplitShareEntity(splitId = 1, personId = 2, shareAmount = 900.0))

        assertEquals(0.0, of(FullTransaction(tx, split, shares))[9].amount, 0.001)
    }

    @Test
    fun `another month's spending stays in its own month`() {
        val august = expense(1, 3, 500.0).copy(
            date = LocalDateTime.of(2026, 8, 3, 12, 0).atZone(zone).toInstant().toEpochMilli(),
        )

        assertTrue(of(FullTransaction(august)).all { it.amount == 0.0 })
    }

    @Test
    fun `days after today are marked as not yet happened`() {
        val days = of(FullTransaction(expense(1, 3, 250.0)))

        assertTrue(days[17].hasHappened)
        assertFalse(days[18].hasHappened)
        assertFalse(days.last().hasHappened)
    }

    @Test
    fun `a finished month has every day happened`() {
        val august = DailySpend.of(emptyList(), YearMonth.of(2026, 8), today, zone)

        assertTrue(august.all { it.hasHappened })
        assertEquals(31, august.size)
    }

    @Test
    fun `the busiest day is the one with the most spent`() {
        val days = of(
            FullTransaction(expense(1, 3, 250.0)),
            FullTransaction(expense(2, 9, 1800.0)),
            FullTransaction(expense(3, 14, 60.0)),
        )

        assertEquals(LocalDate.of(2026, 9, 9), DailySpend.busiest(days)?.date)
    }

    @Test
    fun `a month with nothing spent has no busiest day`() {
        assertNull(DailySpend.busiest(of()))
    }

    @Test
    fun `the daily average divides by the days so far, not by the whole month`() {
        // 18 days elapsed of 30. ₹1,800 spent -> ₹100 a day, not ₹60.
        val days = of(FullTransaction(expense(1, 3, 1800.0)))

        assertEquals(100.0, DailySpend.dailyAverage(days), 0.001)
    }

    @Test
    fun `midnight belongs to the day it falls on`() {
        val days = of(FullTransaction(expense(1, 5, 120.0).copy(date = at(5, hour = 0))))

        assertEquals(120.0, days[4].amount, 0.001)
        assertEquals(0.0, days[3].amount, 0.001)
    }
}
