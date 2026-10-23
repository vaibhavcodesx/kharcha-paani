package com.vaibhav.kharchapaani.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class TodayTest {

    private val india = ZoneId.of("Asia/Kolkata")

    @Test
    fun `the date moves on within a minute of midnight, and only once`() = runTest {
        var now = Instant.parse("2026-10-31T18:29:00Z") // 23:59 in India
        val seen = mutableListOf<LocalDate>()
        val job = launch { Today.dates(zone = { india }, now = { now }).collect { seen += it } }
        runCurrent()

        // Still the 31st a few checks later: nothing new is sent.
        advanceTimeBy(Today.CHECK_EVERY_MILLIS * 3 / 2)
        now = Instant.parse("2026-10-31T18:30:05Z") // 00:00:05 on the 1st
        advanceTimeBy(Today.CHECK_EVERY_MILLIS)
        runCurrent()
        job.cancel()

        assertEquals(listOf(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 1)), seen)
    }

    @Test
    fun `the date is the phone's own zone's, not UTC's`() = runTest {
        // 20:00 UTC on the 31st is already the 1st in India.
        val first = mutableListOf<LocalDate>()
        val job = launch { Today.dates(zone = { india }, now = { Instant.parse("2026-10-31T20:00:00Z") }).collect { first += it } }
        runCurrent()
        job.cancel()

        assertEquals(listOf(LocalDate.of(2026, 11, 1)), first)
    }
}
