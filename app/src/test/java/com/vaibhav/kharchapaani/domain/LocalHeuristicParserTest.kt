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

    data class GoldenEntry(
        val input: String,
        val expectedType: String,
        val expectedAmount: Double,
        val expectedCategory: String,
        val expectedPaymentMethod: String,
        val knownFriends: List<String> = emptyList(),
        val expectedSplits: List<String> = emptyList(),
    )

    @Test
    fun `golden table parses Indian and Hinglish quick entries accurately`() {
        val goldenTable = listOf(
            GoldenEntry("chai 20 cash", "expense", 20.0, "Food", "Cash"),
            GoldenEntry("lassi 50 paytm", "expense", 50.0, "Beverages", "Paytm"),
            GoldenEntry("kurkure and chips 30", "expense", 30.0, "Packaged goods", ""),
            GoldenEntry("maggi and cold drink 80", "expense", 80.0, "Beverages", ""),
            GoldenEntry("biryani 350 gpay with rahul", "expense", 350.0, "Food", "GPay", listOf("Rahul"), listOf("Rahul")),
            GoldenEntry("auto 120 phonepe", "expense", 120.0, "Transport", "PhonePe"),
            GoldenEntry("petrol 500 card", "expense", 500.0, "Transport", "Card"),
            GoldenEntry("bhai ko rent diya 15k bank transfer", "expense", 15000.0, "Rent", "Bank Transfer"),
            GoldenEntry("flat maintenance 2.5k", "expense", 2500.0, "Rent", ""),
            GoldenEntry("salary credited 75k", "income", 75000.0, "Income (Client)", ""),
            GoldenEntry("freelance client paid 25000 neft", "income", 25000.0, "Income (Client)", "Bank Transfer"),
            GoldenEntry("cashback received 150 upi", "income", 150.0, "Income (Client)", "GPay"),
            GoldenEntry("doctor clinic fee 800 gpay", "expense", 800.0, "Health", "GPay"),
            GoldenEntry("dawa pharmacy 320", "expense", 320.0, "Health", ""),
            GoldenEntry("blinkit grocery 650", "expense", 650.0, "Shopping", ""),
            GoldenEntry("wifi recharge 999 airtel", "expense", 999.0, "Bills", ""),
            GoldenEntry("netflix subscription 649 credit card", "expense", 649.0, "Bills", "Credit Card"),
            GoldenEntry("pvr movie ticket 850 split with vikram and anjali", "expense", 850.0, "Entertainment", "", emptyList(), listOf("Vikram", "Anjali")),
            GoldenEntry("chai 40 cash", "expense", 40.0, "Food", "Cash"),
            GoldenEntry("rapido bike 45 upi", "expense", 45.0, "Transport", "GPay"),
            GoldenEntry("haircut salon 250", "expense", 250.0, "Self-care", ""),
            GoldenEntry("gym membership 2k gpay", "expense", 2000.0, "Self-care", "GPay"),
            GoldenEntry("stipend got money 10k imps", "income", 10000.0, "Income (Client)", "Bank Transfer"),
            GoldenEntry("1 lakh bonus credited", "income", 100000.0, "Income (Client)", ""),
            GoldenEntry("swiggy dinner 450 split with aman", "expense", 450.0, "Food", "", listOf("Aman"), listOf("Aman")),
            GoldenEntry("subway lunch 280 gpay", "expense", 280.0, "Food", "GPay"),
            GoldenEntry("uber cab 320", "expense", 320.0, "Transport", ""),
            GoldenEntry("zomato pizza 600 split with priya", "expense", 600.0, "Food", "", listOf("Priya"), listOf("Priya")),
            GoldenEntry("smoothie 120 phonepe", "expense", 120.0, "Beverages", "PhonePe"),
            GoldenEntry("amazon shopping 1499 card", "expense", 1499.0, "Shopping", "Card")
        )

        for (entry in goldenTable) {
            val result = LocalHeuristicParser.parse(entry.input, entry.knownFriends)
            assertEquals("Type mismatch for '${entry.input}'", entry.expectedType, result.type)
            assertEquals("Amount mismatch for '${entry.input}'", entry.expectedAmount, result.amount, 0.001)
            assertEquals("Category mismatch for '${entry.input}'", entry.expectedCategory, result.category)
            assertEquals("Payment method mismatch for '${entry.input}'", entry.expectedPaymentMethod, result.paymentMethod)
            assertEquals("Split names mismatch for '${entry.input}'", entry.expectedSplits, result.splitWithPersonNames)
        }
    }
}
