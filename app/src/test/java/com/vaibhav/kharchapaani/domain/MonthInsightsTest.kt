package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthInsightsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 15)

    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(id: Long, date: LocalDate, category: String = "Food", type: String = "expense", ownedByMe: Boolean = true) =
        FullTransaction(
            TransactionEntity(
                id = id, type = type, amount = 100.0, category = category, merchant = "m$id", paymentMethod = "UPI",
                date = at(date), ownerProfileId = if (ownedByMe) null else "someone-else",
            ),
        )

    @Test
    fun `a category's entries are this month's only, newest first`() {
        val entries = MonthInsights.categoryEntries(
            transactions = listOf(
                tx(1, today.minusDays(3)),
                tx(2, LocalDate.of(2026, 3, 20)), // March: not in September's chart
                tx(3, today),
                tx(4, LocalDate.of(2025, 9, 15)), // September last year
                tx(5, today.minusDays(1), category = "Transport"),
                tx(6, today.minusDays(2), type = "income"),
            ),
            category = "Food",
            month = YearMonth.from(today),
            zone = zone,
        )

        assertEquals(listOf(3L, 1L), entries.map { it.transaction.id })
    }

    @Test
    fun `groupmates' expenses count only when I have a share in them, as the chart does`() {
        val mine = FullTransaction(
            TransactionEntity(id = 7, type = "expense", amount = 300.0, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = at(today), isSplit = true, ownerProfileId = "bob"),
            split = SplitEntity(id = 1, transactionId = 7, totalAmount = 300.0, paidByPersonId = 2),
            shares = listOf(SplitShareEntity(splitId = 1, personId = -1, shareAmount = 100.0), SplitShareEntity(splitId = 1, personId = 2, shareAmount = 200.0)),
        )
        val notMine = tx(8, today, ownedByMe = false)

        val entries = MonthInsights.categoryEntries(listOf(mine, notMine), "Food", YearMonth.from(today), zone)

        assertEquals(listOf(7L), entries.map { it.transaction.id })
    }

    @Test
    fun `this month's daily average divides by the days so far, not the whole month`() {
        val september = YearMonth.from(today)
        assertEquals(1000.0, MonthInsights.dailyAverage(15000.0, september, today), 0.0)
        assertEquals(0.0, MonthInsights.dailyAverage(0.0, september, today), 0.0)
        assertEquals(33.33, MonthInsights.dailyAverage(100.0, september, LocalDate.of(2026, 9, 3)), 0.0)
    }

    @Test
    fun `a past month's daily average divides by all its days`() {
        assertEquals(100.0, MonthInsights.dailyAverage(3100.0, YearMonth.of(2026, 8), today), 0.0)
        assertEquals(100.0, MonthInsights.dailyAverage(2800.0, YearMonth.of(2026, 2), today), 0.0)
    }
}
