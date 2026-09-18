package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The daily cap (owner decision, 2026-09-19: 20). A backstop against a loop or a stuck retry rather
 * than a budget — the key is on a free tier at this volume.
 */
class AiUsageTest {

    private val today = LocalDate.of(2026, 9, 19)

    @Test
    fun `a fresh install has the whole day's worth`() {
        assertEquals(AI_DAILY_CAP, AiUsage.none.remaining(today))
        assertTrue(AiUsage.none.canAsk(today))
    }

    @Test
    fun `each answer costs one`() {
        var usage = AiUsage.none
        repeat(3) { usage = usage.afterAsking(today) }

        assertEquals(3, usage.used)
        assertEquals(AI_DAILY_CAP - 3, usage.remaining(today))
    }

    @Test
    fun `the cap stops at the cap`() {
        var usage = AiUsage.none
        repeat(AI_DAILY_CAP) { usage = usage.afterAsking(today) }

        assertEquals(0, usage.remaining(today))
        assertFalse(usage.canAsk(today))
    }

    @Test
    fun `remaining never goes below zero, however it got over`() {
        assertEquals(0, AiUsage(today, AI_DAILY_CAP + 5).remaining(today))
    }

    @Test
    fun `yesterday's count doesn't carry into today`() {
        // Stored as day + count rather than a counter and a scheduled reset, so it resets by itself.
        val spent = AiUsage(today.minusDays(1), AI_DAILY_CAP)

        assertEquals(AI_DAILY_CAP, spent.remaining(today))
        assertTrue(spent.canAsk(today))
    }

    @Test
    fun `asking on a new day starts that day at one`() {
        val rolled = AiUsage(today.minusDays(1), 17).afterAsking(today)

        assertEquals(today, rolled.day)
        assertEquals(1, rolled.used)
    }

    @Test
    fun `a clock that went backwards doesn't hand out a free day`() {
        // The stored day is in the future; that is not today, so it rolls over rather than
        // letting the count keep climbing against a date nobody is on.
        val future = AiUsage(today.plusDays(1), 20)

        assertEquals(AI_DAILY_CAP, future.remaining(today))
    }
}
