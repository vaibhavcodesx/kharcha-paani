package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's own reading of how people actually type: Hinglish words, "5 sau", "kal". */
class LocalHeuristicParserHinglishTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_792_925_400_000L

    @Test
    fun `sau and hazaar read as hundreds and thousands`() {
        assertEquals(500.0, LocalHeuristicParser.parse("auto 5 sau").amount, 0.001)
        assertEquals(2000.0, LocalHeuristicParser.parse("rent 2 hazaar").amount, 0.001)
        assertEquals(2500.0, LocalHeuristicParser.parse("sofa 2.5 hazaar").amount, 0.001)
        assertEquals(3000.0, LocalHeuristicParser.parse("fees 3 thousand").amount, 0.001)
    }

    @Test
    fun `dedh sau and dhai sau are a hundred and fifty and two fifty`() {
        assertEquals(150.0, LocalHeuristicParser.parse("dedh sau chai samosa").amount, 0.001)
        assertEquals(250.0, LocalHeuristicParser.parse("dhai sau auto").amount, 0.001)
    }

    @Test
    fun `filler words are not the merchant`() {
        assertEquals("Chai", LocalHeuristicParser.parse("chai 40 rupaye").merchant)
        assertEquals("Chai", LocalHeuristicParser.parse("40 rs ka chai").merchant)
        assertEquals("Chai", LocalHeuristicParser.parse("kal chai 40").merchant)
        assertEquals("Dinner", LocalHeuristicParser.parse("aaj dinner 1200 me").merchant)
    }

    @Test
    fun `yesterday, kal and parso move the date back and aaj leaves it`() {
        assertEquals(now - day, LocalHeuristicParser.parse("chai 40 yesterday", now = now).date)
        assertEquals(now - day, LocalHeuristicParser.parse("kal chai 40", now = now).date)
        assertEquals(now - 2 * day, LocalHeuristicParser.parse("parso auto 90", now = now).date)
        assertEquals(now - 2 * day, LocalHeuristicParser.parse("auto 90 day before yesterday", now = now).date)
        assertEquals(now, LocalHeuristicParser.parse("aaj chai 40", now = now).date)
        assertEquals(now, LocalHeuristicParser.parse("chai 40", now = now).date)
    }

    @Test
    fun `everyday Hinglish words find a category`() {
        assertEquals("Groceries", LocalHeuristicParser.parse("sabzi 120").category)
        assertEquals("Groceries", LocalHeuristicParser.parse("doodh 54").category)
        assertEquals("Food", LocalHeuristicParser.parse("samosa 30").category)
        assertEquals("Food", LocalHeuristicParser.parse("nashta 60").category)
        assertEquals("Rent", LocalHeuristicParser.parse("kiraya 12000").category)
        assertEquals("Bills", LocalHeuristicParser.parse("bijli bill 900").category)
    }

    @Test
    fun `mila is money in`() {
        assertEquals("income", LocalHeuristicParser.parse("salary mila 50000").type)
        assertEquals("income", LocalHeuristicParser.parse("dost se 500 mila").type)
    }
}
