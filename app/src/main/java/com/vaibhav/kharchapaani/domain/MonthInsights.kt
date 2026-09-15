package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.FullTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Insights' view of the current month, on the same terms as LedgerRepository.monthlyAnalytics. */
object MonthInsights {

    /**
     * The expenses behind one category's total this month, newest first: the ones I own or have a
     * share in. The old drill-down listed every transaction ever in the category under a chart of
     * this month, so "Food ₹2,400 this month" could list a dinner from March.
     */
    fun categoryEntries(transactions: List<FullTransaction>, category: String, today: LocalDate, zone: ZoneId): List<FullTransaction> {
        val month = YearMonth.from(today)
        return transactions
            .filter { ft ->
                val tx = ft.transaction
                tx.type.equals("expense", ignoreCase = true) &&
                    tx.category.equals(category, ignoreCase = true) &&
                    YearMonth.from(Instant.ofEpochMilli(tx.date).atZone(zone)) == month &&
                    (tx.isOwnedByMe || ft.shares.any { it.personId == ME })
            }
            .sortedByDescending { it.transaction.date }
    }

    /** Spend per day so far this month (today counts as a day). */
    fun dailyAverage(monthSpend: Double, today: LocalDate): Double =
        if (monthSpend <= 0) 0.0 else roundPaisa(monthSpend / today.dayOfMonth)

    private const val ME = -1L
}
