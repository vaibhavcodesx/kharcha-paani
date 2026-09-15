package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.FullTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Insights' view of one month, on the same terms as [MonthAnalytics]. */
object MonthInsights {

    /**
     * The expenses behind one category's total in [month], newest first: the ones I own or have a
     * share in. The old drill-down listed every transaction ever in the category under a chart of
     * this month, so "Food ₹2,400 this month" could list a dinner from March.
     */
    fun categoryEntries(transactions: List<FullTransaction>, category: String, month: YearMonth, zone: ZoneId): List<FullTransaction> =
        transactions
            .filter { ft ->
                val tx = ft.transaction
                tx.type.equals("expense", ignoreCase = true) &&
                    tx.category.equals(category, ignoreCase = true) &&
                    YearMonth.from(Instant.ofEpochMilli(tx.date).atZone(zone)) == month &&
                    (tx.isOwnedByMe || ft.shares.any { it.personId == ME })
            }
            .sortedByDescending { it.transaction.date }

    /** Spend per day: over the days so far this month (today counts), or the whole of a past month. */
    fun dailyAverage(monthSpend: Double, month: YearMonth, today: LocalDate): Double {
        if (monthSpend <= 0) return 0.0
        val days = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
        return roundPaisa(monthSpend / days)
    }

    private const val ME = -1L
}
