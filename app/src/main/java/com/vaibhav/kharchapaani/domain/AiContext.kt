package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The data block each [AiPrompt] is given.
 *
 * Pure on purpose: what the model is asked is the part most worth pinning down, and building it
 * here means it can be tested without a network — the same reason [DailySpend] is a domain object
 * rather than something the chart works out for itself.
 *
 * Every figure the answer is allowed to use has to appear here, because the voice rules forbid
 * inventing one. So anything an answer should be able to say has to be in this string.
 */
object AiContext {

    /** How many previous months [Overspending] and [WhatToChange] compare against. */
    const val TRAILING_MONTHS = 3

    private fun rupees(amount: Double): String = "Rs " + Money.plain(amount)

    fun monthInReview(
        analytics: MonthlyAnalytics,
        days: List<DaySpend>,
        month: YearMonth,
    ): String = buildString {
        appendLine("Month: ${month.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${month.year}")
        appendLine("Total spent: ${rupees(analytics.totalExpense)}")
        appendLine("Total came in: ${rupees(analytics.totalIncome)}")
        appendLine("Days logged in a row: ${analytics.dailyStreak}")
        appendLine()
        appendLine("By category:")
        analytics.categorySummaries.forEach { appendLine("- ${it.category}: ${rupees(it.amount)} over ${it.count} ${entries(it.count)}") }
        if (analytics.topMerchant.isNotBlank()) {
            appendLine()
            appendLine("Spent most at: ${analytics.topMerchant}")
        }
        val elapsed = days.filter { it.hasHappened }
        if (elapsed.isNotEmpty()) {
            appendLine()
            appendLine("Average per day so far: ${rupees(DailySpend.dailyAverage(days))}")
            DailySpend.busiest(days)?.let { appendLine("Busiest day: ${it.date} at ${rupees(it.amount)}") }
            appendLine("Days with nothing spent: ${elapsed.count { it.amount <= 0.0 }} of ${elapsed.size}")
            appendLine()
            appendLine("Spending per day:")
            elapsed.filter { it.amount > 0.0 }.forEach { appendLine("- ${it.date}: ${rupees(it.amount)}") }
        }
    }

    /**
     * This month against each of the previous [TRAILING_MONTHS], per category.
     *
     * "Overspending" is relative to something, so a single month cannot answer it — without history
     * the prompt would be an opinion about one number, which is the generic thing being replaced.
     */
    fun trailingComparison(
        thisMonth: List<CategorySummary>,
        previous: Map<YearMonth, List<CategorySummary>>,
        month: YearMonth,
    ): String = buildString {
        val months = previous.keys.sorted()
        appendLine("This month (${month}):")
        if (thisMonth.isEmpty()) appendLine("- nothing spent")
        thisMonth.forEach { appendLine("- ${it.category}: ${rupees(it.amount)}") }
        appendLine()
        appendLine("Previous months, for comparison:")
        if (months.isEmpty()) {
            appendLine("- none. There is no earlier month of data at all.")
        } else {
            months.forEach { m ->
                val summaries = previous[m].orEmpty()
                appendLine("$m:")
                if (summaries.isEmpty()) appendLine("- nothing spent") else summaries.forEach { appendLine("- ${it.category}: ${rupees(it.amount)}") }
            }
            appendLine()
            appendLine("Average per category across those ${months.size} month(s):")
            averagePerCategory(previous).forEach { (category, average) -> appendLine("- $category: ${rupees(average)}") }
        }
        appendLine()
        appendLine("Months of history available before this one: ${months.size}")
    }

    /** The mean of each category across the previous months, counting a month with none as zero. */
    fun averagePerCategory(previous: Map<YearMonth, List<CategorySummary>>): Map<String, Double> {
        if (previous.isEmpty()) return emptyMap()
        val categories = previous.values.flatten().map { it.category }.toSet()
        return categories.associateWith { category ->
            val total = previous.values.sumOf { month -> month.firstOrNull { it.category == category }?.amount ?: 0.0 }
            roundPaisa(total / previous.size)
        }.toSortedMap()
    }

    /** The comparison, plus what the app knows that the person may not have added up. */
    fun whatToChange(
        comparison: String,
        subscriptions: List<SubscriptionEntity>,
        transactions: List<FullTransaction>,
        month: YearMonth,
        zone: ZoneId,
    ): String = buildString {
        append(comparison)
        appendLine()
        val live = subscriptions.filter { it.cancelledAt == null }
        appendLine("Subscriptions running (these renew whether or not they are used):")
        if (live.isEmpty()) {
            appendLine("- none")
        } else {
            live.forEach { appendLine("- ${it.name}: ${rupees(it.amount)} ${it.cycle.lowercase()}, ${it.category}") }
            appendLine("Total per month from subscriptions: ${rupees(live.sumOf { monthlyEquivalent(it) })}")
        }
        appendLine()
        appendLine("How this month was paid:")
        val byMethod = paymentMix(transactions, month, zone)
        if (byMethod.isEmpty()) appendLine("- nothing spent") else byMethod.forEach { (method, amount) -> appendLine("- $method: ${rupees(amount)}") }
    }

