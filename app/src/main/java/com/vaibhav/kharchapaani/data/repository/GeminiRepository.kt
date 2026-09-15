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
import com.vaibhav.kharchapaani.domain.LocalStatementParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiRepository {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun getApiKey(): String {
        return try {
            val key = BuildConfig.GEMINI_API_KEY
            if (key.isNotBlank() && key != "MY_GEMINI_API_KEY") key else ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun parseMessyText(
        rawText: String,
        knownFriends: List<String> = emptyList()
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
            - category: Pick the best match among [Food, Transport, Shopping, Bills, Entertainment, Health, Rent, Team Payment, Income (Client), Income (Other), Other]
            - paymentMethod: Pick best among [GPay, PhonePe, Paytm, Cash, Card, Bank Transfer, Other]
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
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textOutput = parts?.optJSONObject(0)?.optString("text") ?: ""

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
            Log.e("GeminiRepository", "Error parsing with Gemini: ${e.message}", e)
        }

        // Fallback to local heuristic parser
        LocalHeuristicParser.parse(rawText, knownFriends)
    }

    suspend fun parseReceiptOrScreenshot(
        bitmap: Bitmap,
        knownFriends: List<String> = emptyList()
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
            - category: Pick the best fit among [Food, Transport, Shopping, Bills, Entertainment, Health, Rent, Team Payment, Income (Client), Income (Other), Other]
            - paymentMethod: Pick best among [GPay, PhonePe, Paytm, Cash, Card, Bank Transfer, Other]
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
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textOutput = parts?.optJSONObject(0)?.optString("text") ?: ""

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
            Log.e("GeminiRepository", "Error parsing receipt: ${e.message}", e)
        }

        unreadReceipt()
    }

    /**
     * An unread bill fills in nothing. The old fallback invented a merchant ("Scanned Receipt"), a
     * payment method (GPay) and sometimes a category, and put developer text in the note.
     */
    private fun unreadReceipt() = ParsedTransactionResult(type = "expense", amount = 0.0, merchant = "", category = "Other", paymentMethod = "Other", note = "", readFailed = true)

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
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")
                if (!text.isNullOrBlank()) {
                    return@withContext text.trim()
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error generating summary: ${e.message}", e)
        }

        null
    }

    suspend fun parseBankOrUpiStatementPdf(
        pdfBytes: ByteArray,
        fileName: String = "statement.pdf"
    ): StatementParseResult? = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        // Null means "couldn't read it". This used to return a generated sample statement (two
        // ₹75,000 salaries, Zomato, Netflix, some dated in the future) shown as the person's own.
        if (apiKey.isBlank() || pdfBytes.isEmpty()) {
            return@withContext null
        }

        val base64Pdf = Base64.encodeToString(pdfBytes, Base64.NO_WRAP)
        val prompt = """
            You are an expert financial auditor and UPI / Bank Statement extraction engine.
            The provided document is a financial statement PDF / document (e.g. PhonePe, Google Pay, Paytm, HDFC, SBI, ICICI, Axis, Kotak, Bank of Baroda, etc.).
            
            Extract EVERY SINGLE transaction row / line item across all months and pages in this statement.
            
            For each row, provide:
            - date: ISO date string "YYYY-MM-DD" or "YYYY-MM-DDTHH:mm:ss" matching the exact date in the statement
            - type: "expense" (for debits, payments, withdrawals, sent money, Dr, purchases, -) OR "income" (for credits, salary, deposits, cashbacks, refunds, received money, Cr, +)
            - amount: strictly positive numeric float value (e.g. 350.0)
            - merchant: Clean, human-friendly merchant or payee name (e.g. "Swiggy", "Uber", "Amazon", "Infosys Salary", "Tea Stall", "Ramesh Kumar")
            - category: Best category from [Food, Transport, Shopping, Bills, Entertainment, Health, Rent, Groceries, Investment, Income (Salary), Income (Client), Income (Other), Other]
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

        try {
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
                })
            }

            val requestBody = requestJson.toString().toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val jsonResp = JSONObject(responseBody)
                val candidates = jsonResp.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val content = firstCandidate?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                val textOutput = parts?.optJSONObject(0)?.optString("text") ?: ""

                if (textOutput.isNotBlank()) {
                    val parsed = JSONObject(textOutput)
                    val bankOrApp = parsed.optString("bankOrApp", "Bank / UPI Statement")
                    val period = parsed.optString("statementPeriod", "")
                    val txArray = parsed.optJSONArray("transactions")

                    val items = mutableListOf<StatementTransactionItem>()
                    if (txArray != null) {
                        for (i in 0 until txArray.length()) {
                            val itemObj = txArray.getJSONObject(i)
                            val rawDate = itemObj.optString("date", "")
                            val (timestamp, formattedDate, monthName) = LocalStatementParser.parseDateToTimestamp(rawDate)
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
                            val monthSortKey = String.format(java.util.Locale.US, "%04d%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)

                            items.add(
                                StatementTransactionItem(
                                    date = timestamp,
                                    dateFormatted = formattedDate,
                                    monthYearKey = monthName,
                                    monthSortKey = monthSortKey,
                                    type = itemObj.optString("type", "expense"),
                                    amount = kotlin.math.abs(itemObj.optDouble("amount", 0.0)),
                                    merchant = itemObj.optString("merchant", "Merchant"),
                                    category = itemObj.optString("category", "Food"),
                                    paymentMethod = itemObj.optString("paymentMethod", "UPI"),
                                    note = itemObj.optString("note", ""),
                                    isSelected = true
                                )
                            )
                        }
                    }

                    if (items.isNotEmpty()) {
                        return@withContext LocalStatementParser.buildStatementGroups(bankOrApp, period, items)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GeminiRepository", "Error parsing statement PDF: ${e.message}", e)
        }

        null
    }


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
}
