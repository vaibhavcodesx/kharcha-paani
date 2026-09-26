package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone's own guess, used offline and whenever the AI can't be reached. */
class LocalHeuristicParserTest {

    @Test
    fun `keywords match whole words`() {
        // "vi" (the telecom) inside "movie" put it under Bills; "tea" matched "team".
        assertEquals("Entertainment", LocalHeuristicParser.parse("movie 400").category)
        assertEquals("Team Payment", LocalHeuristicParser.parse("team hosting 900").category)
    }

    @Test
    fun `the specific payment method wins over the general one`() {
        assertEquals("Credit Card", LocalHeuristicParser.parse("shoes 2,999 credit card").paymentMethod)
        assertEquals("GPay", LocalHeuristicParser.parse("chai 20 google pay").paymentMethod)
    }

    @Test
    fun `amounts with grouping commas read in full`() {
        assertEquals(1299.0, LocalHeuristicParser.parse("dinner 1,299 gpay").amount, 0.001)
    }

    @Test
    fun `known friends match as whole names`() {
        val result = LocalHeuristicParser.parse("lunch with rajesh 600", knownFriends = listOf("Raj", "Rajesh"))

        assertEquals(listOf("Rajesh"), result.splitWithPersonNames)
    }

    @Test
    fun `split names are separated on the word and, not the letters`() {
        // "Anand" used to come apart as "An" and "".
        val result = LocalHeuristicParser.parse("pizza 900 split with anand and priya")

        assertEquals(listOf("Anand", "Priya"), result.splitWithPersonNames)
    }

    @Test
    fun `no payment method said means none, not GPay`() {
        assertEquals("", LocalHeuristicParser.parse("chai 20").paymentMethod)
    }
}
