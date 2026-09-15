package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.ActivityItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One day of Recent Activity, as Home lists it. */
data class ActivityDay(
    val date: LocalDate,
    val label: String,
    val items: List<ActivityItem>,
    /** What I spent that day: my share of each expense. Income and settlements aren't spending. */
    val spent: Double,
)

object ActivityDays {

    /**
     * Groups an already-sorted feed (newest first) into days in [zone], keeping the order.
     * Labels: "Today", "Yesterday", "Mon, 12 Sep", and "Mon, 12 Sep 2025" outside the current year.
     */
    fun group(items: List<ActivityItem>, today: LocalDate, zone: ZoneId, locale: Locale = Locale.getDefault()): List<ActivityDay> {
        val thisYear = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
        val otherYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale)
        return items
            .groupBy { Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() }
            .map { (date, dayItems) ->
                ActivityDay(
                    date = date,
                    label = when {
                        date == today -> "Today"
                        date == today.minusDays(1) -> "Yesterday"
                        date.year == today.year -> date.format(thisYear)
                        else -> date.format(otherYear)
                    },
                    items = dayItems,
                    spent = roundPaisa(
                        dayItems.filterIsInstance<ActivityItem.Transaction>()
                            .filter { it.fullTransaction.transaction.type == "expense" }
                            .sumOf { it.fullTransaction.myPersonalShare }
                    ),
                )
            }
    }
}
