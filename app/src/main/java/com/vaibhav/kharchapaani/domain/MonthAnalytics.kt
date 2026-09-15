package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One month's spending and income, as Insights, the Home month strip and the widget show it. */
object MonthAnalytics {

    fun of(
        transactions: List<TransactionEntity>,
        splits: List<SplitEntity>,
        shares: List<SplitShareEntity>,
        month: YearMonth,
        today: LocalDate,
        zone: ZoneId,
    ): MonthlyAnalytics {
        val splitMap = splits.associateBy { it.transactionId }
        val shareMap = shares.groupBy { it.splitId }

        // Mine, or a groupmate's that I have a share in.
        val monthTransactions = transactions.filter { tx ->
            (tx.isOwnedByMe || splitMap[tx.id]?.let { split -> shareMap[split.id].orEmpty().any { it.personId == ME } } == true) &&
                YearMonth.from(Instant.ofEpochMilli(tx.date).atZone(zone)) == month
        }

        var totalPersonalExpense = 0.0
        var totalIncome = 0.0
        val categoryTotals = mutableMapOf<String, Double>()
        val categoryCounts = mutableMapOf<String, Int>()
        val merchantTotals = mutableMapOf<String, Double>()

        for (tx in monthTransactions) {
            if (tx.type.equals("income", ignoreCase = true)) {
                totalIncome += tx.amount
                continue
            }
            val split = splitMap[tx.id]
            val personalExpenseAmount = if (split != null) {
                val splitShares = shareMap[split.id].orEmpty()
                val myShare = splitShares.find { it.personId == ME }?.shareAmount
                when {
                    split.paidByPersonId != ME -> myShare ?: 0.0
                    myShare != null -> myShare
                    else -> (tx.amount - splitShares.filter { it.personId != ME }.sumOf { it.shareAmount }).coerceAtLeast(0.0)
                }
            } else {
                tx.amount
            }

            totalPersonalExpense += personalExpenseAmount
            categoryTotals[tx.category] = (categoryTotals[tx.category] ?: 0.0) + personalExpenseAmount
            categoryCounts[tx.category] = (categoryCounts[tx.category] ?: 0) + 1
            if (tx.merchant.isNotBlank()) {
                merchantTotals[tx.merchant] = (merchantTotals[tx.merchant] ?: 0.0) + personalExpenseAmount
            }
        }

        val summaries = categoryTotals.map { (cat, amount) ->
            CategorySummary(
                category = cat,
                amount = roundPaisa(amount),
                percentage = if (totalPersonalExpense > 0) ((amount / totalPersonalExpense) * 100f).toFloat() else 0f,
                count = categoryCounts[cat] ?: 1,
            )
        }.sortedByDescending { it.amount }

        return MonthlyAnalytics(
            totalExpense = roundPaisa(totalPersonalExpense),
            totalIncome = roundPaisa(totalIncome),
            netCashflow = roundPaisa(totalIncome - totalPersonalExpense),
            categorySummaries = summaries,
            topMerchant = merchantTotals.maxByOrNull { it.value }?.key ?: (summaries.firstOrNull()?.category ?: "None"),
            dailyStreak = dailyStreak(transactions.filter { it.isOwnedByMe }, today, zone),
            topCategory = summaries.firstOrNull()?.category ?: "Food",
            topCategoryAmount = summaries.firstOrNull()?.amount ?: 0.0,
        )
    }

    /** Days in a row, up to today, with something logged. A day not logged yet today doesn't break it. */
    fun dailyStreak(transactions: List<TransactionEntity>, today: LocalDate, zone: ZoneId): Int {
        val days = transactions.mapTo(HashSet()) { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
        var day = if (today in days) today else today.minusDays(1)
        var streak = 0
        while (day in days) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** The first month with anything in it, so the month picker doesn't go back further. */
    fun earliestMonth(transactions: List<TransactionEntity>, today: LocalDate, zone: ZoneId): YearMonth =
        transactions.minOfOrNull { it.date }
            ?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) }
            ?.coerceAtMost(YearMonth.from(today))
            ?: YearMonth.from(today)

    private const val ME = -1L
}
