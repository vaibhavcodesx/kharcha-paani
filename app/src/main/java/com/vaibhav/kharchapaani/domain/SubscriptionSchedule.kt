package com.vaibhav.kharchapaani.domain

import java.time.LocalDate

/**
 * A subscription's dates (start, charged-through, cancelled) are calendar days, stored as epoch
 * milliseconds.
 *
 * They used to be stored as local midnight in the phone's zone at the time and read back in its zone
 * now. After a flight west, midnight in India read as the evening before, so the charge already made
 * that day looked missing and was made again (correctness audit, 2026-10-24).
 *
 * Now a day is written as midnight UTC, and read by rounding to the nearest UTC midnight. That reads
 * the new values exactly, and the old ones too: local midnight anywhere between UTC−12 and UTC+12 is
 * within half a day of that day's UTC midnight. India, at +5:30, is well inside. (+13 and +14, a
 * few Pacific islands and New Zealand's summer time, round to the day before; there's no telling
 * from the number alone.) No migration: rows are re-written the new way as they're charged.
 */
object CalendarDay {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    fun toMillis(date: LocalDate): Long = date.toEpochDay() * DAY_MILLIS

    fun fromMillis(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis + DAY_MILLIS / 2, DAY_MILLIS))
}

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
