package com.vaibhav.kharchapaani.domain

/**
 * Tidying an answer before it reaches the screen.
 *
 * A model's output format can be asked for but never guaranteed, so the two things that must be
 * true of every answer — it reads in rupees, and it does not stop mid-sentence — are enforced here
 * instead of only in the prompt. Pure string work, so it is testable without a network.
 */
object AiAnswerText {

    /**
     * The answer as the screen should show it, or null when there is nothing worth showing.
     *
     * @param truncated the response came back with `finishReason: MAX_TOKENS`, so the last sentence
     *   is very likely a fragment.
     */
    fun tidy(raw: String, truncated: Boolean): String? {
        val text = rupees(raw.trim())
        if (text.isBlank()) return null
        return (if (truncated) toLastCompleteSentence(text) else text).takeIf { it.isNotBlank() }
    }

    /**
     * `Rs 2,009` / `Rs. 2009` / `INR 500` / `₹ 500` all become `₹2,009`.
     *
     * The rest of the app formats money one way, and an answer that says "Rs 4672" next to a screen
     * saying "₹4,672" reads like it came from somewhere else — which it did, and that is exactly
     * what shouldn't show. Only forms directly in front of a digit are touched, so a sentence that
     * happens to contain "Rs" as a word is left alone.
     */
    fun rupees(text: String): String = text
        .replace(CURRENCY_WORD, "₹")
        .replace(SYMBOL_THEN_SPACE, "₹")

    /**
     * Everything up to the last sentence that actually finished.
     *
     * A sentence ends at `.`, `!` or `?` followed by a space or the end of the text — which is why
     * the decimal point in "₹366.68" is not one, and why "…of ₹366.68. You also had" comes back as
     * "…of ₹366.68." rather than keeping the dangling clause.
     *
     * If nothing in the text ever finished, the text is returned whole: a fragment still says more
     * than an empty card.
     */
    fun toLastCompleteSentence(text: String): String {
        val end = SENTENCE_END.findAll(text).lastOrNull()?.range?.last ?: return text.trim()
        return text.substring(0, end + 1).trim()
    }

    /** `Rs`, `Rs.`, `INR` or `rupees`, immediately before an amount. */
    private val CURRENCY_WORD = Regex("""\b(?:Rs\.?|INR)\s*(?=\d)""", RegexOption.IGNORE_CASE)

    /** The symbol with a space after it, which reads as a gap in the middle of an amount. */
    private val SYMBOL_THEN_SPACE = Regex("""₹\s+(?=\d)""")

    private val SENTENCE_END = Regex("""[.!?](?=\s|$)""")
}
