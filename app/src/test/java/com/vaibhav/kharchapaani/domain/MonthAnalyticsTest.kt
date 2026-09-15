package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthAnalyticsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 15)

    private fun at(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    private fun tx(
        id: Long,
        time: LocalDateTime,
        amount: Double,
        category: String = "Food",
        merchant: String = "m$id",
        type: String = "expense",
        owner: String? = null,
    ) = TransactionEntity(id = id, type = type, amount = amount, category = category, merchant = merchant, paymentMethod = "UPI", date = at(time), ownerProfileId = owner)

    // Three months of entries, with the edges of September right at midnight.
    private val august31Late = tx(1, LocalDateTime.of(2026, 8, 31, 23, 59), 500.0, "Shopping", "Myntra")
    private val september1Midnight = tx(2, LocalDateTime.of(2026, 9, 1, 0, 0), 120.0, "Food", "Chai Point")
    private val septemberRent = tx(3, LocalDateTime.of(2026, 9, 5, 10, 0), 18000.0, "Rent", "Landlord")
    private val septemberSalary = tx(4, LocalDateTime.of(2026, 9, 1, 9, 0), 60000.0, "Income (Salary)", "Acme", type = "income")
    private val september30Late = tx(5, LocalDateTime.of(2026, 9, 30, 23, 59), 80.0, "Transport", "Rapido")
    private val october1 = tx(6, LocalDateTime.of(2026, 10, 1, 0, 0), 999.0, "Food", "Swiggy")
    private val augustSalary = tx(7, LocalDateTime.of(2026, 8, 1, 9, 0), 55000.0, "Income (Salary)", "Acme", type = "income")
    private val lastSeptember = tx(8, LocalDateTime.of(2025, 9, 10, 12, 0), 7000.0, "Shopping", "Croma")

    // A dinner I paid ₹900 for, split three ways: ₹300 is mine.
    private val splitDinner = tx(9, LocalDateTime.of(2026, 8, 20, 21, 0), 900.0, "Food", "Truffles")
    private val dinnerSplit = SplitEntity(id = 1, transactionId = 9, totalAmount = 900.0)
    private val dinnerShares = listOf(
        SplitShareEntity(splitId = 1, personId = -1, shareAmount = 300.0),
        SplitShareEntity(splitId = 1, personId = 2, shareAmount = 300.0),
        SplitShareEntity(splitId = 1, personId = 3, shareAmount = 300.0),
    )

    // A groupmate's September expense I'm not in, and one I owe ₹250 of.
    private val notMine = tx(10, LocalDateTime.of(2026, 9, 12, 13, 0), 4000.0, "Entertainment", owner = "bob")
    private val bobsCab = tx(11, LocalDateTime.of(2026, 9, 12, 18, 0), 500.0, "Transport", "Uber", owner = "bob")
    private val cabSplit = SplitEntity(id = 2, transactionId = 11, totalAmount = 500.0, paidByPersonId = 2)
    private val cabShares = listOf(SplitShareEntity(splitId = 2, personId = -1, shareAmount = 250.0), SplitShareEntity(splitId = 2, personId = 2, shareAmount = 250.0))

    private val all = listOf(august31Late, september1Midnight, septemberRent, septemberSalary, september30Late, october1, augustSalary, lastSeptember, splitDinner, notMine, bobsCab)
    private val splits = listOf(dinnerSplit, cabSplit)
    private val shares = dinnerShares + cabShares

    private fun month(year: Int, month: Int) = MonthAnalytics.of(all, splits, shares, YearMonth.of(year, month), today, zone)

    @Test
    fun `September counts only September, edges included`() {
        val september = month(2026, 9)

        // Chai 120 + rent 18000 + Rapido 80 + my half of Bob's cab 250.
        assertEquals(18450.0, september.totalExpense, 0.0)
        assertEquals(60000.0, september.totalIncome, 0.0)
        assertEquals(41550.0, september.netCashflow, 0.0)
        assertEquals(listOf("Rent", "Transport", "Food"), september.categorySummaries.map { it.category })
        assertEquals(330.0, september.categorySummaries.first { it.category == "Transport" }.amount, 0.0)
        assertEquals("Landlord", september.topMerchant)
    }

    @Test
    fun `August counts August, with only my share of a split`() {
        val august = month(2026, 8)

        // Myntra 500 + my 300 of the dinner.
        assertEquals(800.0, august.totalExpense, 0.0)
        assertEquals(55000.0, august.totalIncome, 0.0)
        assertEquals(listOf("Shopping", "Food"), august.categorySummaries.map { it.category })
        assertEquals(300.0, august.categorySummaries.first { it.category == "Food" }.amount, 0.0)
    }

    @Test
    fun `October starts at its first midnight`() {
        val october = month(2026, 10)

        assertEquals(999.0, october.totalExpense, 0.0)
        assertEquals(0.0, october.totalIncome, 0.0)
    }

    @Test
    fun `the same month last year is its own month`() {
        assertEquals(7000.0, month(2025, 9).totalExpense, 0.0)
        assertEquals(0.0, month(2025, 8).totalExpense, 0.0)
    }

    @Test
    fun `a month with nothing is empty, not borrowed from another`() {
        val july = month(2026, 7)

        assertEquals(0.0, july.totalExpense, 0.0)
        assertEquals(0.0, july.totalIncome, 0.0)
        assertEquals(emptyList<String>(), july.categorySummaries.map { it.category })
        assertEquals("None", july.topMerchant)
    }

    @Test
    fun `category shares add up to the month's spend`() {
        val september = month(2026, 9)

        assertEquals(100.0, september.categorySummaries.sumOf { it.percentage.toDouble() }, 0.01)
    }

    @Test
    fun `the picker starts at the first month with anything in it`() {
        assertEquals(YearMonth.of(2025, 9), MonthAnalytics.earliestMonth(all, today, zone))
        assertEquals(YearMonth.of(2026, 9), MonthAnalytics.earliestMonth(emptyList(), today, zone))
    }

    @Test
    fun `the streak counts back from today, and a quiet morning doesn't break it`() {
        val days = listOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
            .mapIndexed { i, d -> tx(100L + i, d.atTime(12, 0), 10.0) }

        assertEquals(3, MonthAnalytics.dailyStreak(days, today, zone))
        assertEquals(2, MonthAnalytics.dailyStreak(days.drop(1), today, zone))
        assertEquals(0, MonthAnalytics.dailyStreak(days.drop(3), today, zone))
    }
}
