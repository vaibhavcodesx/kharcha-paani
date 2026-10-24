package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.StatementMonthGroup
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
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

    private val YEAR_FIRST = Regex("^\\d{4}[-/]")

    private val displayDateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
    private val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.US)
    private val monthSortFormatter = SimpleDateFormat("yyyyMM", Locale.US)

    fun parseDateToTimestamp(rawDateStr: String?): Triple<Long, String, String> {
        val cal = readDate(rawDateStr) ?: Calendar.getInstance()
        return Triple(cal.timeInMillis, displayDateFormatter.format(cal.time), monthYearFormatter.format(cal.time))
    }

    /**
     * Whether [rawDateStr] reads as a date at all. [parseDateToTimestamp] puts a row it can't read
     * on today, which is a guess, so the import leaves such a row unticked (see [StatementChecks]).
     */
    fun readsAsDate(rawDateStr: String?): Boolean = readDate(rawDateStr) != null

    private fun readDate(rawDateStr: String?): Calendar? {
        if (rawDateStr.isNullOrBlank()) return null

        val cleaned = rawDateStr.trim()
            .replace(",", " ")
            .replace("  ", " ")
            .trim()

        // Strict, and a year-first format only for text that starts with a 4-digit year: leniently,
        // "05-10-2026" read as year 5, month 10, day 2026, rolled over and came out in 2011
        // (correctness audit, 2026-10-24).
        val startsWithYear = YEAR_FIRST.containsMatchIn(cleaned)
        for (format in supportedDateFormats) {
            if (format.toPattern().startsWith("yyyy") && !startsWithYear) continue
            try {
                format.isLenient = false
                val date = format.parse(cleaned)
                if (date != null) {
                    val cal = Calendar.getInstance().apply { time = date }
                    // Adjust century if 2-digit year was parsed
                    if (cal.get(Calendar.YEAR) < 100) {
                        cal.set(Calendar.YEAR, 2000 + cal.get(Calendar.YEAR))
                    }
                    return cal
                }
            } catch (e: Exception) {
                // continue to next format
            }
        }
        return null
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
                    totalExpense = roundPaisa(expenseSum),
                    totalIncome = roundPaisa(incomeSum),
                    transactionCount = monthItems.size,
                    items = monthItems
                )
            }.sortedByDescending { it.monthSortKey }

        val totalExpense = roundPaisa(items.filter { it.type == "expense" }.sumOf { it.amount })
        val totalIncome = roundPaisa(items.filter { it.type == "income" }.sumOf { it.amount })

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
}
