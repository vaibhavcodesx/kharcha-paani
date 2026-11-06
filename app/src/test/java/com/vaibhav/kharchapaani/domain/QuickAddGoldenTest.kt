package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Golden tests for the quick-add heuristic parser after #238 (W3).
 * Asserts today's exact parser behavior across 40 Indian and Hinglish test cases,
 * pinning amounts, categories, merchants, payment methods, relative dates, and split names.
 */
class QuickAddGoldenTest {

    private val dayMillis = 24L * 60 * 60 * 1000L
    private val fixedNow = 1_792_925_400_000L

    data class TestCase(
        val input: String,
        val expectedType: String,
        val expectedAmount: Double,
        val expectedMerchant: String,
        val expectedCategory: String,
        val expectedPaymentMethod: String,
        val expectedDaysBack: Int = 0,
        val knownFriends: List<String> = emptyList(),
        val expectedSplits: List<String> = emptyList(),
    )

    private val testCases = listOf(
        // Required cases from W3 prompt
        TestCase("5 sau", "expense", 500.0, "Expense", "Other", "", expectedDaysBack = 0),
        TestCase("dedh sau", "expense", 150.0, "Expense", "Other", "", expectedDaysBack = 0),
        TestCase("kal", "expense", 0.0, "Expense", "Other", "", expectedDaysBack = 1),
        TestCase("parso", "expense", 0.0, "Expense", "Other", "", expectedDaysBack = 2),
        TestCase("chai 40 cash", "expense", 40.0, "Chai", "Food", "Cash", expectedDaysBack = 0),
        TestCase("dinner 1200 with Rohan", "expense", 1200.0, "Dinner", "Food", "", expectedDaysBack = 0, knownFriends = listOf("Rohan"), expectedSplits = listOf("Rohan")),

        // Hinglish numbers & shorthands
        TestCase("auto 5 sau", "expense", 500.0, "Auto", "Transport", "", expectedDaysBack = 0),
        TestCase("rent 2 hazaar", "expense", 2000.0, "Rent", "Rent", "", expectedDaysBack = 0),
        TestCase("sofa 2.5 hazaar", "expense", 2500.0, "Sofa", "Other", "", expectedDaysBack = 0),
        TestCase("fees 3 thousand", "expense", 3000.0, "Fees", "Other", "", expectedDaysBack = 0),
        TestCase("dedh sau chai samosa", "expense", 150.0, "Chai Samosa", "Food", "", expectedDaysBack = 0),
        TestCase("dhai sau auto", "expense", 250.0, "Auto", "Transport", "", expectedDaysBack = 0),
        TestCase("dhai sau nashta", "expense", 250.0, "Nashta", "Food", "", expectedDaysBack = 0),

        // Relative dates: kal, parso, yesterday, day before yesterday, aaj
        TestCase("kal chai 40", "expense", 40.0, "Chai", "Food", "", expectedDaysBack = 1),
        TestCase("parso auto 90", "expense", 90.0, "Auto", "Transport", "", expectedDaysBack = 2),
        TestCase("chai 40 yesterday", "expense", 40.0, "Chai", "Food", "", expectedDaysBack = 1),
        TestCase("auto 90 day before yesterday", "expense", 90.0, "Auto", "Transport", "", expectedDaysBack = 2),
        TestCase("aaj dinner 1200 me", "expense", 1200.0, "Dinner", "Food", "", expectedDaysBack = 0),

        // Mixed Hindi and English with filler words
        TestCase("40 rs ka chai", "expense", 40.0, "Chai", "Food", "", expectedDaysBack = 0),
        TestCase("chai 40 rupaye", "expense", 40.0, "Chai", "Food", "", expectedDaysBack = 0),
        TestCase("sabzi 120 cash", "expense", 120.0, "Sabzi", "Groceries", "Cash", expectedDaysBack = 0),
        TestCase("doodh 54 paytm", "expense", 54.0, "Doodh", "Groceries", "Paytm", expectedDaysBack = 0),
        TestCase("samosa 30 cash", "expense", 30.0, "Samosa", "Food", "Cash", expectedDaysBack = 0),
        TestCase("kiraya 12000 bank transfer", "expense", 12000.0, "Kiraya", "Rent", "Bank Transfer", expectedDaysBack = 0),
        TestCase("bijli bill 900 gpay", "expense", 900.0, "Bijli Bill", "Bills", "GPay", expectedDaysBack = 0),
        TestCase("pani puri 50 cash", "expense", 50.0, "Pani Puri", "Food", "Cash", expectedDaysBack = 0),
        TestCase("golgappe 40", "expense", 40.0, "Golgappe", "Food", "", expectedDaysBack = 0),
        TestCase("thali 150 google pay", "expense", 150.0, "Thali", "Food", "GPay", expectedDaysBack = 0),
        TestCase("dosa 80 phonepe", "expense", 80.0, "Dosa", "Food", "PhonePe", expectedDaysBack = 0),

        // Income keywords & mila
        TestCase("salary mila 50000", "income", 50000.0, "Salary", "Income (Client)", "", expectedDaysBack = 0),
        TestCase("dost se 500 mila", "income", 500.0, "Dost", "Income (Client)", "", expectedDaysBack = 0),
        TestCase("cashback mila 100", "income", 100.0, "Cashback", "Income (Client)", "", expectedDaysBack = 0),
        TestCase("freelance payment received 15k", "income", 15000.0, "Freelance Payment", "Income (Client)", "", expectedDaysBack = 0),

        // Splits and friend recognition
        TestCase("biryani 800 with Rohan and Priya", "expense", 800.0, "Biryani", "Food", "", expectedDaysBack = 0, knownFriends = listOf("Rohan", "Priya"), expectedSplits = listOf("Rohan", "Priya")),
        TestCase("pizza 600 split with Vikram and Anjali", "expense", 600.0, "Pizza", "Food", "", expectedDaysBack = 0, expectedSplits = listOf("Vikram", "Anjali")),
        TestCase("lunch 1500 with Anand", "expense", 1500.0, "Lunch", "Food", "", expectedDaysBack = 0, knownFriends = listOf("Anand"), expectedSplits = listOf("Anand")),
        TestCase("cab 450 split with Ankit", "expense", 450.0, "Cab", "Transport", "", expectedDaysBack = 0, knownFriends = listOf("Ankit Shah"), expectedSplits = listOf("Ankit")),

        // Multi-item categories & everyday purchases
        TestCase("kurkure and chips 30 cash", "expense", 30.0, "Kurkure Chips", "Packaged goods", "Cash", expectedDaysBack = 0),
        TestCase("doctor clinic 500 card", "expense", 500.0, "Doctor Clinic", "Health", "Card", expectedDaysBack = 0),
        TestCase("wifi recharge 999 airtel", "expense", 999.0, "Wifi Recharge Airtel", "Bills", "", expectedDaysBack = 0)
    )

