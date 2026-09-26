package com.vaibhav.kharchapaani.domain

import java.time.LocalDate

/**
 * The questions the AI can be asked. Four named ones rather than a chat box: a blank field makes
 * the person do the work of knowing what to ask, and every answer arrives cold. Each is a different
 * question over different data — if two would send nearly the same context, they should be one.
 *
 * Designed in `docs/ai-prompt-set-design.md` and approved 2026-09-19.
 */
enum class AiPrompt(val label: String, val eyebrow: String) {
    // Labels are short on purpose: three long ones stack one per chip-row on a phone and eat the
    // card. The eyebrow above each answer carries what the question actually means.
    /** What happened. Aggregates plus the daily series, so it can say something a total cannot. */
    MonthInReview("This month", "How the month went"),

    /** Diagnostic, and the one question a single month genuinely cannot answer. Needs history. */
    Overspending("Overspending", "Against your last three months"),

    /** Advice. Kept apart from the diagnosis so a diagnosis doesn't always arrive with advice. */
    WhatToChange("What to change", "Three things, with figures"),

    /** A group's whole life. Lives on the group sheet — asking it from Insights would need a picker. */
    TripReview("Read this trip", "This group, start to finish"),
    ;

    /** The three that belong on Insights, in the order they appear. */
    companion object {
        val forInsights: List<AiPrompt> = listOf(MonthInReview, Overspending, WhatToChange)
    }
}

/**
 * The house rules every answer follows, prepended to each prompt.
 *
 * Specified rather than left to the model, because this is what decides whether the feature reads
 * as designed or generated. The template is Settings' own microcopy ("Signing out keeps everything
 * already on this phone"): plain, specific, tells you the thing you wanted to know.
 */
const val AI_VOICE = """
You are writing inside Kharcha Paani, an Indian expense-splitting app, for the person whose money
this is. Rules, all of them binding:
- Never state a number that is not in the data below. Do not estimate, extrapolate or round into a
  different figure. If the data cannot answer the question, say what is missing in one sentence.
- Name figures. "Food was ₹2,009" beats "you spent a lot on food". A number with nothing beside
  it is trivia, so compare it to something in the data.
- Indian English and rupees. Write amounts with the rupee sign and no space: ₹2,009 or ₹2,009.50.
  Never write "Rs", "Rs.", "INR" or a dollar sign.
- Write dates the way a person says them: "25 August", not "2026-08-25".
- Finish every sentence you start, and keep the whole answer under 120 words.
- No praise that isn't earned, and no scolding. If the month was expensive, say what happened.
- Plain sentences. No financial jargon, no emoji, no headings, no markdown.
- Do not greet, sign off, or mention that you are an AI.
"""

/** What a prompt asks for, on top of [AI_VOICE] and the data block. */
fun AiPrompt.instruction(possessive: String): String = when (this) {
    AiPrompt.MonthInReview ->
        """
        Write 2-4 sentences on how $possessive month went. Lead with the shape of it rather than
        the total, which is already on screen above you: whether it was steady or concentrated into
        a few days, and which days or categories carried it.
        """.trimIndent()

    AiPrompt.Overspending ->
        """
        Say where spending is above its own recent normal, comparing each category this month
        against the same category across the previous three months. 2-4 sentences. Name the
        categories that are genuinely up and the ones that are in line. If nothing is meaningfully
        up, say so plainly rather than inventing a concern. If there is less than one full previous
        month of data, say that this is too little history to compare against, and stop. If the
        data says this month is still in progress, remember its figures cover only the days so far:
        a category below its average may simply not have happened yet, so never call it "down" for
        that reason alone, and say that the month isn't over when it matters to the comparison.
        """.trimIndent()

    AiPrompt.WhatToChange ->
        """
        Suggest at most three specific changes, each naming a figure from the data. One short line
        each, no numbering. Do not suggest anything generic ("make a budget", "cook at home",
        "track your spending") — every suggestion must point at something in this data. If you can
        only find one or two specific things, give one or two.
        """.trimIndent()

    AiPrompt.TripReview ->
        """
        Write 3-5 sentences on how this group went, the way a friend who has seen the receipts
        would: whether it was run cheaply or expensively for its length, what stood out, and who
        carried the cost — who paid up front is in the data, separately from what each person's
        share came to; do not confuse the two. Use people's names, they are in the data. Say plainly if it was good value
        or if one day or category blew it. Do not moralise about anyone's spending — it is their
        trip. If the group has settled up, say where everyone landed.
        """.trimIndent()
}

/**
 * How many AI answers may be generated in a day.
 *
 * Owner decision, 2026-09-19: 20. The key is on a free tier for this volume, so the cap is a
 * backstop against a loop or a stuck retry rather than a budget.
 */
const val AI_DAILY_CAP = 20

/** The running count of answers generated on [day]. Resets by itself when the day changes. */
data class AiUsage(val day: LocalDate, val used: Int) {

