package com.vaibhav.kharchapaani.data.repository

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.ParsedTransactionResult
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.domain.LocalHeuristicParser
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.LocalStatementParser
import com.vaibhav.kharchapaani.domain.AI_VOICE
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.domain.instruction
import com.vaibhav.kharchapaani.domain.StatementPdf
import com.vaibhav.kharchapaani.domain.StatementReadFailure
import com.vaibhav.kharchapaani.domain.StatementReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * @param client the HTTP client; tests swap in one whose interceptor answers without a network.
 * @param endpoint builds the generateContent URL for a model, so tests can point it elsewhere.
 * @param apiKey where the key comes from, so tests don't depend on this build having one.
 */
class GeminiRepository(
    private val client: OkHttpClient = defaultClient(),
    private val endpoint: (model: String) -> String = ::googleEndpoint,
    private val apiKey: () -> String = ::buildConfigApiKey,
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String = apiKey()

    /** Whether this build can ask anything at all. A button that can never work is worse than none. */
    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    suspend fun parseMessyText(
        rawText: String,
        knownFriends: List<String> = emptyList(),
        choices: ParseChoices = ParseChoices.defaults,
    ): ParsedTransactionResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext LocalHeuristicParser.parse(rawText, knownFriends)
        }

        val prompt = """
            You are a smart expense transaction parser for a personal finance app.
            Analyze the following user input and extract structured financial transaction information.
            
            Known teammates / friends: ${knownFriends.joinToString(", ")}
            
            Input text: "$rawText"
            
            Rules:
            - type: "expense" (default) or "income" (if money received, salary, client payout, etc.)
            - amount: total numeric value in standard currency (e.g. 370.0)
            - merchant: Clean merchant or description (e.g. "McDonald's", "Uber", "Priya (Loan)", "Client X")
            - category: Pick the best match among [${choices.categoryList}]
            - paymentMethod: Pick best among [${choices.paymentList}]
            - splitWith: Array of friends' names mentioned in input to split with (e.g. ["Rahul", "Priya"])
            - note: Short summary or original memo
            
            Return ONLY valid JSON matching this schema:
            {
              "type": "expense",
              "amount": 370.0,
              "merchant": "McDonald's",
              "category": "Food",
              "paymentMethod": "GPay",
              "splitWith": [],
              "note": "burger 370 mcd gpay"
            }
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${endpoint(MODEL)}?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) Log.e(TAG, "Gemini HTTP ${response.code}: ${responseBody.take(400)}")

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val candidate = JSONObject(responseBody).optJSONArray("candidates")?.optJSONObject(0)
                val textOutput = candidate?.let(::answerText).orEmpty()

                if (textOutput.isNotBlank()) {
                    val parsed = JSONObject(textOutput)
                    val splitArray = parsed.optJSONArray("splitWith")
                    val splitList = mutableListOf<String>()
                    if (splitArray != null) {
                        for (i in 0 until splitArray.length()) {
                            splitList.add(splitArray.getString(i))
                        }
                    }

                    return@withContext ParsedTransactionResult(
                        type = parsed.optString("type", "expense"),
                        amount = parsed.optDouble("amount", 0.0),
                        merchant = parsed.optString("merchant", "Expense"),
                        category = parsed.optString("category", "Other"),
                        paymentMethod = parsed.optString("paymentMethod", "GPay"),
                        date = System.currentTimeMillis(),
                        note = parsed.optString("note", rawText),
                        splitWithPersonNames = splitList,
                        confidence = 0.95f
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing with Gemini: ${e.message}", e)
        }

        // Fallback to local heuristic parser
        LocalHeuristicParser.parse(rawText, knownFriends)
    }

    suspend fun parseReceiptOrScreenshot(
        bitmap: Bitmap,
        knownFriends: List<String> = emptyList(),
        choices: ParseChoices = ParseChoices.defaults,
    ): ParsedTransactionResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext unreadReceipt()
        }

        val base64Image = bitmapToBase64(bitmap)
        val prompt = """
            You are an expert OCR and financial document parser.
            This image is a receipt, invoice, or payment app screenshot (e.g. GPay, PhonePe, Paytm, Apple Pay, Bank confirmation).
            
            Extract the structured transaction data:
            - type: "expense" (paid to merchant/friend) or "income" (received money)
            - amount: total transaction amount as a float number
            - merchant: receiver/merchant name or payment destination (e.g. "Starbucks", "Uber", "Swiggy", "PhonePe Merchant")
            - category: Pick the best fit among [${choices.categoryList}]
            - paymentMethod: Pick best among [${choices.paymentList}]
            - note: short description or reference ID
            
            Return ONLY valid JSON matching this schema:
            {
              "type": "expense",
              "amount": 250.0,
              "merchant": "Swiggy",
              "category": "Food",
              "paymentMethod": "GPay",
              "note": "Payment to Swiggy UPI"
            }
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.1)
                })
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${endpoint(MODEL)}?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) Log.e(TAG, "Gemini HTTP ${response.code}: ${responseBody.take(400)}")

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val candidate = JSONObject(responseBody).optJSONArray("candidates")?.optJSONObject(0)
                val textOutput = candidate?.let(::answerText).orEmpty()

                if (textOutput.isNotBlank()) {
                    val parsed = JSONObject(textOutput)
                    return@withContext ParsedTransactionResult(
                        type = parsed.optString("type", "expense"),
                        amount = parsed.optDouble("amount", 0.0),
                        merchant = parsed.optString("merchant", "Receipt"),
                        category = parsed.optString("category", "Food"),
                        paymentMethod = parsed.optString("paymentMethod", "GPay"),
                        date = System.currentTimeMillis(),
                        note = parsed.optString("note", "Scanned from receipt"),
                        confidence = 0.98f
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing receipt: ${e.message}", e)
        }

        unreadReceipt()
    }

    /**
     * An unread bill fills in nothing. The old fallback invented a merchant ("Scanned Receipt"), a
     * payment method (GPay) and sometimes a category, and put developer text in the note.
     */
    private fun unreadReceipt() = ParsedTransactionResult(type = "expense", amount = 0.0, merchant = "", category = "Other", paymentMethod = "Other", note = "", readFailed = true)

    /**
     * Asks one of the [AiPrompt]s and returns the answer, or null when it couldn't be generated.
     *
     * One method for all four rather than four near-identical ones: what differs between them is
     * the instruction and the data block, and both are built in the domain layer where they can be
     * tested without a network. These are short calls, so they keep the default timeout — unlike a
     * statement, which is why that one has its own.
     */
    suspend fun ask(prompt: AiPrompt, possessive: String, data: String): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext null

        val body = """
            ${AI_VOICE.trim()}

            ${prompt.instruction(possessive)}

            The data, and the only figures you may use:
            $data
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().put("parts", JSONArray().apply { put(JSONObject().put("text", body)) }))
                })
                put("generationConfig", JSONObject().apply {
                    // Low, not zero: these are sentences, and zero makes them read like a form letter.
                    put("temperature", 0.35)
                    put("maxOutputTokens", 2048)
                })
            }
            val request = Request.Builder()
                .url("${endpoint(MODEL)}?key=$apiKey")
                .post(requestJson.toString().toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini HTTP ${response.code}: ${responseBody.take(400)}")
                    return@withContext null
                }
                val candidate = JSONObject(responseBody).optJSONArray("candidates")?.optJSONObject(0)
                candidate?.let(::answerText)?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error asking ${prompt.name}: ${e.message}", e)
            null
        }
    }

    /** A short read of the month, or null when it couldn't be generated (the screen says so). */
    suspend fun generateFinancialSummary(analytics: MonthlyAnalytics): String? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) {
            return@withContext null
        }

        val categoryBreakdown = analytics.categorySummaries.joinToString(", ") {
            "${it.category}: ₹${String.format("%.0f", it.amount)} (${String.format("%.1f", it.percentage)}%)"
        }

        val prompt = """
            You are a helpful personal finance advisor.
            Write a short, engaging, 2-3 sentence plain-language summary of the user's finances based on this aggregated data.
            Do not use generic financial jargon. Be encouraging and provide 1 practical observation.
            
            Data:
            - Monthly Total Spending: ₹${String.format("%.2f", analytics.totalExpense)}
            - Monthly Total Income: ₹${String.format("%.2f", analytics.totalIncome)}
            - Net Cashflow: ₹${String.format("%.2f", analytics.netCashflow)}
            - Category Breakdown: $categoryBreakdown
            - Top Spending Category/Merchant: ${analytics.topMerchant}
            - Consecutive Logging Streak: ${analytics.dailyStreak} days
        """.trimIndent()

        try {
            val requestJson = JSONObject().apply {
                val contents = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val parts = JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        }
                        put("parts", parts)
                    }
                    put(contentObj)
                }
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.4)
                })
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("${endpoint(MODEL)}?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (!response.isSuccessful) Log.e(TAG, "Gemini HTTP ${response.code}: ${responseBody.take(400)}")

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val candidate = JSONObject(responseBody).optJSONArray("candidates")?.optJSONObject(0)
                val text = candidate?.let(::answerText).orEmpty()
                if (text.isNotBlank()) {
                    return@withContext text
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating summary: ${e.message}", e)
        }

        null
    }

    /**
     * Reads a bank / UPI statement PDF. Kept for callers that only care whether there is a
     * statement; [readStatementPdf] also says why when there isn't.
     */
    suspend fun parseBankOrUpiStatementPdf(
        pdfBytes: ByteArray,
        fileName: String = "statement.pdf",
        choices: ParseChoices = ParseChoices.defaults,
    ): StatementParseResult? = (readStatementPdf(pdfBytes, fileName, choices) as? StatementReadResult.Success)?.statement

    /**
     * Reads every transaction out of a bank / UPI statement PDF.
     *
     * This is the slowest call in the app by a wide margin — a year of rows across a dozen pages,
     * from a model that thinks before it answers — and it shared the 30-second read timeout with
     * the one-line text parse. So it was the only AI path that timed out, and it timed out every
     * time, while the others looked fine. It gets its own budget ([STATEMENT_TIMEOUT]) and its own
     * failure reasons: a bare null told the person nothing and told us nothing either.
     */
    suspend fun readStatementPdf(
        pdfBytes: ByteArray,
        fileName: String = "statement.pdf",
        choices: ParseChoices = ParseChoices.defaults,
    ): StatementReadResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        // A failure used to come back as a generated sample statement (two ₹75,000 salaries, Zomato,
        // Netflix, some dated in the future) shown as the person's own, one tap from their ledger.
        if (apiKey.isBlank()) return@withContext failed(StatementReadFailure.NoKey, "no API key in this build")
        StatementPdf.precheck(pdfBytes)?.let { return@withContext failed(it, "$fileName, ${pdfBytes.size} bytes") }

        val base64Pdf = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
        val prompt = statementPrompt(choices)

        val requestJson = JSONObject().apply {
            val contents = JSONArray().apply {
                val contentObj = JSONObject().apply {
                    val parts = JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", "application/pdf")
                                put("data", base64Pdf)
                            })
                        })
                    }
                    put("parts", parts)
                }
                put(contentObj)
            }
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("responseMimeType", "application/json")
                put("temperature", 0.1)
                // A statement's worth of rows is a long answer; the default budget cuts it off
                // mid-array, and truncated JSON is indistinguishable from a broken read.
                put("maxOutputTokens", STATEMENT_MAX_OUTPUT_TOKENS)
            })
        }

        val request = Request.Builder()
            .url("${endpoint(MODEL)}?key=$apiKey")
            .post(requestJson.toString().toRequestBody(jsonMediaType))
            .build()
        val patientClient = client.newBuilder()
            .readTimeout(STATEMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(STATEMENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(STATEMENT_TIMEOUT_SECONDS + 30, TimeUnit.SECONDS)
            .build()

        val body = try {
            patientClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    // Google's error body says which of these it is (bad key, quota, a file it
                    // won't take). Dropping it on the floor is what made this undiagnosable.
                    return@withContext failed(StatementReadFailure.Rejected, "HTTP ${response.code}: ${text.take(400)}")
                }
                text
            }
        } catch (e: InterruptedIOException) {
            // Covers SocketTimeoutException and the one OkHttp's callTimeout throws.
            return@withContext failed(StatementReadFailure.Timeout, e.message)
        } catch (e: IOException) {
            return@withContext failed(StatementReadFailure.Offline, e.message)
        } catch (e: Exception) {
            return@withContext failed(StatementReadFailure.Unreadable, e.message)
        }

        val candidate = try {
            JSONObject(body).optJSONArray("candidates")?.optJSONObject(0)
        } catch (e: JSONException) {
            return@withContext failed(StatementReadFailure.Unreadable, "envelope: ${e.message}")
        } ?: return@withContext failed(StatementReadFailure.Rejected, "no candidate: ${body.take(400)}")

        val finishReason = candidate.optString("finishReason")
        val textOutput = answerText(candidate)
        if (textOutput.isBlank()) {
            return@withContext failed(truncatedOr(finishReason, StatementReadFailure.Unreadable), "empty answer, finishReason=$finishReason")
        }

        val parsed = try {
            JSONObject(textOutput)
        } catch (e: JSONException) {
            // A cut-off answer is invalid JSON; say that, rather than calling the statement unreadable.
            return@withContext failed(truncatedOr(finishReason, StatementReadFailure.Unreadable), "answer: ${e.message}")
        }

        val items = statementItems(parsed.optJSONArray("transactions"), choices)
        if (items.isEmpty()) {
            return@withContext failed(truncatedOr(finishReason, StatementReadFailure.NothingFound), "0 rows, finishReason=$finishReason")
        }
        StatementReadResult.Success(
            LocalStatementParser.buildStatementGroups(
                parsed.optString("bankOrApp", "Bank / UPI Statement"),
                parsed.optString("statementPeriod", ""),
                items,
            )
        )
    }

    private fun statementItems(txArray: JSONArray?, choices: ParseChoices): List<StatementTransactionItem> {
        val items = mutableListOf<StatementTransactionItem>()
        for (i in 0 until (txArray?.length() ?: 0)) {
            val itemObj = txArray?.optJSONObject(i) ?: continue
            val (timestamp, formattedDate, monthName) = LocalStatementParser.parseDateToTimestamp(itemObj.optString("date", ""))
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val monthSortKey = String.format(java.util.Locale.US, "%04d%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
            val type = itemObj.optString("type", "expense")

            items.add(
                StatementTransactionItem(
                    date = timestamp,
                    dateFormatted = formattedDate,
                    monthYearKey = monthName,
                    monthSortKey = monthSortKey,
                    type = type,
                    amount = kotlin.math.abs(itemObj.optDouble("amount", 0.0)),
                    merchant = itemObj.optString("merchant", "Merchant"),
                    category = choices.categoryFor(type, itemObj.optString("category")),
                    paymentMethod = choices.paymentFor(itemObj.optString("paymentMethod", "UPI")),
                    note = itemObj.optString("note", ""),
                    isSelected = true,
                )
            )
        }
        return items
    }

    private fun truncatedOr(finishReason: String, otherwise: StatementReadFailure): StatementReadFailure =
        if (finishReason == "MAX_TOKENS") StatementReadFailure.Truncated else otherwise

    private fun failed(reason: StatementReadFailure, detail: String?): StatementReadResult.Failed {
        Log.e(TAG, "Statement read failed: $reason${detail?.let { " - $it" } ?: ""}")
        return StatementReadResult.Failed(reason, detail)
    }

    private fun statementPrompt(choices: ParseChoices): String = """
        You are an expert financial auditor and UPI / Bank Statement extraction engine.
        The provided document is a financial statement PDF / document (e.g. PhonePe, Google Pay, Paytm, HDFC, SBI, ICICI, Axis, Kotak, Bank of Baroda, etc.).

        Extract EVERY SINGLE transaction row / line item across all months and pages in this statement.

        For each row, provide:
        - date: ISO date string "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm:ss" matching the exact date in the statement
        - type: "expense" (for debits, payments, withdrawals, sent money, Dr, purchases, -) OR "income" (for credits, salary, deposits, cashbacks, refunds, received money, Cr, +)
        - amount: strictly positive numeric float value (e.g. 350.0)
        - merchant: Clean, human-friendly merchant or payee name (e.g. "Swiggy", "Uber", "Amazon", "Infosys Salary", "Tea Stall", "Ramesh Kumar")
        - category: Best category from [${choices.categoryList}]
        - paymentMethod: e.g. "UPI", "GPay", "PhonePe", "Paytm", "Debit Card", "Net Banking", "NEFT", "IMPS", "Bank Transfer"
        - note: Original narration or transaction reference id

        Return ONLY valid JSON matching this schema:
        {
          "bankOrApp": "PhonePe UPI Statement",
          "statementPeriod": "Jul 2024 - Aug 2024",
          "transactions": [
            {
              "date": "2024-08-14T12:30:00",
              "type": "expense",
              "amount": 250.0,
              "merchant": "Swiggy",
              "category": "Food",
              "paymentMethod": "UPI",
              "note": "UPI/4123541/swiggy@icici"
            }
          ]
        }
    """.trimIndent()

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Resize bitmap if very large to prevent memory overhead
        val scaled = if (bitmap.width > 1200 || bitmap.height > 1200) {
            val maxDim = maxOf(bitmap.width, bitmap.height)
            val scale = 1200f / maxDim
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * The answer out of a candidate, as every one of these calls needs it. Reading `parts[0].text`
     * was wrong twice over: a thinking model can put a thought part first, and a long answer can
     * arrive split across several parts. Thought parts are marked and skipped; the rest join.
     */
    private fun answerText(candidate: JSONObject): String {
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: return ""
        return buildString {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought", false)) continue
                append(part.optString("text", ""))
            }
        }.trim()
    }

    companion object {
        /** Google's current legacy Flash model; every call in this class uses the same one. */
        private const val MODEL = "gemini-3.5-flash"
        private const val TAG = "GeminiRepository"

        /** Short calls: one line of text, or one photo. */
        private const val DEFAULT_TIMEOUT_SECONDS = 30L

        /**
         * A statement is pages of rows read by a model that thinks first. Thirty seconds was never
         * enough for it, which is the whole reason statement import never worked on a real file.
         */
        internal const val STATEMENT_TIMEOUT_SECONDS = 180L

        /** Room for a long year of rows without letting a runaway answer stream forever. */
        private const val STATEMENT_MAX_OUTPUT_TOKENS = 32768

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        private fun googleEndpoint(model: String): String =
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        private fun buildConfigApiKey(): String = try {
            BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() && it != "MY_GEMINI_API_KEY" }.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
}

/** The categories and payment methods a parser may answer with: this account's own choices. */
data class ParseChoices(val expenseCategories: List<String>, val incomeCategories: List<String>, val paymentMethods: List<String>) {
    val categoryList: String get() = (expenseCategories + incomeCategories).joinToString(", ")
    val paymentList: String get() = paymentMethods.joinToString(", ")

    /** A parsed category as one of these choices; unknown ones land on Other rather than a one-off. */
    fun categoryFor(type: String, raw: String?): String =
        if (type.equals("income", ignoreCase = true)) {
            Categories.match(raw, incomeCategories, Categories.OTHER_INCOME)
        } else {
            Categories.match(raw, expenseCategories, Categories.OTHER)
        }

    /** A parsed payment method in this account's spelling when it's one of them, else as read (e.g. "NEFT"). */
    fun paymentFor(raw: String?): String =
        Categories.match(raw, paymentMethods, fallback = raw?.trim()?.takeIf { it.isNotEmpty() } ?: Categories.OTHER)

    companion object {
        val defaults = of(CategoryPrefs())

        fun of(prefs: CategoryPrefs) = ParseChoices(
            Categories.expenseCategories(prefs),
            Categories.incomeCategories(prefs),
            Categories.paymentMethods(prefs),
        )
    }
}