    @Test
    fun `quick-add golden test suite validates 40 cases pinning today's behavior`() {
        assertEquals("Golden test suite must have 25 or more cases", 40, testCases.size)

        for (tc in testCases) {
            val result = LocalHeuristicParser.parse(tc.input, knownFriends = tc.knownFriends, now = fixedNow)

            assertEquals("Type mismatch for '${tc.input}'", tc.expectedType, result.type)
            assertEquals("Amount mismatch for '${tc.input}'", tc.expectedAmount, result.amount, 0.001)
            assertEquals("Merchant mismatch for '${tc.input}'", tc.expectedMerchant, result.merchant)
            assertEquals("Category mismatch for '${tc.input}'", tc.expectedCategory, result.category)
            assertEquals("Payment method mismatch for '${tc.input}'", tc.expectedPaymentMethod, result.paymentMethod)
            assertEquals("Date mismatch for '${tc.input}'", fixedNow - tc.expectedDaysBack * dayMillis, result.date)
            assertEquals("Split names mismatch for '${tc.input}'", tc.expectedSplits, result.splitWithPersonNames)
        }
    }

    @Test
    fun `quick-add merchant stays clean of split friend names and multi-word payment methods (W11)`() {
        // Multi-word payment methods stay out of merchant
        assertEquals("Thali", LocalHeuristicParser.parse("thali 150 google pay").merchant)
        assertEquals("Kiraya", LocalHeuristicParser.parse("kiraya 12000 bank transfer").merchant)
        assertEquals("Shoes", LocalHeuristicParser.parse("shoes 2999 credit card").merchant)
        assertEquals("Groceries", LocalHeuristicParser.parse("groceries 850 debit card").merchant)

        // Split friend names stay out of merchant
        assertEquals("Dinner", LocalHeuristicParser.parse("dinner 1200 with Rohan", knownFriends = listOf("Rohan")).merchant)
        assertEquals("Dinner", LocalHeuristicParser.parse("dinner 1200 with Rohan and Ankit", knownFriends = listOf("Rohan", "Ankit")).merchant)
        assertEquals("Biryani", LocalHeuristicParser.parse("biryani 800 with Rohan and Priya", knownFriends = listOf("Rohan", "Priya")).merchant)
        assertEquals("Pizza", LocalHeuristicParser.parse("pizza 600 split with Vikram and Anjali").merchant)
        assertEquals("Lunch", LocalHeuristicParser.parse("lunch 1500 with Anand", knownFriends = listOf("Anand")).merchant)
        assertEquals("Cab", LocalHeuristicParser.parse("cab 450 split with Ankit", knownFriends = listOf("Ankit Shah")).merchant)
    }

    @Test
    fun `the place after the split names stays the merchant`() {
        val atStarbucks = LocalHeuristicParser.parse("coffee 200 split with Amit at Starbucks")
        assertEquals(listOf("Amit"), atStarbucks.splitWithPersonNames)
        assertEquals("Coffee Starbucks", atStarbucks.merchant)

        val toit = LocalHeuristicParser.parse("dinner with Rohan at Toit 2400", knownFriends = listOf("Rohan"))
        assertEquals(listOf("Rohan"), toit.splitWithPersonNames)
        assertEquals("Dinner Toit", toit.merchant)

        // Not a friend and not "split with": no split, so the name isn't taken out of the line.
        val chai = LocalHeuristicParser.parse("chai 40 with Amit")
        assertEquals(emptyList<String>(), chai.splitWithPersonNames)
        assertEquals("Chai Amit", chai.merchant)
    }
}
