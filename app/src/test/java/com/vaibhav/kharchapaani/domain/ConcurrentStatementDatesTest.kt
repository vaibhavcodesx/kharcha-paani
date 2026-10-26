package com.vaibhav.kharchapaani.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConcurrentStatementDatesTest {
    @Test fun `overlapping statement imports preserve every parsed day and display label`() {
        val pool = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val patterns = listOf("yyyy-MM-dd", "dd/MM/yyyy", "dd-MM-yyyy", "dd MMM yyyy")
        try {
            val tasks = (0 until 8).map { worker -> pool.submit {
                start.await()
                repeat(500) { index ->
                    val day = LocalDate.of(2020 + worker, 1 + index % 12, 1 + index % 27)
                    val source = day.format(DateTimeFormatter.ofPattern(patterns[(worker + index) % patterns.size], Locale.US))
                    val (timestamp, display, month) = LocalStatementParser.parseDateToTimestamp(source)
                    assertEquals("parsed day for $source", day, Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate())
                    assertEquals("display for $source", day.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)), display)
                    assertEquals("month for $source", day.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)), month)
                    assertTrue("readsAsDate for $source", LocalStatementParser.readsAsDate(source))
                }
            } }
            start.countDown()
            tasks.forEach { it.get(60, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            pool.shutdownNow()
        }
    }
}
