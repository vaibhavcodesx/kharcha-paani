package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.ParsedTransactionResult
import java.util.Locale

object LocalHeuristicParser {

    // Most specific first: "credit card" has to win over "card", and "google pay" over "pay". Matched
    // as whole words (see [containsWord]), so "cred" no longer fires inside "credited".
    private val paymentMethods = listOf(
        "google pay" to "GPay",
        "credit card" to "Credit Card",
        "debit card" to "Debit Card",
        "bank transfer" to "Bank Transfer",
        "gpay" to "GPay",
        "phonepe" to "PhonePe",
        "paytm" to "Paytm",
        "cash" to "Cash",
        "card" to "Card",
        "upi" to "GPay",
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
            if (containsWord(lower, kw)) {
                type = "income"
                break
            }
        }

        // 2. Detect Amount: see [AiParse.amountIn].
        val amount = AiParse.amountIn(text) ?: 0.0

        // 3. Detect Payment Method. Blank when none is said: the sheet then uses the person's own
        // default rather than GPay, which may be hidden or never used.
        var paymentMethod = ""
        for ((kw, method) in paymentMethods) {
            if (containsWord(lower, kw)) {
                paymentMethod = method
                break
            }
        }

        // 4. Detect Category
        var category = if (type == "income") "Income (Client)" else "Other"
        for ((keywords, cat) in categories) {
            // Whole words: as substrings, "vi" (the telecom) put every "movie" under Bills, "tea"
            // matched "team" and "steam", "bus" matched "business", and "cab" matched "cabbage".
            if (keywords.any { containsWord(lower, it) }) {
                category = cat
                break
            }
        }

        // 5. Detect Split Friends. Known friends first, matched as whole words ("Raj" is not in
        // "Rajesh"); names after "split with" only when none of them appear. Split on the word
        // "and", not the letters: "Anand" used to come apart as "An" and "".
        val splitFriends = mutableListOf<String>()
        knownFriends.filter { it.isNotBlank() && containsWord(lower, it.trim().lowercase(Locale.getDefault())) }
            .forEach { splitFriends.add(it) }
        if (splitFriends.isEmpty()) {
            Regex("""split\s+(?:with\s+)?([a-zA-Z\s,&]+)""").find(lower)?.let { match ->
                match.groupValues[1].split(Regex(""",|&|\band\b"""))
                    .map { it.trim().capitalizeWord() }
                    .filter { it.length > 1 }
                    .forEach { splitFriends.add(it) }
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
            clean.isNotBlank() && !commonWords.contains(clean) && !clean.matches(Regex("""\d+(\.\d+)?""")) &&
                !clean.matches(Regex("""\d+k""")) && !clean.matches(Regex("""(rs|inr)?\d+"""))
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

    private fun containsWord(text: String, word: String): Boolean =
        Regex("""(?<![\p{L}\p{N}])${Regex.escape(word)}(?![\p{L}\p{N}])""").containsMatchIn(text)

    private fun String.capitalizeWord(): String {
        return this.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }
}

/**
 * Reading numbers and dates out of text a person typed or a model returned. Pure, so the awkward
 * cases are tested rather than hoped for.
 */
object AiParse {

    private val DIGIT_GROUPING = Regex("""(?<=\d),(?=\d)""")
    private val SHORTHAND = Regex("""(\d+(?:\.\d+)?)\s*(k|lakh|lac)(?![\p{L}])""", RegexOption.IGNORE_CASE)
    private val WITH_CURRENCY = Regex("""(?:₹|\brs\.?|\binr)\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
    private val ANY_NUMBER = Regex("""(?<![\d.])(\d+(?:\.\d{1,2})?)(?![\d])""")

    /**
     * The amount in something like "dinner 1,299 gpay", "₹1.2k", "rent 12k", "2 coffees ₹300" or
     * "1,00,000 bonus", or null when there isn't one.
     *
     * The old parser took the first run of digits, so "1,299" read as ₹1 (the comma ended it) and
     * "2 coffees 300" as ₹2. Now: grouping commas are dropped first (Indian 1,00,000 as well as
     * 1,299), shorthand wins, then a figure written with ₹/Rs/INR, then the largest bare number,
     * since a count of things is almost always smaller than what they cost.
     */
    fun amountIn(text: String): Double? {
        val clean = text.replace(DIGIT_GROUPING, "")
        SHORTHAND.find(clean)?.let { m ->
            val n = m.groupValues[1].toDoubleOrNull() ?: return@let
            val scale = if (m.groupValues[2].equals("k", ignoreCase = true)) 1_000.0 else 100_000.0
            return roundPaisa(n * scale).takeIf { it > 0 }
        }
        WITH_CURRENCY.find(clean)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0 }?.let { return it }
        return ANY_NUMBER.findAll(clean).mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it > 0 }.maxOrNull()
    }

    /**
     * An amount field from a model's JSON, which is usually a number but can come back as a string
     * ("1,299", "₹1299.00"). `optDouble` turned any such string into 0 — or NaN — silently.
     */
    fun amountField(value: Any?): Double {
        val n = when (value) {
            is Number -> value.toDouble()
            is String -> amountIn(value) ?: 0.0
            else -> 0.0
        }
        return if (n.isFinite()) kotlin.math.abs(n) else 0.0
    }

    /**
     * A date a model read from a receipt or a sentence ("2026-09-24", "2026-09-24T19:05", also with
     * seconds or a zone), as epoch millis in [zone]; [now] when there isn't a usable one.
     *
     * Not usable: missing, unparseable, more than a day in the future (a misread year), or more than
     * a year back (a model that guessed). A date with no time keeps the current time of day, so an
     * entry for "yesterday" sorts sensibly among yesterday's others.
     */
    fun dateOrNow(raw: String?, now: Long, zone: java.time.ZoneId): Long {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return now
        val nowTime = java.time.Instant.ofEpochMilli(now).atZone(zone)
        val parsed: java.time.ZonedDateTime = try {
            when {
                text.length <= 10 -> java.time.LocalDate.parse(text).atTime(nowTime.toLocalTime()).atZone(zone)
                text.endsWith("Z") || Regex("""[+-]\d{2}:\d{2}$""").containsMatchIn(text) ->
                    java.time.OffsetDateTime.parse(text).atZoneSameInstant(zone)
                else -> java.time.LocalDateTime.parse(text).atZone(zone)
            }
        } catch (e: java.time.format.DateTimeParseException) {
            return now
        }
        val millis = parsed.toInstant().toEpochMilli()
        val day = 24L * 60 * 60 * 1000
        return if (millis > now + day || millis < now - 366 * day) now else millis
    }
}
