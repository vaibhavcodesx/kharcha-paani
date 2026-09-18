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
    /** What happened. Aggregates plus the daily series, so it can say something a total cannot. */
    MonthInReview("Month in review", "This month"),

    /** Diagnostic, and the one question a single month genuinely cannot answer. Needs history. */
    Overspending("Where am I overspending?", "Against your last three months"),

    /** Advice. Kept apart from the diagnosis so a diagnosis doesn't always arrive with advice. */
    WhatToChange("What should I change?", "Three things, with figures"),

    /** A group's whole life. Lives on the group sheet — asking it from Insights would need a picker. */
    TripReview("How did this trip go?", "This group, start to finish"),
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
- Name figures. "Food was Rs 2,009" beats "you spent a lot on food". A number with nothing beside
  it is trivia, so compare it to something in the data.
- Indian English and rupees. Write amounts as Rs 2,009 or Rs 2,009.50. Never use dollars.
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
        month of data, say that this is too little history to compare against, and stop.
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
        carried the cost. Use people's names, they are in the data. Say plainly if it was good value
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
