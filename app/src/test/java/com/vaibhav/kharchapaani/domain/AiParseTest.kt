package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AiParseTest {

    @Test
    fun `grouping commas are digits, not the end of the number`() {
        // The old parser read "1,299" as ₹1: the comma ended the first run of digits.
        assertEquals(1299.0, AiParse.amountIn("dinner 1,299 gpay")!!, 0.001)
        assertEquals(100000.0, AiParse.amountIn("bonus 1,00,000")!!, 0.001)
    }

    @Test
    fun `shorthand scales`() {
        assertEquals(1200.0, AiParse.amountIn("₹1.2k headphones")!!, 0.001)
        assertEquals(12000.0, AiParse.amountIn("rent 12k")!!, 0.001)
        assertEquals(200000.0, AiParse.amountIn("car 2 lakh")!!, 0.001)
    }

    @Test
    fun `a count is not the price`() {
        // The first number used to win: "2 coffees 300" came out as ₹2.
        assertEquals(300.0, AiParse.amountIn("2 coffees 300")!!, 0.001)
        assertEquals(450.0, AiParse.amountIn("table for 4, rs 450")!!, 0.001)
    }

    @Test
    fun `no number, no amount`() {
        assertNull(AiParse.amountIn("coffee with Rohan"))
    }

    @Test
    fun `a model's amount field survives being a string`() {
        assertEquals(1299.0, AiParse.amountField("₹1,299.00"), 0.001)
        assertEquals(370.0, AiParse.amountField(370), 0.001)
        assertEquals(0.0, AiParse.amountField(Double.NaN), 0.001)
        assertEquals(0.0, AiParse.amountField(null), 0.001)
        assertEquals(250.0, AiParse.amountField(-250.0), 0.001)
    }

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 9, 24, 21, 30).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `a date keeps the current time of day, a date-time keeps its own`() {
        val yesterday = AiParse.dateOrNow("2026-09-23", now, zone)
        assertEquals(LocalDateTime.of(2026, 9, 23, 21, 30), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(yesterday), zone))

        val paid = AiParse.dateOrNow("2026-09-20T13:05", now, zone)
        assertEquals(LocalDateTime.of(2026, 9, 20, 13, 5), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(paid), zone))
    }

    @Test
    fun `a date that can't be right falls back to now`() {
        assertEquals(now, AiParse.dateOrNow("", now, zone))
        assertEquals(now, AiParse.dateOrNow("24/09/2026", now, zone))
        assertEquals(now, AiParse.dateOrNow("2027-09-24", now, zone)) // a misread year, in the future
        assertEquals(now, AiParse.dateOrNow("2019-01-01", now, zone)) // a guess, years back
    }
}
