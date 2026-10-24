package com.vaibhav.kharchapaani.ui.format

import com.vaibhav.kharchapaani.domain.HisaabItemiser
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * The message that asks a friend to pay you back (docs/designs/ask-to-pay.md). Neutral on purpose:
 * the lines do the asking, so it reads as "here's the hisaab", not as you chasing them. It's what
 * the settle sheet previews and exactly what gets shared; the payer edits it, if at all, in the app
 * they send it from (eng review D6).
 */
object AskMessage {

    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /**
     * @param asked what this ask is for: the settle sheet's amount, which can be less than the
     *   whole [hisaab] (eng review D4). The lines always add up to the whole.
     * @param upiId the payer's own UPI ID, or null when they haven't added one.
     */
    fun build(friendName: String, hisaab: HisaabItemiser.Hisaab, asked: Double, upiId: String?, zone: ZoneId = ZoneId.systemDefault()): String =
        buildString {
            append("Hey ").append(firstName(friendName)).append(", here's our hisaab:\n")
            if (hisaab.carriedOver <= -0.005) append("• Carried over: ").append(Money.format(hisaab.carriedOver)).append('\n')
            if (abs(hisaab.earlier) >= 0.005) append("• Earlier: ").append(Money.format(hisaab.earlier)).append('\n')
            hisaab.lines.forEach { append("• ").append(describe(it, zone)).append('\n') }
            append("Total: ").append(Money.format(hisaab.total)).append('\n')
            if (abs(asked - hisaab.total) >= 0.005) {
                append("Asking ").append(Money.format(asked)).append(" of the ").append(Money.format(hisaab.total)).append(" you owe.\n")
            }
            upiId?.let { append("UPI: ").append(it).append('\n') }
            append("Sent from Kharcha Paani")
        }

    /** "Asked today", "Asked yesterday", "Asked 3 days ago": calendar days, not 24-hour blocks. */
    fun askedLabel(askedAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val days = ChronoUnit.DAYS.between(dateOf(askedAt, zone), dateOf(now, zone)).coerceAtLeast(0)
        return when (days) {
            0L -> "Asked today"
            1L -> "Asked yesterday"
            else -> "Asked $days days ago"
        }
    }

    private fun describe(line: HisaabItemiser.Line, zone: ZoneId): String {
        val date = dateOf(line.date, zone).format(day)
        val amount = Money.format(line.amount)
        return when (line.kind) {
            HisaabItemiser.Kind.THEIR_SHARE -> "${line.title}, $date: your share $amount"
            HisaabItemiser.Kind.MY_SHARE -> "${line.title}, $date: my share of what you paid $amount"
            HisaabItemiser.Kind.AS_GUEST -> "${line.title}, $date: your share as my guest $amount"
            HisaabItemiser.Kind.I_SENT -> "I sent you, $date: $amount"
            HisaabItemiser.Kind.THEY_SENT -> "You sent me, $date: $amount"
        }
    }

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun firstName(name: String) = name.trim().substringBefore(' ').ifEmpty { name }
}
