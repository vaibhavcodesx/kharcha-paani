package com.vaibhav.kharchapaani.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class MonthNamesTest {

    private val today = LocalDate.of(2026, 9, 15)
    private val en = Locale.ENGLISH

    @Test
    fun `the picker names the month and year`() {
        assertEquals("September 2026", MonthNames.title(YearMonth.of(2026, 9), en))
    }

    @Test
    fun `copy drops the year within this year`() {
        assertEquals("August", MonthNames.short(YearMonth.of(2026, 8), today, en))
        assertEquals("December 2025", MonthNames.short(YearMonth.of(2025, 12), today, en))
    }

    @Test
    fun `this month reads as this month`() {
        assertEquals("this month's", MonthNames.possessive(YearMonth.of(2026, 9), today, en))
        assertEquals("August's", MonthNames.possessive(YearMonth.of(2026, 8), today, en))
    }
}
