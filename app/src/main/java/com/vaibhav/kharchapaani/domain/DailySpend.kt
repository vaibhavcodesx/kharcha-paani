package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.FullTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What one day of a month cost. Every day of the month is present, spent or not. */
data class DaySpend(
    val date: LocalDate,
    val amount: Double,
    /** False for days after today in the current month: no spending, but nothing to read into it. */
    val hasHappened: Boolean,
)

/**
 * Spending per day across a month, for the flow chart in Insights.
 *
 * The share rules are [MonthAnalytics]'s, deliberately: a figure on this chart has to add up to the
 * month total shown above it, and "my share of a group dinner" is what that total counts.
 */
object DailySpend {

    private const val ME = -1L

    fun of(
        transactions: List<FullTransaction>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
    ): List<DaySpend> {
        val totals = mutableMapOf<LocalDate, Double>()

        for (full in transactions) {
            val tx = full.transaction
            if (tx.type.equals("income", ignoreCase = true)) continue
            // Mine, or a groupmate's that I have a share in.
            if (!tx.isOwnedByMe && full.shares.none { it.personId == ME }) continue

            val date = Instant.ofEpochMilli(tx.date).atZone(zone).toLocalDate()
            if (YearMonth.from(date) != month) continue

            totals[date] = (totals[date] ?: 0.0) + myShareOf(full)
        }

        return (1..month.lengthOfMonth()).map { day ->
            val date = month.atDay(day)
            DaySpend(
                date = date,
                amount = roundPaisa(totals[date] ?: 0.0),
                hasHappened = !date.isAfter(today),
            )
        }
    }

    /** The same split arithmetic [MonthAnalytics] uses, so the two always agree. */
    private fun myShareOf(full: FullTransaction): Double {
        val split = full.split ?: return full.transaction.amount
        val myShare = full.shares.ownShareOf(ME)
        return when {
            split.paidByPersonId != ME -> myShare ?: 0.0
            myShare != null -> myShare
            else -> (full.transaction.amount - full.shares.allButOwnShareOf(ME)).coerceAtLeast(0.0)
        }
    }

    /** The busiest day of the month, or null when nothing was spent. */
    fun busiest(days: List<DaySpend>): DaySpend? = days.filter { it.amount > 0.0 }.maxByOrNull { it.amount }

    /**
     * The average across days that have actually happened. A month in progress divides by the days
     * so far, not by 30, or the first of the month always looks like a triumph.
     */
    fun dailyAverage(days: List<DaySpend>): Double {
        val elapsed = days.count { it.hasHappened }
        if (elapsed == 0) return 0.0
        return roundPaisa(days.filter { it.hasHappened }.sumOf { it.amount } / elapsed)
    }
}
