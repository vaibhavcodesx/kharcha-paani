package com.vaibhav.kharchapaani.ui.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** The listing fixture's dates stay in the capture's month, however early in it the capture runs. */
class StoreFixtureDatesTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(text: String) = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun month(millis: Long) = YearMonth.from(Instant.ofEpochMilli(millis).atZone(zone))

    // Every (daysAgo, hour, minute) the fixture seeds.
    private val seeded = listOf(Triple(2L, 21, 0), Triple(0L, 9, 10), Triple(1L, 18, 30), Triple(1L, 8, 45), Triple(0L, 8, 0))

    @Test
    fun `two minutes into the 1st, nothing lands in the month before`() {
        // #210 review: capped to "five minutes before now", these all fell into November.
        val now = at("2026-12-01T00:02:00")
        seeded.forEach { (daysAgo, hour, minute) ->
            assertEquals("stamp($daysAgo, $hour, $minute)", YearMonth.of(2026, 12), month(StoreFixture.stamp(daysAgo, hour, minute, now, zone)))
        }
        // The most recent still sorts first.
        assertTrue(StoreFixture.stamp(0, 9, 10, now, zone) > StoreFixture.stamp(2, 21, 0, now, zone))
    }

    @Test
    fun `mid-month, entries keep their days and times`() {
        val now = at("2026-11-15T12:00:00")
        assertEquals(at("2026-11-14T18:30:00"), StoreFixture.stamp(1, 18, 30, now, zone))
        assertEquals(at("2026-11-13T21:00:00"), StoreFixture.stamp(2, 21, 0, now, zone))
        // Later today than now: pulled back to before now, still today.
        assertEquals(at("2026-11-15T11:55:00"), StoreFixture.stamp(0, 13, 0, now, zone))
    }

    @Test
    fun `two minutes after midnight, today entries stay today in UTC and Asia Kolkata`() {
        listOf(ZoneId.of("UTC"), ZoneId.of("Asia/Kolkata")).forEach { z ->
            val now = LocalDateTime.parse("2026-11-19T00:02:00").atZone(z).toInstant().toEpochMilli()
            val expectedToday = LocalDateTime.parse("2026-11-19T00:02:00").atZone(z).toLocalDate()

            val stamp0 = StoreFixture.stamp(0, 9, 10, now, z)
            val stamp1 = StoreFixture.stamp(0, 8, 0, now, z)

            assertEquals("Zone $z: stamp(0, 9, 10) day", expectedToday, Instant.ofEpochMilli(stamp0).atZone(z).toLocalDate())
            assertEquals("Zone $z: stamp(0, 8, 0) day", expectedToday, Instant.ofEpochMilli(stamp1).atZone(z).toLocalDate())
            assertTrue("Zone $z: more recent entry sorts later", stamp0 > stamp1)
        }
    }
}
