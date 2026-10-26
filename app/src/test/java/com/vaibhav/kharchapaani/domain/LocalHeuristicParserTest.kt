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
    fun `haircuts and the gym are self-care, juice a beverage, maggi packaged goods`() {
        assertEquals("Self-care", LocalHeuristicParser.parse("haircut 300").category)
        assertEquals("Self-care", LocalHeuristicParser.parse("gym 1500").category)
        assertEquals("Beverages", LocalHeuristicParser.parse("juice 60").category)
        assertEquals("Packaged goods", LocalHeuristicParser.parse("maggi 14").category)
        // A doctor is still Health.
        assertEquals("Health", LocalHeuristicParser.parse("doctor 500").category)
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
    fun `a friend saved with a full name is found by the first name`() {
        val result = LocalHeuristicParser.parse("auto 120 with ankit", knownFriends = listOf("Ankit Shah", "Rohan"))

        assertEquals(listOf("Ankit"), result.splitWithPersonNames)
    }

    @Test
    fun `a full name said isn't found again by its first name`() {
        val result = LocalHeuristicParser.parse("lunch 600 rahul mehta", knownFriends = listOf("Rahul Mehta", "Rahul Kumar"))

        assertEquals(listOf("Rahul Mehta"), result.splitWithPersonNames)
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
