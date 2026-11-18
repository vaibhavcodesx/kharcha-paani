package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class SubscriptionScheduleTest {

    @Test(timeout = 2000)
    fun `checkpoint bounds an ancient schedule and preserves month end and leap day`() {
        assertEquals(d("2026-09-30"), SubscriptionSchedule.nextDue(
            d("0001-01-31"), SubscriptionCycle.MONTHLY, after = d("2026-09-18")))
        assertEquals(d("2028-02-29"), SubscriptionSchedule.nextDue(
            d("0004-02-29"), SubscriptionCycle.YEARLY, after = d("2027-03-01")))
        assertEquals(listOf(d("2026-09-30")), SubscriptionSchedule.dueDates(
            d("0001-01-31"), SubscriptionCycle.MONTHLY, d("2026-09-30"), after = d("2026-09-18")))
        assertEquals(listOf(d("2028-02-29")), SubscriptionSchedule.dueDates(
            d("0004-02-29"), SubscriptionCycle.YEARLY, d("2028-02-29"), after = d("2027-03-01")))
        assertEquals(listOf(d("2026-09-22")), SubscriptionSchedule.dueDates(
            d("2026-09-01"), SubscriptionCycle.WEEKLY, d("2026-09-22"), after = d("2026-09-18")))
        assertEquals(emptyList<LocalDate>(), SubscriptionSchedule.dueDates(
            d("0001-01-31"), SubscriptionCycle.MONTHLY, d("2026-09-30"),
            cancelled = d("2026-09-01"), after = d("2026-09-18")))
    }

    private fun d(text: String) = LocalDate.parse(text)

    @Test
    fun `a monthly subscription is due once a month, up to today and no further`() {
        val due = SubscriptionSchedule.dueDates(d("2026-06-15"), SubscriptionCycle.MONTHLY, today = d("2026-09-18"))

        assertEquals(listOf(d("2026-06-15"), d("2026-07-15"), d("2026-08-15"), d("2026-09-15")), due)
    }

    @Test
    fun `nothing is created before it starts, and a start today counts`() {
        assertEquals(emptyList<LocalDate>(), SubscriptionSchedule.dueDates(d("2026-10-01"), SubscriptionCycle.MONTHLY, today = d("2026-09-18")))
        assertEquals(listOf(d("2026-09-18")), SubscriptionSchedule.dueDates(d("2026-09-18"), SubscriptionCycle.MONTHLY, today = d("2026-09-18")))
    }

    @Test
    fun `the 31st lands on the last day of a short month, then goes back`() {
        val due = SubscriptionSchedule.dueDates(d("2026-01-31"), SubscriptionCycle.MONTHLY, today = d("2026-04-30"))

        assertEquals(listOf(d("2026-01-31"), d("2026-02-28"), d("2026-03-31"), d("2026-04-30")), due)
    }

    @Test
    fun `weekly and yearly keep their own rhythm`() {
        assertEquals(
            listOf(d("2026-09-01"), d("2026-09-08"), d("2026-09-15")),
            SubscriptionSchedule.dueDates(d("2026-09-01"), SubscriptionCycle.WEEKLY, today = d("2026-09-18")),
        )
        assertEquals(
            listOf(d("2024-02-29"), d("2025-02-28"), d("2026-02-28")),
            SubscriptionSchedule.dueDates(d("2024-02-29"), SubscriptionCycle.YEARLY, today = d("2026-09-18")),
        )
    }

    @Test
    fun `cancelling stops the future and keeps what already went through`() {
        val due = SubscriptionSchedule.dueDates(d("2026-06-15"), SubscriptionCycle.MONTHLY, today = d("2026-09-18"), cancelled = d("2026-08-20"))

        assertEquals(listOf(d("2026-06-15"), d("2026-07-15"), d("2026-08-15")), due)
        assertNull(SubscriptionSchedule.nextDue(d("2026-06-15"), SubscriptionCycle.MONTHLY, after = d("2026-09-18"), cancelled = d("2026-08-20")))
    }

    @Test
    fun `the next charge is the first one still to come`() {
        assertEquals(d("2026-10-15"), SubscriptionSchedule.nextDue(d("2026-06-15"), SubscriptionCycle.MONTHLY, after = d("2026-09-18")))
        // Due today already counts as charged, so the next one is next month.
        assertEquals(d("2026-10-15"), SubscriptionSchedule.nextDue(d("2026-06-15"), SubscriptionCycle.MONTHLY, after = d("2026-09-15")))
        assertEquals(d("2026-11-01"), SubscriptionSchedule.nextDue(d("2026-11-01"), SubscriptionCycle.MONTHLY, after = d("2026-09-18")))
    }
}