    fun remaining(today: LocalDate): Int = if (day == today) (AI_DAILY_CAP - used).coerceAtLeast(0) else AI_DAILY_CAP

    fun canAsk(today: LocalDate): Boolean = remaining(today) > 0

    /** The count after one more answer, rolling over to a fresh day when the date has moved on. */
    fun afterAsking(today: LocalDate): AiUsage =
        if (day == today) copy(used = used + 1) else AiUsage(today, 1)

    companion object {
        val none = AiUsage(LocalDate.MIN, 0)
    }
}

/**
 * Why an AI call produced no answer. One "couldn't get an answer" covered all of these, and they
 * need different things from the person: waiting, reconnecting, or nothing at all because the
 * problem is on our side.
 */
enum class AiFailure {
    /** This build has no Gemini key. */
    NoKey,

    /** No connection, or it dropped mid-call. */
    Offline,

    /** The call didn't finish in time. */
    Timeout,

    /** HTTP 429 / RESOURCE_EXHAUSTED: per-minute rate or the key's free quota. */
    RateLimited,

    /** Google refused the key itself (invalid, or restricted to another app / API). */
    KeyRefused,

    /** Google's side failed (5xx). */
    ServerError,

    /** The model declined: a safety or policy block on the prompt or the answer. */
    Blocked,

    /** The answer ran out of room before any usable text. */
    Truncated,

    /** It answered, but with nothing usable (empty, or not the JSON asked for). */
    Unreadable,
    ;

    companion object {
        /**
         * An unsuccessful HTTP response, by status and Google's error body. Google puts the useful
         * part in `error.status` / `error.message`: API_KEY_INVALID arrives as a 400, a key
         * restricted to another app as a 403, quota as 429 RESOURCE_EXHAUSTED.
         */
        fun fromHttp(code: Int, body: String): AiFailure {
            val text = body.uppercase()
            return when {
                code == 429 || "RESOURCE_EXHAUSTED" in text -> RateLimited
                code == 401 || code == 403 || "API_KEY" in text || "PERMISSION_DENIED" in text -> KeyRefused
                code >= 500 -> ServerError
                else -> Unreadable
            }
        }

        /** A candidate's `finishReason` that means the model declined rather than finished. */
        fun isBlockReason(finishReason: String): Boolean =
            finishReason in setOf("SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII", "IMAGE_SAFETY")
    }
}

/** What asking one question produced. */
sealed interface AiAskResult {
    data class Answer(val text: String) : AiAskResult
    data class Failed(val reason: AiFailure) : AiAskResult

    val answerOrNull: String? get() = (this as? Answer)?.text
}

/** The line an Insights or trip answer shows instead of an answer. */
fun AiFailure.askMessage(): String = when (this) {
    AiFailure.NoKey -> "AI answers aren't set up in this copy of the app."
    AiFailure.Offline -> "You're offline. Connect and try again."
    AiFailure.Timeout -> "That took too long to answer. Try again."
    AiFailure.RateLimited -> "The AI is busy or out of free answers for now. Try again in a few minutes."
    AiFailure.KeyRefused -> "The AI service turned this copy of the app away. That's on our side, not yours."
    AiFailure.ServerError -> "The AI service is having trouble. Try again in a minute."
    AiFailure.Blocked -> "The AI wouldn't answer that one."
    AiFailure.Truncated -> "The answer came back cut off. Try again."
    AiFailure.Unreadable -> "The answer didn't come back readable. Try again."
}

/** The notice above the quick-add sheet when typed text had to be read on the phone instead. */
fun AiFailure.quickAddNotice(): String = when (this) {
    AiFailure.Offline -> "You're offline, so this is a best guess from your words. Check it before saving."
    AiFailure.RateLimited -> "The AI is busy right now, so this is a best guess from your words. Check it before saving."
    AiFailure.KeyRefused, AiFailure.NoKey -> "The AI isn't available in this copy, so this is a best guess from your words. Check it before saving."
    else -> "Couldn't reach the AI, so this is a best guess from your words. Check it before saving."
}

/** The notice above the quick-add sheet when a bill or screenshot couldn't be read. */
fun AiFailure.receiptNotice(): String = when (this) {
    AiFailure.NoKey, AiFailure.KeyRefused -> "Bill reading isn't available in this copy of the app. Fill in the details."
    AiFailure.Offline -> "You're offline, so the bill wasn't read. Fill in the details, or try again when you're connected."
    AiFailure.Timeout -> "Reading the bill took too long. Fill in the details, or try again."
    AiFailure.RateLimited -> "The AI is busy right now, so the bill wasn't read. Fill in the details, or try again in a few minutes."
    AiFailure.ServerError -> "The AI service is having trouble, so the bill wasn't read. Fill in the details, or try again."
    AiFailure.Blocked, AiFailure.Truncated, AiFailure.Unreadable ->
        "Couldn't read that bill. Fill in the details, or try a clearer photo."
}
