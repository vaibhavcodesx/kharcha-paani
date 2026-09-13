package com.example.domain

import com.example.data.model.ParsedTransactionResult
import java.util.Locale

object LocalHeuristicParser {

    private val paymentMethods = listOf(
        "gpay" to "GPay",
        "google pay" to "GPay",
        "phonepe" to "PhonePe",
        "paytm" to "Paytm",
        "cash" to "Cash",
        "card" to "Card",
        "credit card" to "Card",
        "debit card" to "Card",
        "upi" to "GPay",
        "bank transfer" to "Bank Transfer",
        "neft" to "Bank Transfer",
        "imps" to "Bank Transfer",
        "cred" to "Card"
    )

    private val categories = listOf(
        listOf("burger", "pizza", "mcd", "mcdonalds", "kfc", "starbucks", "coffee", "tea", "chai", "lunch", "dinner", "breakfast", "swiggy", "zomato", "restaurant", "food", "snack", "subway", "dominos", "biryani") to "Food",
        listOf("uber", "ola", "rapido", "cab", "auto", "metro", "bus", "train", "flight", "petrol", "diesel", "fuel", "toll", "parking", "transport", "travel") to "Transport",
        listOf("amazon", "flipkart", "myntra", "zara", "h&m", "nike", "clothes", "shoes", "shopping", "mall", "electronics", "blinkit", "zepto", "instamart", "grocery", "groceries") to "Shopping",
        listOf("wifi", "broadband", "electricity", "water", "bill", "recharge", "airtel", "jio", "vi", "subscription", "netflix", "spotify", "youtube", "cloud", "aws", "gcp") to "Bills",
        listOf("movie", "cinema", "pvr", "game", "gaming", "steam", "party", "club", "drinks", "beer", "entertainment", "concert") to "Entertainment",
        listOf("pharmacy", "medicine", "doctor", "hospital", "gym", "fitness", "supplement", "health", "dental", "clinic") to "Health",
        listOf("rent", "maintenance", "flat", "apartment", "pg", "hostel", "landlord") to "Rent",
        listOf("project", "freelance", "salary", "client", "payout", "dividend", "interest", "refund", "cashback", "stipend") to "Income (Client)",
        listOf("teammate", "team", "hackathon", "bounty", "domain", "hosting", "tool") to "Team Payment"
    )

    private val incomeKeywords = listOf(
        "received", "got paid", "credited", "income", "salary", "freelance", "client payment",
        "client paid", "refund", "cashback", "dividend", "stipend", "got money"
    )

    private val iouKeywords = listOf(
        "lent", "gave to", "loaned", "paid for"
    )

    fun parse(rawText: String, knownFriends: List<String> = emptyList()): ParsedTransactionResult {
        val text = rawText.trim()
        val lower = text.lowercase(Locale.getDefault())

        // 1. Detect Type (Income vs Expense)
        var type = "expense"
        for (kw in incomeKeywords) {
            if (lower.contains(kw)) {
                type = "income"
                break
            }
        }

        // 2. Detect Amount: Look for patterns like 370, 370.50, ₹370, Rs 370, Rs. 370, 12k, 5k
        var amount = 0.0
        val kMatch = Regex("""(\d+(\.\d+)?)\s*k\b""").find(lower)
        if (kMatch != null) {
            val num = kMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            amount = num * 1000.0
        } else {
            val amountRegex = Regex("""(?:₹|rs\.?|inr)?\s*(\d+(?:\.\d{1,2})?)""")
            val matches = amountRegex.findAll(lower).toList()
            for (match in matches) {
                val value = match.groupValues[1].toDoubleOrNull()
                if (value != null && value > 0) {
                    amount = value
                    break
                }
            }
        }

        // 3. Detect Payment Method
        var paymentMethod = "GPay"
        for ((kw, method) in paymentMethods) {
            if (lower.contains(kw)) {
                paymentMethod = method
                break
            }
        }

        // 4. Detect Category
        var category = if (type == "income") "Income (Client)" else "Other"
        for ((keywords, cat) in categories) {
            if (keywords.any { lower.contains(it) }) {
                category = cat
                break
            }
        }

        // 5. Detect Split Friends
        val splitFriends = mutableListOf<String>()
        val splitRegex = Regex("""split\s+(?:with\s+)?([a-zA-Z\s,]+)""")
        val splitMatch = splitRegex.find(lower)
        if (splitMatch != null) {
            val rawNames = splitMatch.groupValues[1]
            val splitParts = rawNames.split(",", "and", "&")
            for (p in splitParts) {
                val trimmed = p.trim().capitalizeWord()
                if (trimmed.isNotBlank() && trimmed.length > 1) {
                    splitFriends.add(trimmed)
                }
            }
        } else {
            // Check against known friends list
            for (friend in knownFriends) {
                if (lower.contains(friend.lowercase(Locale.getDefault()))) {
                    splitFriends.add(friend)
                }
            }
        }

        // 6. Detect Merchant / Title
        var merchant = ""
        val commonWords = setOf(
            "split", "with", "gpay", "phonepe", "paytm", "cash", "card", "paid",
            "received", "rs", "inr", "for", "to", "at", "the", "and", "lent", "a"
        )
        val tokens = text.split(Regex("""\s+""")).filter { token ->
            val clean = token.lowercase(Locale.getDefault()).replace(Regex("[^a-z0-9]"), "")
            clean.isNotBlank() && !commonWords.contains(clean) && !clean.matches(Regex("""\d+(\.\d+)?""")) && !clean.matches(Regex("""\d+k"""))
        }

        merchant = if (tokens.isNotEmpty()) {
            tokens.take(3).joinToString(" ").capitalizeWord()
        } else {
            if (type == "income") "Client Payment" else if (category != "Other") category else "Expense"
        }

        return ParsedTransactionResult(
            type = type,
            amount = amount,
            merchant = merchant,
            category = category,
            paymentMethod = paymentMethod,
            date = System.currentTimeMillis(),
            note = text,
            splitWithPersonNames = splitFriends.distinct(),
            confidence = if (amount > 0) 0.85f else 0.4f
        )
    }

    private fun String.capitalizeWord(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}
