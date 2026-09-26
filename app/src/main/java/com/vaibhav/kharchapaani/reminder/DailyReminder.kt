package com.vaibhav.kharchapaani.reminder

import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The daily "nothing logged today" reminder: whether it's on and when it comes.
 *
 * On by default, at 9 PM — late enough that the day's spending has happened, early enough to still
 * remember it. On Android 13+ it can't show until notifications are allowed, and the app asks for
 * that once, only after the person has logged something of their own (MainActivity), never on a
 * first launch before they've seen what the app is.
 *
 * Kept on this phone rather than per account: it's about whoever is signed in here, and the check
 * itself reads the signed-in account's ledger.
 */
data class ReminderSettings(
    val enabled: Boolean = true,
    /** Minutes after midnight, local time. */
    val minuteOfDay: Int = DEFAULT_MINUTE_OF_DAY,
) {
    val time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)

    companion object {
        const val DEFAULT_MINUTE_OF_DAY = 21 * 60

        /** Anything out of range (a corrupted preference) falls back to the default rather than crashing. */
        fun clampMinute(minute: Int): Int = minute.takeIf { it in 0 until 24 * 60 } ?: DEFAULT_MINUTE_OF_DAY
    }
}

object ReminderSchedule {

    /**
     * How long from [now] until the next time the reminder is due. Exactly at the time counts as
     * "now" is past it, so a run that fires on time schedules tomorrow, not itself again.
     */
    fun delayUntilNext(now: ZonedDateTime, minuteOfDay: Int): Duration {
        val minute = ReminderSettings.clampMinute(minuteOfDay)
        val today = now.toLocalDate().atTime(minute / 60, minute % 60).atZone(now.zone)
        // atZone resolves a time skipped by a clock change to the next valid instant, so this never
        // lands on a time that doesn't exist.
        val next = if (today.isAfter(now)) today else today.plusDays(1)
        return Duration.between(now, next)
    }

    /** The time as the settings row says it: "9:00 PM", or "21:00" on a phone set to 24-hour time. */
    fun label(minuteOfDay: Int, is24Hour: Boolean = false, locale: Locale = Locale.getDefault()): String {
        val minute = ReminderSettings.clampMinute(minuteOfDay)
        val pattern = if (is24Hour) "HH:mm" else "h:mm a"
        return LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
}

object ReminderRule {

    /**
     * True when nothing of this account's own is dated [today] — the same test the streak uses
     * (MonthAnalytics.dailyStreak: own entries, by the entry's date). A groupmate's expense landing
     * in a shared group doesn't count as the person having logged their day.
     */
    fun nothingLoggedToday(transactions: List<TransactionEntity>, today: LocalDate, zone: ZoneId): Boolean =
        transactions.none { it.isOwnedByMe && Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() == today }
}
