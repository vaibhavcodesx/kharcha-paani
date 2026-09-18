package com.vaibhav.kharchapaani.domain

import java.time.LocalDate

/** How often a subscription is charged. */
enum class SubscriptionCycle { WEEKLY, MONTHLY, YEARLY }

/**
 * When a subscription falls due. Entries are only ever created for dates that have arrived: a
 * subscription is a rule, and the ledger holds what it has actually charged (backlog item 8,
 * approved 2026-09-15).
 */
object SubscriptionSchedule {

    /** Every due date from [start] up to and including [today], oldest first. Never the future. */
    fun dueDates(start: LocalDate, cycle: SubscriptionCycle, today: LocalDate, cancelled: LocalDate? = null): List<LocalDate> {
        if (start.isAfter(today)) return emptyList()
        val last = if (cancelled != null && cancelled.isBefore(today)) cancelled else today
        val dates = mutableListOf<LocalDate>()
        var occurrence = 0L
        while (true) {
            val date = advance(start, cycle, occurrence)
            if (date.isAfter(last)) break
            dates += date
            occurrence++
        }
        return dates
    }

    /** The first due date after [after], for showing what's coming. Null once cancelled. */
    fun nextDue(start: LocalDate, cycle: SubscriptionCycle, after: LocalDate, cancelled: LocalDate? = null): LocalDate? {
        if (cancelled != null && !cancelled.isAfter(after)) return null
        var occurrence = 0L
        while (true) {
            val date = advance(start, cycle, occurrence)
            if (date.isAfter(after)) return if (cancelled != null && date.isAfter(cancelled)) null else date
            occurrence++
        }
    }

    /**
     * The nth charge after [start]. A monthly one keeps its day of the month where the month is long
     * enough: the 31st becomes the 28th in February, then goes back to the 31st in March.
     */
    private fun advance(start: LocalDate, cycle: SubscriptionCycle, occurrence: Long): LocalDate = when (cycle) {
        SubscriptionCycle.WEEKLY -> start.plusWeeks(occurrence)
        SubscriptionCycle.MONTHLY -> start.plusMonths(occurrence).let { moved ->
            moved.withDayOfMonth(minOf(start.dayOfMonth, moved.lengthOfMonth()))
        }
        SubscriptionCycle.YEARLY -> start.plusYears(occurrence).let { moved ->
            moved.withDayOfMonth(minOf(start.dayOfMonth, moved.lengthOfMonth()))
        }
    }
}
