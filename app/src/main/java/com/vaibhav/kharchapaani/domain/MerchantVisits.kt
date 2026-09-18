package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.ActivityItem
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * How many times each merchant has come up this month.
 *
 * Feeds the "3×" chip on an activity row (backlog §5, Image 10). Scoped to the calendar month on
 * purpose: "you have paid Rapido 47 times" since install is trivia, "three times this month" is
 * something you might act on.
 */
object MerchantVisits {

    /** Merchant name, lowercased and trimmed, to the number of expenses this month under it. */
    fun thisMonth(items: List<ActivityItem>, month: YearMonth, zone: ZoneId): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (item in items) {
            val tx = (item as? ActivityItem.Transaction)?.fullTransaction?.transaction ?: continue
            if (tx.type.equals("income", ignoreCase = true)) continue
            val name = key(tx.merchant) ?: continue
            if (YearMonth.from(Instant.ofEpochMilli(tx.date).atZone(zone)) != month) continue
            counts[name] = (counts[name] ?: 0) + 1
        }
        return counts
    }

    /** The lookup key for a merchant name: "  Blue Tokai " and "blue tokai" are one merchant. */
    fun key(merchant: String): String? = merchant.trim().lowercase().takeIf { it.isNotEmpty() }
}
