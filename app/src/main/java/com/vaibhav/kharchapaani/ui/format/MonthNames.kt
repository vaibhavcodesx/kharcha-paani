package com.vaibhav.kharchapaani.ui.format

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** How a month is named in copy, relative to today. */
object MonthNames {

    /** "September 2026": the month picker's label. */
    fun title(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        "${month.month.getDisplayName(TextStyle.FULL, locale)} ${month.year}"

    /** "August", or "August 2025" once it's not this year: for "went in August". */
    fun short(month: YearMonth, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        if (month.year == today.year) month.month.getDisplayName(TextStyle.FULL, locale) else title(month, locale)

    /** "this month's" or "August's". */
    fun possessive(month: YearMonth, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        if (month == YearMonth.from(today)) "this month's" else "${short(month, today, locale)}'s"
}
