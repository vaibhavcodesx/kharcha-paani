package com.vaibhav.kharchapaani.data.repository

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.remote.AppIdentity
import com.vaibhav.kharchapaani.data.model.ParsedTransactionResult
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.domain.StatementChecks
import com.vaibhav.kharchapaani.domain.LocalHeuristicParser
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.LocalStatementParser
import com.vaibhav.kharchapaani.domain.AI_VOICE
import com.vaibhav.kharchapaani.domain.AiAnswerText
import com.vaibhav.kharchapaani.domain.AiAskResult
import com.vaibhav.kharchapaani.domain.AiFailure
import com.vaibhav.kharchapaani.domain.AiParse
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * @param client the HTTP client; tests swap in one whose interceptor answers without a network.
 * @param endpoint builds the generateContent URL for a model, so tests can point it elsewhere.
 * @param apiKey where the key comes from, so tests don't depend on this build having one.
 */
class GeminiRepository(
    /**
     * This app's package and signing fingerprint, sent with every request so a Gemini key
     * restricted to this app is accepted. Null in tests and when the signature can't be read;
     * see [AppIdentity].
     */
    appIdentity: AppIdentity? = null,
    private val client: OkHttpClient = defaultClient(appIdentity),
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
    ): ParsedTransactionResult = parseText(rawText, knownFriends, choices).result

    /**
     * Reads a typed or spoken line ("dinner 1,299 with Rohan yesterday gpay") into an entry.
     *
     * Never fails outright: without the AI it falls back to [LocalHeuristicParser], flagged
     * [ParsedTransactionResult.guessedOnPhone] so the sheet can say so, and [TextParseOutcome.failure]
     * says why the AI wasn't used (offline and out-of-quota want different notices).
     */
    suspend fun parseText(
        rawText: String,
        knownFriends: List<String> = emptyList(),
        choices: ParseChoices = ParseChoices.defaults,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TextParseOutcome = withContext(Dispatchers.IO) {
        val onPhone = LocalHeuristicParser.parse(rawText, knownFriends).copy(date = now)
        val apiKey = getApiKey()
        // No key is this build's normal, not a failure, so no notice.
        if (apiKey.isBlank()) return@withContext TextParseOutcome(onPhone, failure = null)

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val body = jsonRequest(listOf(textPart(textPrompt(rawText, knownFriends, choices, today))), temperature = 0.1, json = true, thinking = QUICK_THINKING)
        when (val outcome = call(body, apiKey, TEXT_TIMEOUT_SECONDS, "text")) {
            is CallOutcome.Failed -> TextParseOutcome(onPhone.copy(guessedOnPhone = true), outcome.reason)
            is CallOutcome.Ok -> {
                val parsed = outcome.json()
                    ?: return@withContext TextParseOutcome(onPhone.copy(guessedOnPhone = true), AiFailure.Unreadable)
                // The model missing a figure that plainly is in the line is worse than the phone's guess.
                val amount = AiParse.amountField(parsed.opt("amount")).takeIf { it > 0 } ?: onPhone.amount
                val splitWith = parsed.optJSONArray("splitWith")
                    ?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optString(i).trim().takeIf { it.isNotEmpty() } } }
                    .orEmpty()
                TextParseOutcome(
                    ParsedTransactionResult(
                        type = if (parsed.optString("type").equals("income", ignoreCase = true)) "income" else "expense",
                        amount = amount,
                        merchant = parsed.optString("merchant").trim().ifEmpty { onPhone.merchant },
                        category = parsed.optString("category", "Other"),
                        paymentMethod = parsed.optString("paymentMethod").trim().ifEmpty { choices.paymentMethods.firstOrNull() ?: "Other" },
                        date = AiParse.dateOrNow(parsed.optString("date"), now, zone),
                        note = parsed.optString("note").trim().ifEmpty { rawText.trim() },
                        splitWithPersonNames = splitWith,
                        confidence = 0.95f,
                    ),
                    failure = null,
                )
            }
        }
    }

    private fun textPrompt(rawText: String, knownFriends: List<String>, choices: ParseChoices, today: LocalDate): String = """
        You turn one line a person typed or said into an entry for an Indian expense app. Amounts are
        in rupees.

        Today is $today. Friends on this account: ${knownFriends.joinToString(", ").ifEmpty { "none" }}.

        The line: ${JSONObject.quote(rawText)}

        Fill in:
        - type: "income" only if money came in (salary, refund, client paid, received); otherwise "expense".
        - amount: the total as a plain number. "1,299" is 1299, "1.2k" is 1200, "2 lakh" is 200000. If
          several numbers appear, it is the money, not a count ("2 coffees 300" is 300). 0 if there is
          no amount.
        - merchant: the place or thing, short and properly capitalised ("Swiggy", "Auto to office",
          "Rohan (loan)"). Never include the amount or the payment method.
        - category: exactly one of [${choices.categoryList}].
        - paymentMethod: exactly one of [${choices.paymentList}]. If the line doesn't say, use the first
          one in that list.
        - date: "YYYY-MM-DD" only if the line says when ("yesterday", "on 12 Sep", "last Friday"),
          worked out from today's date; otherwise "".
        - splitWith: names from the friends list that the line says to split with, spelled exactly as
          in the list; [] if none.
        - note: the original line.

        Return only this JSON:
        {"type":"expense","amount":370,"merchant":"McDonald's","category":"Food","paymentMethod":"GPay","date":"","splitWith":[],"note":"burger 370 mcd gpay"}
    """.trimIndent()

    suspend fun parseReceiptOrScreenshot(
        bitmap: Bitmap,
        @Suppress("UNUSED_PARAMETER") knownFriends: List<String> = emptyList(),
        choices: ParseChoices = ParseChoices.defaults,
    ): ParsedTransactionResult = when (val result = readReceipt(bitmap, choices)) {
        // Kept for existing callers: a bill with no total still reads as a failed read here.
        is ReceiptReadResult.Read -> if (result.missingAmount) result.entry.copy(readFailed = true) else result.entry
        is ReceiptReadResult.Failed -> unreadReceipt()
    }

    /**
     * Reads a bill, receipt or payment screenshot. Says why when it can't ([ReceiptReadResult.Failed]),
     * and says when it read the bill but found no total ([ReceiptReadResult.Read.missingAmount]):
     * "try a clearer photo" is the wrong advice for someone who is simply offline.
     */
    suspend fun readReceipt(
        bitmap: Bitmap,
        choices: ParseChoices = ParseChoices.defaults,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ReceiptReadResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext ReceiptReadResult.Failed(AiFailure.NoKey)

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val parts = listOf(
            textPart(receiptPrompt(choices, today)),
            JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", bitmapToBase64(bitmap))),
        )
        when (val outcome = call(jsonRequest(parts, temperature = 0.1, json = true, thinking = QUICK_THINKING), apiKey, RECEIPT_TIMEOUT_SECONDS, "receipt")) {
            is CallOutcome.Failed -> ReceiptReadResult.Failed(outcome.reason)
            is CallOutcome.Ok -> {
                val parsed = outcome.json() ?: return@withContext ReceiptReadResult.Failed(AiFailure.Unreadable)
                val amount = AiParse.amountField(parsed.opt("amount"))
                ReceiptReadResult.Read(
                    ParsedTransactionResult(
                        type = if (parsed.optString("type").equals("income", ignoreCase = true)) "income" else "expense",
                        amount = amount,
                        merchant = parsed.optString("merchant").trim(),
                        category = parsed.optString("category", "Other"),
                        paymentMethod = parsed.optString("paymentMethod").trim().ifEmpty { "Other" },
                        date = AiParse.dateOrNow(parsed.optString("date"), now, zone),
                        note = parsed.optString("note").trim(),
                        confidence = 0.98f,
                    ),
                    missingAmount = amount <= 0,
                )
            }
        }
    }

    private fun receiptPrompt(choices: ParseChoices, today: LocalDate): String = """
        This image is a bill, receipt, invoice or payment-app screenshot (GPay, PhonePe, Paytm, a bank
        SMS or confirmation) from India. Today is $today.

        Fill in:
        - type: "income" if this shows money received; otherwise "expense".
        - amount: the total actually paid, as a plain number: the grand total after tax, discounts and
          tip, not a subtotal or one line item. For a payment screenshot, the amount paid. 0 if you
          can't find it, or if this isn't a bill or payment at all. Never guess.
        - merchant: who was paid, as a person would say it ("Starbucks", "Swiggy", "Ramesh Kumar"),
          not a UPI handle or reference number.
        - category: exactly one of [${choices.categoryList}].
        - paymentMethod: exactly one of [${choices.paymentList}] if the image shows how it was paid,
          otherwise "".
        - date: when it was paid, "YYYY-MM-DD", or "YYYY-MM-DDTHH:mm" if a time is shown; "" if no date
          is visible. Indian receipts write dates day first (05/09 is 5 September).
        - note: a short description, or the order or reference number if that's all there is.

        Return only this JSON:
        {"type":"expense","amount":250,"merchant":"Swiggy","category":"Food","paymentMethod":"GPay","date":"2026-09-24T13:05","note":"Order 4412"}
    """.trimIndent()

    /**
     * An unread bill fills in nothing. The old fallback invented a merchant ("Scanned Receipt"), a
     * payment method (GPay) and sometimes a category, and put developer text in the note.
     */
    private fun unreadReceipt() = ParsedTransactionResult(type = "expense", amount = 0.0, merchant = "", category = "Other", paymentMethod = "Other", note = "", readFailed = true)

    /** Asks one of the [AiPrompt]s; null when it couldn't be answered. [askForAnswer] also says why. */
    suspend fun ask(prompt: AiPrompt, possessive: String, data: String): String? =
        askForAnswer(prompt, possessive, data).answerOrNull

    /**
     * Asks one of the [AiPrompt]s and returns the answer, or why there isn't one.
     *
     * One method for all four rather than four near-identical ones: what differs between them is
     * the instruction and the data block, and both are built in the domain layer where they can be
     * tested without a network.
     */
    suspend fun askForAnswer(prompt: AiPrompt, possessive: String, data: String): AiAskResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey()
        if (apiKey.isBlank()) return@withContext AiAskResult.Failed(AiFailure.NoKey)

        val text = """
            ${AI_VOICE.trim()}

            ${prompt.instruction(possessive)}

            The data, and the only figures you may use:
            $data
        """.trimIndent()
        // Low, not zero: these are sentences, and zero makes them read like a form letter.
        val body = jsonRequest(listOf(textPart(text)), temperature = 0.35, json = false, maxOutputTokens = ASK_MAX_OUTPUT_TOKENS)
        when (val outcome = call(body, apiKey, ASK_TIMEOUT_SECONDS, prompt.name)) {
            is CallOutcome.Failed -> AiAskResult.Failed(outcome.reason)
            is CallOutcome.Ok -> {
                val truncated = outcome.finishReason == "MAX_TOKENS"
                if (truncated) Log.w(TAG, "${prompt.name} hit the output cap of $ASK_MAX_OUTPUT_TOKENS tokens")
                AiAnswerText.tidy(outcome.text, truncated)?.let { AiAskResult.Answer(it) }
                    ?: AiAskResult.Failed(if (truncated) AiFailure.Truncated else AiFailure.Unreadable)
            }
        }
    }

    private fun textPart(text: String): JSONObject = JSONObject().put("text", text)

    /**
     * @param thinking a Gemini `thinkingLevel`, or null for the model's own default. Reading one line
     *   or one receipt doesn't need the default's long think: measured on gemini-3.6-flash, "low"
     *   answered a receipt in 2 s instead of 5 with the same fields.
     */
    private fun jsonRequest(
        parts: List<JSONObject>,
        temperature: Double,
        json: Boolean,
        maxOutputTokens: Int? = null,
        thinking: String? = null,
    ): JSONObject =
        JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().apply { parts.forEach { put(it) } })))
            put("generationConfig", JSONObject().apply {
                if (json) put("responseMimeType", "application/json")
                put("temperature", temperature)
                maxOutputTokens?.let { put("maxOutputTokens", it) }
                thinking?.let { put("thinkingConfig", JSONObject().put("thinkingLevel", it)) }
            })
        }

    /** One request's outcome: the first candidate's answer, or why there isn't a usable one. */
    private sealed interface CallOutcome {
        data class Ok(val text: String, val finishReason: String) : CallOutcome {
            /** The answer as the JSON object asked for, or null when it isn't one. */
            fun json(): JSONObject? = try {
                JSONObject(stripFence(text))
            } catch (e: JSONException) {
                null
            }
        }

        data class Failed(val reason: AiFailure) : CallOutcome
    }

    /**
     * Sends one generateContent request and classifies what came back. Every AI path goes through
     * here, so every one of them tells offline from out-of-quota from a refused key from a block,
     * instead of each catching `Exception` and landing on the same vague message.
     */
    private fun call(body: JSONObject, apiKey: String, timeoutSeconds: Long, label: String): CallOutcome {
        val first = callModel(MODEL, body, apiKey, timeoutSeconds, label)
        // Google's "high demand" 503s and per-model rate limits come and go by the minute and are
        // per model. One retry on the lighter model turns most of them into an answer instead of a
        // notice. Anything else (offline, a refused key, a block) would fail the same way again.
        if (first !is CallOutcome.Failed || first.reason !in RETRY_ON_FALLBACK) return first
        Log.w(TAG, "$label: $MODEL unavailable (${first.reason}); retrying on $FALLBACK_MODEL")
        // The fallback gets its own default thinking: it may not take the same levels.
        val plain = JSONObject(body.toString()).apply { optJSONObject("generationConfig")?.remove("thinkingConfig") }
        return callModel(FALLBACK_MODEL, plain, apiKey, timeoutSeconds, "$label (fallback)")
    }

    private fun callModel(model: String, body: JSONObject, apiKey: String, timeoutSeconds: Long, label: String): CallOutcome {
        val request = Request.Builder()
            .url("${endpoint(model)}?key=$apiKey")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        val timed = client.newBuilder()
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .callTimeout(timeoutSeconds + 15, TimeUnit.SECONDS)
            .build()
        val (code, text) = try {
            timed.newCall(request).execute().use { response -> response.code to response.body?.string().orEmpty() }
        } catch (e: InterruptedIOException) {
            return failedCall(label, AiFailure.Timeout, e.message)
        } catch (e: IOException) {
            return failedCall(label, AiFailure.Offline, e.message)
        }
        if (code !in 200..299) return failedCall(label, AiFailure.fromHttp(code, text), "HTTP $code: ${text.take(400)}")

        val envelope = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return failedCall(label, AiFailure.Unreadable, "envelope: ${e.message}")
        }
        val candidate = envelope.optJSONArray("candidates")?.optJSONObject(0)
        if (candidate == null) {
            val blocked = envelope.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            return failedCall(label, if (blocked.isNotEmpty()) AiFailure.Blocked else AiFailure.Unreadable, "no candidate: ${text.take(400)}")
        }
        val finishReason = candidate.optString("finishReason")
        val answer = answerText(candidate)
        if (answer.isBlank()) {
            val reason = when {
                AiFailure.isBlockReason(finishReason) -> AiFailure.Blocked
                finishReason == "MAX_TOKENS" -> AiFailure.Truncated
                else -> AiFailure.Unreadable
            }
            return failedCall(label, reason, "empty answer, finishReason=$finishReason")
        }
        return CallOutcome.Ok(answer, finishReason)
    }

    private fun failedCall(label: String, reason: AiFailure, detail: String?): CallOutcome.Failed {
        Log.e(TAG, "$label failed: $reason${detail?.let { " - $it" } ?: ""}")
        return CallOutcome.Failed(reason)
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
                    val reason = when (AiFailure.fromHttp(response.code, text)) {
                        AiFailure.RateLimited -> StatementReadFailure.RateLimited
                        AiFailure.KeyRefused -> StatementReadFailure.KeyRefused
                        else -> StatementReadFailure.Rejected
                    }
                    return@withContext failed(reason, "HTTP ${response.code}: ${text.take(400)}")
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

        val envelope = try {
            JSONObject(body)
        } catch (e: JSONException) {
            return@withContext failed(StatementReadFailure.Unreadable, "envelope: ${e.message}")
        }
        val candidate = envelope.optJSONArray("candidates")?.optJSONObject(0)
            ?: return@withContext failed(
                if (envelope.optJSONObject("promptFeedback")?.optString("blockReason").isNullOrEmpty()) StatementReadFailure.Rejected
                else StatementReadFailure.Blocked,
                "no candidate: ${body.take(400)}",
            )

        val finishReason = candidate.optString("finishReason")
        val textOutput = answerText(candidate)
        if (textOutput.isBlank()) {
            val reason = if (AiFailure.isBlockReason(finishReason)) StatementReadFailure.Blocked
            else truncatedOr(finishReason, StatementReadFailure.Unreadable)
            return@withContext failed(reason, "empty answer, finishReason=$finishReason")
        }

        val parsed = try {
            JSONObject(stripFence(textOutput))
        } catch (e: JSONException) {
            // A cut-off answer is invalid JSON; say that, rather than calling the statement unreadable.
            return@withContext failed(truncatedOr(finishReason, StatementReadFailure.Unreadable), "answer: ${e.message}")
        }

        val period = parsed.optString("statementPeriod", "")
        // Read rows arrive ticked unless something about them needs a second look.
        val items = StatementChecks.review(statementItems(parsed.optJSONArray("transactions"), choices), period)
        if (items.isEmpty()) {
            return@withContext failed(truncatedOr(finishReason, StatementReadFailure.NothingFound), "0 rows, finishReason=$finishReason")
        }
        StatementReadResult.Success(
            LocalStatementParser.buildStatementGroups(
                parsed.optString("bankOrApp", "Bank / UPI Statement"),
                period,
                items,
            )
        )
    }

    private fun statementItems(txArray: JSONArray?, choices: ParseChoices): List<StatementTransactionItem> {
        val items = mutableListOf<StatementTransactionItem>()
        for (i in 0 until (txArray?.length() ?: 0)) {
            val itemObj = txArray?.optJSONObject(i) ?: continue
            // A string amount ("1,299.00") used to become 0 through optDouble and still be listed,
            // pre-ticked, as a ₹0 row. Read it properly, and leave out a row with no money in it.
            val amount = AiParse.amountField(itemObj.opt("amount"))
            if (amount <= 0) continue
            val rawDate = itemObj.optString("date", "")
            val (timestamp, formattedDate, monthName) = LocalStatementParser.parseDateToTimestamp(rawDate)
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val monthSortKey = String.format(java.util.Locale.US, "%04d%02d", cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
            val type = itemObj.optString("type", "expense")

            items.add(
                StatementTransactionItem(
                    date = timestamp,
                    dateFormatted = formattedDate,
                    monthYearKey = monthName,
                    monthSortKey = monthSortKey,
                    type = if (type.equals("income", ignoreCase = true)) "income" else "expense",
                    amount = amount,
                    merchant = itemObj.optString("merchant", "Merchant"),
                    category = choices.categoryFor(type, itemObj.optString("category")),
                    paymentMethod = choices.paymentFor(itemObj.optString("paymentMethod", "UPI")),
                    note = itemObj.optString("note", ""),
                    isSelected = true,
                    flag = if (LocalStatementParser.readsAsDate(rawDate)) null else StatementChecks.NO_DATE,
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
        Do not include opening or closing balances, running-balance columns, page totals or summary
        rows, and never merge two rows into one or split one into two. If a row's date has no year,
        take it from the statement period.

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
        // 1600, not 1200: a tall payment screenshot or a long receipt scaled to 1200 on its long side
        // shrank the small print (the total, the date) past what reads reliably.
        val scaled = if (bitmap.width > 1600 || bitmap.height > 1600) {
            val maxDim = maxOf(bitmap.width, bitmap.height)
            val scale = 1600f / maxDim
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
        /** Short text lines: an entry should be back in seconds or not at all (the phone guesses). */
        internal const val TEXT_TIMEOUT_SECONDS = 25L

        /** One photo up and a short answer back, on a phone network. */
        internal const val RECEIPT_TIMEOUT_SECONDS = 45L

        /** An Insights or trip answer: a thinking model writing a paragraph. */
        internal const val ASK_TIMEOUT_SECONDS = 60L

        /**
         * The model every call uses. Was gemini-3.5-flash, which Google now lists as legacy: on
         * 2026-09-27 it took 15-18 s to parse "dinner 1,299 with Hist yesterday gpay" even with
         * thinking off, and 20 s for a receipt, when it answered at all (503 "high demand" was
         * common). gemini-3.6-flash answered the same in 3-5 s and the receipt in 2 s.
         */
        private const val MODEL = "gemini-3.6-flash"

        /** Where a call goes when [MODEL] is overloaded or rate-limited: 1-3 s, separate quota. */
        private const val FALLBACK_MODEL = "gemini-3.5-flash-lite"

        private val RETRY_ON_FALLBACK = setOf(AiFailure.ServerError, AiFailure.RateLimited)

        /** Thinking for quick reads (a typed line, a receipt): enough to get dates and splits right. */
        private const val QUICK_THINKING = "low"
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

        /**
         * The budget for one [ask] answer — and the whole reason the AI answers were broken.
         *
         * This was 2048, which looks generous for four sentences until you notice that
         * `maxOutputTokens` on a thinking model counts the model's *thinking* as well as its
         * answer. The harder the question, the more of the budget the thinking took and the less
         * was left to write with, which is exactly the pattern that was reported: "This month"
         * mostly came back but stopped mid-sentence, and "What to change" — three findings, each
         * justified against three months of history — usually spent the entire allowance thinking
         * and returned no text at all, which this class then reported as a failure.
         *
         * One cause, two symptoms. The answers themselves are 200-400 tokens; the rest is headroom
         * for thought, and [AiAnswerText] handles the tail if a question ever outgrows even this.
         */
        private const val ASK_MAX_OUTPUT_TOKENS = 8192

        // The statement call derives its own longer-timeout client from this one with
        // `newBuilder()`, so an interceptor added here reaches every Gemini request there is.
        private fun defaultClient(appIdentity: AppIdentity?): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(AppIdentity.restrictionInterceptor(appIdentity))
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

/** What reading a typed or spoken line produced. [failure] is set only when the AI was tried and not used. */
data class TextParseOutcome(val result: ParsedTransactionResult, val failure: AiFailure?)

/** What reading a bill or payment screenshot produced. */
sealed interface ReceiptReadResult {
    /** [missingAmount]: it read the image but found no total, so the amount needs filling in. */
    data class Read(val entry: ParsedTransactionResult, val missingAmount: Boolean) : ReceiptReadResult
    data class Failed(val reason: AiFailure) : ReceiptReadResult
}

/**
 * JSON inside a Markdown fence ("```json ... ```"). responseMimeType asks for bare JSON, but a
 * model can still wrap it, and a fenced answer failed to parse and read as a broken one.
 */
internal fun stripFence(text: String): String {
    val t = text.trim()
    if (!t.startsWith("```")) return t
    return t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
}
