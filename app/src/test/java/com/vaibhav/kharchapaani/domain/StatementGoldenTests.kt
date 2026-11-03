package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Golden test suite for LocalStatementParser and StatementChecks (T6).
 *
 * All statements and transaction records here use purely invented test data:
 * fictional banks, synthetic dates, and dummy merchant names.
 */
class StatementGoldenTests {

    private lateinit var originalTimeZone: TimeZone

    @Before fun pinTimeZone() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
    }

    @After fun restoreTimeZone() {
        if (::originalTimeZone.isInitialized) TimeZone.setDefault(originalTimeZone)
    }

    private val utc = ZoneOffset.UTC
    private val testToday = LocalDate.of(2026, 9, 15)

    private fun makeItem(
        rawDate: String?,
        merchant: String,
        amount: Double,
        type: String = "expense",
        category: String = "Food",
        paymentMethod: String = "UPI",
    ): StatementTransactionItem {
        val isDateValid = LocalStatementParser.readsAsDate(rawDate)
        val (timestamp, formatted, monthYear) = LocalStatementParser.parseDateToTimestamp(rawDate)
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val monthSort = SimpleDateFormat("yyyyMM", Locale.US).format(cal.time)
        return StatementTransactionItem(
            date = timestamp,
            dateFormatted = formatted,
            monthYearKey = monthYear,
            monthSortKey = monthSort,
            type = type,
            amount = amount,
            merchant = merchant,
            category = category,
            paymentMethod = paymentMethod,
            flag = if (!isDateValid) StatementChecks.NO_DATE else null,
            isSelected = isDateValid,
        )
    }

    @Test
    fun `golden statement parsing and safety checks with invented bank statement`() {
        val bankName = "Bharat Union Bank"
        val period = "01/08/2026 to 31/08/2026"

        val rawItems = listOf(
            makeItem("2026-08-02", "Sharma Tea Stall", 40.0, category = "Food", paymentMethod = "UPI"),
            makeItem("05/08/2026", "Verma Grocery Store", 1250.0, category = "Shopping", paymentMethod = "UPI"),
            makeItem("10-08-2026", "Metro Transit SmartCard", 500.0, category = "Transport", paymentMethod = "Card"),
            makeItem("15 Aug 2026", "Sun City PG Rent", 12000.0, category = "Rent", paymentMethod = "Bank Transfer"),
            makeItem("20-Aug-2026", "Zeta Broadband Fiber", 999.0, category = "Bills", paymentMethod = "UPI"),
            makeItem("2026/08/25", "City Dental Care", 800.0, category = "Health", paymentMethod = "UPI"),
            makeItem("30 Aug 2026", "Client Project Stipend", 8000.0, type = "income", category = "Income (Client)", paymentMethod = "Bank Transfer"),

            // Special rows triggering safety checks:
            // 1. Outside period (July date in an August statement)
            makeItem("2026-07-15", "Old Book Depot", 350.0, category = "Shopping", paymentMethod = "UPI"),
            // 2. Future dated row (relative to testToday: 2026-09-15)
            makeItem("2026-09-25", "Future Flight Booking", 4500.0, category = "Transport", paymentMethod = "Card"),
            // 3. Unreadable date string
            makeItem("UNREADABLE_DATE", "Cash Withdrawal", 2000.0, category = "Other", paymentMethod = "Cash"),
            // 4. Suspiciously large income anomaly (₹5,00,000 where median is under ₹1,000)
            makeItem("2026-08-18", "Crafted Corporate Bonus", 500000.0, type = "income", category = "Income (Client)", paymentMethod = "Bank Transfer"),
        )

        // Run safety review
        val reviewed = StatementChecks.review(rawItems, period, testToday, utc)
        val reviewedByMerchant = reviewed.associateBy { it.merchant }

        // Valid rows within period stay ticked and unflagged
        val tea = reviewedByMerchant.getValue("Sharma Tea Stall")
        assertTrue("Regular tea expense must be selected", tea.isSelected)
        assertNull("Regular tea expense must have no flag", tea.flag)

        val rent = reviewedByMerchant.getValue("Sun City PG Rent")
        assertTrue("Regular rent expense must be selected", rent.isSelected)
        assertNull("Rent should not be flagged as large expense", rent.flag)

        val stipend = reviewedByMerchant.getValue("Client Project Stipend")
        assertTrue("Reasonable client income should be selected", stipend.isSelected)
        assertNull("Reasonable client income should not be flagged", stipend.flag)

        // Safety check 1: Outside period row must be unticked and flagged
        val oldBook = reviewedByMerchant.getValue("Old Book Depot")
        assertFalse("Row outside statement period must be unselected", oldBook.isSelected)
        assertEquals(StatementChecks.outside(period), oldBook.flag)

        // Safety check 2: Future date must be unticked and flagged
        val futureFlight = reviewedByMerchant.getValue("Future Flight Booking")
        assertFalse("Future dated row must be unselected", futureFlight.isSelected)
        assertEquals(StatementChecks.FUTURE, futureFlight.flag)

        // Safety check 3: Unreadable date must stay unticked and flagged
        val unreadable = reviewedByMerchant.getValue("Cash Withdrawal")
        assertFalse("Unreadable date row must be unselected", unreadable.isSelected)
        assertEquals(StatementChecks.NO_DATE, unreadable.flag)

        // Safety check 4: Outlier large income must be unticked and flagged
        val suspiciousIncome = reviewedByMerchant.getValue("Crafted Corporate Bonus")
        assertFalse("Disproportionate income must be unselected", suspiciousIncome.isSelected)
        assertEquals(StatementChecks.LARGE, suspiciousIncome.flag)

        // Now test grouping and aggregation via LocalStatementParser
        val result = LocalStatementParser.buildStatementGroups(bankName, period, reviewed)

        assertEquals("Bank name must match", bankName, result.bankOrApp)
        assertEquals("Period must match", period, result.statementPeriod)
        assertEquals("Total transactions count must match items count", reviewed.size, result.totalTransactionsCount)

        // Verified sums across expense and income
        val expectedExpense = roundPaisa(rawItems.filter { it.type == "expense" }.sumOf { it.amount })
        val expectedIncome = roundPaisa(rawItems.filter { it.type == "income" }.sumOf { it.amount })
        assertEquals("Total expense sum must match", expectedExpense, result.totalExpenseSum, 0.0)
        assertEquals("Total income sum must match", expectedIncome, result.totalIncomeSum, 0.0)

        // Month groups verify descending sort and aggregate math
        assertTrue("Month groups must not be empty", result.monthGroups.isNotEmpty())
        for (mg in result.monthGroups) {
            val groupExpenses = roundPaisa(mg.items.filter { it.type == "expense" }.sumOf { it.amount })
            val groupIncome = roundPaisa(mg.items.filter { it.type == "income" }.sumOf { it.amount })
            assertEquals("Month group ${mg.monthYearKey} expense sum match", groupExpenses, mg.totalExpense, 0.0)
            assertEquals("Month group ${mg.monthYearKey} income sum match", groupIncome, mg.totalIncome, 0.0)
            assertEquals("Month group items count match", mg.items.size, mg.transactionCount)
        }
    }

    @Test
    fun `golden multi-month statement correctly groups across boundary months with invented data`() {
        val bankName = "Kaveri Trust Bank"
        val period = "Jul 2026 – Aug 2026"

        val items = listOf(
            makeItem("2026-07-05", "Cafe Deccan", 150.0, category = "Food"),
            makeItem("2026-07-20", "Stationery Hub", 220.0, category = "Shopping"),
            makeItem("2026-08-01", "July Freelance Stipend", 8000.0, type = "income", category = "Income (Client)"),
            makeItem("2026-08-10", "QuickBite Meals", 340.0, category = "Food"),
            makeItem("2026-08-28", "Fuel Station", 1200.0, category = "Transport"),
        )

        val reviewed = StatementChecks.review(items, period, testToday, utc)
        // All items within Jul-Aug 2026 are valid and within period
        assertTrue("All items must be selected", reviewed.all { it.isSelected && it.flag == null })

        val parseResult = LocalStatementParser.buildStatementGroups(bankName, period, reviewed)

        assertEquals(2, parseResult.monthGroups.size)
        // First group should be August 2026 (descending)
        val augGroup = parseResult.monthGroups[0]
        assertEquals("August 2026", augGroup.monthYearKey)
        assertEquals("202608", augGroup.monthSortKey)
        assertEquals(3, augGroup.transactionCount)
        assertEquals(1540.0, augGroup.totalExpense, 0.0)
        assertEquals(8000.0, augGroup.totalIncome, 0.0)

        // Second group should be July 2026
        val julGroup = parseResult.monthGroups[1]
        assertEquals("July 2026", julGroup.monthYearKey)
        assertEquals("202607", julGroup.monthSortKey)
        assertEquals(2, julGroup.transactionCount)
        assertEquals(370.0, julGroup.totalExpense, 0.0)
        assertEquals(0.0, julGroup.totalIncome, 0.0)
    }

    @Test
    fun `statement checks grace period handles dates one day before and after period boundaries`() {
        val period = "01/08/2026 to 31/08/2026"
        val items = listOf(
            makeItem("2026-07-31", "Timezone Boundary Left Grace", 100.0), // 1 day before: covered by grace
            makeItem("2026-09-01", "Timezone Boundary Right Grace", 100.0), // 1 day after: covered by grace
            makeItem("2026-07-30", "Two Days Before Period", 100.0),        // 2 days before: flagged outside
            makeItem("2026-09-02", "Two Days After Period", 100.0),         // 2 days after: flagged outside
        )

        val reviewed = StatementChecks.review(items, period, testToday, utc).associateBy { it.merchant }

        assertTrue("One day before period must be granted grace", reviewed.getValue("Timezone Boundary Left Grace").isSelected)
        assertTrue("One day after period must be granted grace", reviewed.getValue("Timezone Boundary Right Grace").isSelected)

        val twoDaysBefore = reviewed.getValue("Two Days Before Period")
        assertFalse("Two days before period must be unselected", twoDaysBefore.isSelected)
        assertEquals(StatementChecks.outside(period), twoDaysBefore.flag)

        val twoDaysAfter = reviewed.getValue("Two Days After Period")
        assertFalse("Two days after period must be unselected", twoDaysAfter.isSelected)
        assertEquals(StatementChecks.outside(period), twoDaysAfter.flag)
    }
}