    /** A subscription's cost per month, so a yearly one can be compared with a monthly one. */
    fun monthlyEquivalent(subscription: SubscriptionEntity): Double = roundPaisa(
        when (subscription.cycle.uppercase()) {
            "WEEKLY" -> subscription.amount * 52.0 / 12.0
            "YEARLY" -> subscription.amount / 12.0
            else -> subscription.amount
        }
    )

    /** What was spent through each payment method this month, largest first. */
    fun paymentMix(transactions: List<FullTransaction>, month: YearMonth, zone: ZoneId): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (full in transactions) {
            val tx = full.transaction
            if (tx.type.equals("income", ignoreCase = true)) continue
            if (YearMonth.from(Instant.ofEpochMilli(tx.date).atZone(zone)) != month) continue
            val method = tx.paymentMethod.trim().ifEmpty { "Other" }
            totals[method] = (totals[method] ?: 0.0) + full.myPersonalShare
        }
        return totals.mapValues { roundPaisa(it.value) }.toList().sortedByDescending { it.second }.toMap()
    }

    /**
     * One group's whole life: who paid what, who owes whom, where it went, and how long it ran.
     *
     * The date span and per-day rate are what let an answer say "cheap for nine days" instead of
     * restating the total, which is the difference between this prompt and a receipt.
     */
    fun tripReview(
        details: GroupWithDetails,
        shares: List<MemberShare>,
        debts: List<SimplifiedDebt>,
        categories: List<CategorySummary>,
        settlements: List<SettlementEntity>,
        members: List<PersonEntity>,
        zone: ZoneId,
    ): String = buildString {
        val group = details.group
        appendLine("Group: ${group.name}")
        if (group.description.isNotBlank()) appendLine("About it: ${group.description}")
        appendLine("People: ${members.size + 1} (you, plus ${members.joinToString(", ") { it.name }})")
        appendLine("Total spent: ${rupees(details.totalExpenses)}")
        appendLine("Your share: ${rupees(details.myShare)}")
        appendLine("Expenses logged: ${details.transactionsCount}")
        if (group.isArchived) appendLine("This group is archived, so it is finished.")

        val dates = details.transactions.map { Instant.ofEpochMilli(it.transaction.date).atZone(zone).toLocalDate() }
        if (dates.isNotEmpty()) {
            val first = dates.min()
            val last = dates.max()
            val days = ChronoUnit.DAYS.between(first, last) + 1
            appendLine("Ran from $first to $last, which is $days day(s).")
            if (days > 0) appendLine("That is ${rupees(details.totalExpenses / days)} a day across everyone.")
        }

        appendLine()
        appendLine("Who spent what:")
        shares.forEach { appendLine("- ${it.name}: ${rupees(it.amount)}") }

        appendLine()
        appendLine("Where it went:")
        if (categories.isEmpty()) appendLine("- nothing yet") else categories.forEach { appendLine("- ${it.category}: ${rupees(it.amount)}") }

        appendLine()
        appendLine("Who owes whom now, after ${settlements.size} settlement(s):")
        if (debts.isEmpty()) appendLine("- nobody. Everyone is square.") else debts.forEach {
            appendLine("- ${it.fromPersonName} owes ${it.toPersonName} ${rupees(it.amount)}")
        }

        val biggest = details.transactions.maxByOrNull { it.transaction.amount }
        if (biggest != null) {
            appendLine()
            appendLine("Single biggest expense: ${biggest.transaction.merchant.ifBlank { biggest.transaction.category }} at ${rupees(biggest.transaction.amount)}")
        }
    }

    private fun entries(count: Int) = if (count == 1) "entry" else "entries"

    /** Whether [Overspending] and [WhatToChange] have enough history to say anything. */
    fun hasEnoughHistory(previous: Map<YearMonth, List<CategorySummary>>): Boolean =
        previous.values.any { it.isNotEmpty() }

    /** The months before [month] that a comparison should look at, newest last. */
    fun trailingMonths(month: YearMonth, earliest: YearMonth): List<YearMonth> =
        (TRAILING_MONTHS downTo 1)
            .map { month.minusMonths(it.toLong()) }
            .filter { !it.isBefore(earliest) }
}

/** A plain number for a prompt: no ₹, no grouping, two places only when there are paisa. */
private object Money {
    fun plain(amount: Double): String {
        val rounded = roundPaisa(amount)
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", rounded)
        }
    }
}
