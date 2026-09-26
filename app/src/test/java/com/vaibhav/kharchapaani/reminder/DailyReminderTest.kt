package com.vaibhav.kharchapaani.reminder

import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DailyReminderTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 27)

    private fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(date: Long, owner: String? = null) =
        TransactionEntity(type = "expense", amount = 50.0, category = "Food", merchant = "Chai", paymentMethod = "UPI", date = date, ownerProfileId = owner)

    @Test
    fun `on at 9 PM out of the box`() {
        val settings = ReminderSettings()
        assertTrue(settings.enabled)
        assertEquals(21 * 60, settings.minuteOfDay)
    }

    @Test
    fun `a corrupted stored time falls back to 9 PM`() {
        assertEquals(21 * 60, ReminderSettings.clampMinute(-5))
        assertEquals(21 * 60, ReminderSettings.clampMinute(24 * 60))
        assertEquals(7 * 60 + 30, ReminderSettings.clampMinute(7 * 60 + 30))
    }

    @Test
    fun `before the time, it's due later today`() {
        val now = ZonedDateTime.of(2026, 9, 27, 18, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(3), ReminderSchedule.delayUntilNext(now, 21 * 60))
    }

    @Test
    fun `at or after the time, it's due tomorrow, so a run on time never books itself again`() {
        val onTime = ZonedDateTime.of(2026, 9, 27, 21, 0, 0, 0, zone)
        assertEquals(Duration.ofHours(24), ReminderSchedule.delayUntilNext(onTime, 21 * 60))
        val late = ZonedDateTime.of(2026, 9, 27, 22, 30, 0, 0, zone)
        assertEquals(Duration.ofMinutes(22 * 60 + 30), ReminderSchedule.delayUntilNext(late, 21 * 60))
    }

    @Test
    fun `the label follows the phone's 12 or 24 hour setting`() {
        assertEquals("9:00 PM", ReminderSchedule.label(21 * 60, is24Hour = false, locale = Locale.US))
        assertEquals("21:00", ReminderSchedule.label(21 * 60, is24Hour = true, locale = Locale.US))
    }

    @Test
    fun `nothing logged today means remind`() {
        assertTrue(ReminderRule.nothingLoggedToday(emptyList(), today, zone))
        assertTrue(ReminderRule.nothingLoggedToday(listOf(tx(at(today.minusDays(1), 23))), today, zone))
    }

    @Test
    fun `an entry of your own today means no reminder`() {
        assertFalse(ReminderRule.nothingLoggedToday(listOf(tx(at(today, 0)), tx(at(today.minusDays(3), 12))), today, zone))
    }

    @Test
    fun `a groupmate's expense today doesn't count as you logging your day`() {
        assertTrue(ReminderRule.nothingLoggedToday(listOf(tx(at(today, 13), owner = "someone-else")), today, zone))
    }
}
