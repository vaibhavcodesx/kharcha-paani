package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CalendarDayTest {

    private val days = listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 10, 24), LocalDate.of(1999, 12, 31))

    @Test
    fun `a day written now reads back as the same day`() {
        days.forEach { assertEquals(it, CalendarDay.fromMillis(CalendarDay.toMillis(it))) }
    }

    @Test
    fun `a day written the old way, as local midnight anywhere from UTC-11 to UTC+12, reads back as itself`() {
        for (hours in -11..12) {
            val offset = ZoneOffset.ofHours(hours)
            days.forEach { day ->
                assertEquals("UTC$hours", day, CalendarDay.fromMillis(day.atStartOfDay(offset).toInstant().toEpochMilli()))
            }
        }
        // India's half hour.
        val india = ZoneOffset.ofHoursMinutes(5, 30)
        days.forEach { assertEquals(it, CalendarDay.fromMillis(it.atStartOfDay(india).toInstant().toEpochMilli())) }
    }
}
