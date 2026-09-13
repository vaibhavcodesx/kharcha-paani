package com.example.domain

import com.example.data.model.StatementMonthGroup
import com.example.data.model.StatementParseResult
import com.example.data.model.StatementTransactionItem
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object LocalStatementParser {

    private val supportedDateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US),
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("dd/MM/yyyy", Locale.US),
        SimpleDateFormat("dd-MM-yyyy", Locale.US),
        SimpleDateFormat("dd MMM yyyy", Locale.US),
        SimpleDateFormat("dd-MMM-yyyy", Locale.US),
        SimpleDateFormat("MMM dd, yyyy", Locale.US),
        SimpleDateFormat("yyyy/MM/dd", Locale.US)
    )

    private val displayDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val monthSortFormatter = SimpleDateFormat("yyyyMM", Locale.US)

    fun parseDateToTimestamp(rawDateStr: String?): Triple<Long, String, String> {
        val now = System.currentTimeMillis()
        if (rawDateStr.isNullOrBlank()) {
            val date = Date(now)
            return Triple(now, displayDateFormatter.format(date), monthYearFormatter.format(date))
        }

        val cleaned = rawDateStr.trim()
            .replace(",", " ")
            .replace("  ", " ")
            .trim()

        for (format in supportedDateFormats) {
            try {
                format.isLenient = true
                val date = format.parse(cleaned)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    // Adjust century if 2-digit year was parsed
                    if (cal.get(Calendar.YEAR) < 100) {
                        cal.set(Calendar.YEAR, 2000 + cal.get(Calendar.YEAR))
                    }
                    val timestamp = cal.timeInMillis
                    return Triple(timestamp, displayDateFormatter.format(cal.time), monthYearFormatter.format(cal.time))
                }
            } catch (e: Exception) {
                // continue to next format
            }
        }

        val date = Date(now)
        return Triple(now, displayDateFormatter.format(date), monthYearFormatter.format(date))
    }

    fun buildStatementGroups(
        bankOrApp: String,
        period: String,
        items: List<StatementTransactionItem>
    ): StatementParseResult {
        val sortedItems = items.sortedByDescending { it.date }

        val groups = sortedItems.groupBy { it.monthSortKey }
            .map { (monthSortKey, monthItems) ->
                val monthName = monthItems.firstOrNull()?.monthYearKey ?: "Unknown Month"
                val expenseSum = monthItems.filter { it.type == "expense" }.sumOf { it.amount }
                val incomeSum = monthItems.filter { it.type == "income" }.sumOf { it.amount }
                StatementMonthGroup(
                    monthYearKey = monthName,
                    monthSortKey = monthSortKey,
                    totalExpense = (expenseSum * 100).toLong() / 100.0,
                    totalIncome = (incomeSum * 100).toLong() / 100.0,
                    transactionCount = monthItems.size,
                    items = monthItems
                )
            }.sortedByDescending { it.monthSortKey }

        val totalExpense = (items.filter { it.type == "expense" }.sumOf { it.amount } * 100).toLong() / 100.0
        val totalIncome = (items.filter { it.type == "income" }.sumOf { it.amount } * 100).toLong() / 100.0

        return StatementParseResult(
            bankOrApp = bankOrApp.ifBlank { "Bank / UPI Statement" },
            statementPeriod = period.ifBlank {
                if (groups.isNotEmpty()) "${groups.last().monthYearKey} - ${groups.first().monthYearKey}" else ""
            },
            totalTransactionsCount = items.size,
            totalExpenseSum = totalExpense,
            totalIncomeSum = totalIncome,
            monthGroups = groups,
            allItems = sortedItems
        )
    }

    fun generateSampleParsedStatement(): StatementParseResult {
        val cal = Calendar.getInstance()
        val items = mutableListOf<StatementTransactionItem>()

        // Month 1 (Current month)
        cal.set(Calendar.DAY_OF_MONTH, 22)
        val d1 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d1,
                dateFormatted = displayDateFormatter.format(Date(d1)),
                monthYearKey = monthYearFormatter.format(Date(d1)),
                monthSortKey = monthSortFormatter.format(Date(d1)),
                type = "expense",
                amount = 450.0,
                merchant = "Zomato",
                category = "Food",
                paymentMethod = "UPI (PhonePe)",
                note = "UPI/4235619812/zomato@icici"
            )
        )

        cal.set(Calendar.DAY_OF_MONTH, 18)
        val d2 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d2,
                dateFormatted = displayDateFormatter.format(Date(d2)),
                monthYearKey = monthYearFormatter.format(Date(d2)),
                monthSortKey = monthSortFormatter.format(Date(d2)),
                type = "expense",
                amount = 1890.0,
                merchant = "Amazon India",
                category = "Shopping",
                paymentMethod = "Debit Card",
                note = "AMZN Mktp purchase"
            )
        )

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val d3 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d3,
                dateFormatted = displayDateFormatter.format(Date(d3)),
                monthYearKey = monthYearFormatter.format(Date(d3)),
                monthSortKey = monthSortFormatter.format(Date(d3)),
                type = "income",
                amount = 75000.0,
                merchant = "Company Salary",
                category = "Income (Client)",
                paymentMethod = "Bank Transfer",
                note = "NEFT CR SALARY"
            )
        )

        // Month 2 (Previous month)
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 28)
        val d4 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d4,
                dateFormatted = displayDateFormatter.format(Date(d4)),
                monthYearKey = monthYearFormatter.format(Date(d4)),
                monthSortKey = monthSortFormatter.format(Date(d4)),
                type = "expense",
                amount = 320.0,
                merchant = "Uber Trip",
                category = "Transport",
                paymentMethod = "GPay",
                note = "UPI/Uber rides"
            )
        )

        cal.set(Calendar.DAY_OF_MONTH, 15)
        val d5 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d5,
                dateFormatted = displayDateFormatter.format(Date(d5)),
                monthYearKey = monthYearFormatter.format(Date(d5)),
                monthSortKey = monthSortFormatter.format(Date(d5)),
                type = "expense",
                amount = 2100.0,
                merchant = "Electricity Bill",
                category = "Bills",
                paymentMethod = "Paytm",
                note = "State Electricity Board"
            )
        )

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val d6 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d6,
                dateFormatted = displayDateFormatter.format(Date(d6)),
                monthYearKey = monthYearFormatter.format(Date(d6)),
                monthSortKey = monthSortFormatter.format(Date(d6)),
                type = "income",
                amount = 75000.0,
                merchant = "Company Salary",
                category = "Income (Client)",
                paymentMethod = "Bank Transfer",
                note = "NEFT CR SALARY"
            )
        )

        // Month 3 (2 months ago)
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 20)
        val d7 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d7,
                dateFormatted = displayDateFormatter.format(Date(d7)),
                monthYearKey = monthYearFormatter.format(Date(d7)),
                monthSortKey = monthSortFormatter.format(Date(d7)),
                type = "expense",
                amount = 899.0,
                merchant = "Netflix Subscription",
                category = "Entertainment",
                paymentMethod = "Card",
                note = "Auto-debit recurring"
            )
        )

        cal.set(Calendar.DAY_OF_MONTH, 10)
        val d8 = cal.timeInMillis
        items.add(
            StatementTransactionItem(
                date = d8,
                dateFormatted = displayDateFormatter.format(Date(d8)),
                monthYearKey = monthYearFormatter.format(Date(d8)),
                monthSortKey = monthSortFormatter.format(Date(d8)),
                type = "expense",
                amount = 1450.0,
                merchant = "Nature's Basket Groceries",
                category = "Groceries",
                paymentMethod = "UPI",
                note = "UPI/412891901/store@upi"
            )
        )

        return buildStatementGroups("UPI / Bank Statement", "Recent 3 Months", items)
    }
}
