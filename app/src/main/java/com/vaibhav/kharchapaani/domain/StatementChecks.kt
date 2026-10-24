package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/**
 * Which rows of a read statement to leave unticked for a second look, and why.
 *
 * Every row Gemini returned used to arrive ticked, so one Import added all of them. A statement is
 * a file from anywhere, and the model reads whatever is in it: a crafted PDF could slip in a
 * ₹5,00,000 "salary" among real UPI payments (security audit, 2026-10-24). These rows are still
 * listed, just unticked, with the reason under them:
 * - a date that couldn't be read (the row would otherwise land on today);
 * - a date after today;
 * - a date outside the statement's own period, when that period can be read;
 * - money in far bigger than the rest of the statement. Income only: a big expense (rent, an EMI)
 *   is routine and would need ticking on every import, and the risk is money that was never
 *   received making the month look better than it is.
 */
object StatementChecks {

    const val NO_DATE = "Couldn't read its date: it would be added as today"
    const val FUTURE = "Dated after today"
    const val LARGE = "Much bigger than the rest of this statement"
    fun outside(period: String) = "Outside the statement's dates ($period)"

    /** Income this many times the statement's median, and at least [LARGE_FLOOR], is checked. */
    internal const val LARGE_MULTIPLE = 10.0
    internal const val LARGE_FLOOR = 10_000.0

    /**
     * [items] with a reason and unticked where one applies. A row already flagged (an unreadable
     * date, set while reading it) keeps that reason.
     */
    fun review(
        items: List<StatementTransactionItem>,
        period: String,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<StatementTransactionItem> {
        val range = periodRange(period)
        val median = median(items.map { it.amount })
        return items.map { item ->
            if (item.flag != null) return@map item.copy(isSelected = false)
            val day = Instant.ofEpochMilli(item.date).atZone(zone).toLocalDate()
            val reason = when {
                day.isAfter(today) -> FUTURE
                // A day's grace either side: a statement's period and its rows' dates are sometimes a
                // time zone apart.
                range != null && (day.isBefore(range.first.minusDays(1)) || day.isAfter(range.second.plusDays(1))) -> outside(period.trim())
                item.type == "income" && item.amount >= LARGE_FLOOR && item.amount >= LARGE_MULTIPLE * median -> LARGE
                else -> null
            }
            if (reason == null) item else item.copy(flag = reason, isSelected = false)
        }
    }

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private val separators = Regex("\\s+(?:-|–|—|to)\\s+|\\s*[–—]\\s*", RegexOption.IGNORE_CASE)

    private fun formatter(pattern: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.US)

    private val dayFormats = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "d MMM yyyy", "d MMMM yyyy", "d-MMM-yyyy", "MMM d yyyy").map(::formatter)
    private val monthFormats = listOf("MMM yyyy", "MMMM yyyy").map(::formatter)

    /**
     * The statement's own period ("Jul 2024 - Aug 2024", "01/07/2024 to 31/08/2024"), or null when
     * it isn't two dates that read: then only the other checks apply.
     */
    internal fun periodRange(period: String): Pair<LocalDate, LocalDate>? {
        val sides = period.replace(",", " ").trim().split(separators).map { it.trim() }.filter { it.isNotEmpty() }
        if (sides.size != 2) return null
        val start = readSide(sides[0], end = false) ?: return null
        val end = readSide(sides[1], end = true) ?: return null
        return if (end.isBefore(start)) null else start to end
    }

    private fun readSide(text: String, end: Boolean): LocalDate? {
        val clean = text.replace(Regex("\\s+"), " ")
        dayFormats.forEach { f -> runCatching { return LocalDate.parse(clean, f) } }
        monthFormats.forEach { f ->
            runCatching { YearMonth.parse(clean, f) }.getOrNull()?.let { return if (end) it.atEndOfMonth() else it.atDay(1) }
        }
        return null
    }
}
