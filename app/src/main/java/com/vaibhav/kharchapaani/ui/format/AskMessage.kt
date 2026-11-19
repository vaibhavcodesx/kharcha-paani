package com.vaibhav.kharchapaani.ui.format

import android.content.Context
import com.vaibhav.kharchapaani.R
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

    data class Strings(
        val header: String = "Hey %1\$s, here's our hisaab:",
        val carriedOver: String = "• Carried over: %1\$s",
        val earlier: String = "• Earlier: %1\$s",
        val line: String = "• %1\$s",
        val total: String = "Total: %1\$s",
        val partial: String = "Asking %1\$s of the %2\$s for now.",
        val upi: String = "UPI: %1\$s",
        val footer: String = "Sent from Kharcha Paani",
        val theirShare: String = "%1\$s, %2\$s: your share %3\$s",
        val myShare: String = "%1\$s, %2\$s: my share of what you paid %3\$s",
        val asGuest: String = "%1\$s, %2\$s: your share as my guest %3\$s",
        val iSent: String = "I sent you, %1\$s: %2\$s",
        val theySent: String = "You sent me, %1\$s: %2\$s",
    ) {
        companion object {
            fun from(context: Context): Strings = Strings(
                header = context.getString(R.string.ask_message_header),
                carriedOver = context.getString(R.string.ask_message_carried_over),
                earlier = context.getString(R.string.ask_message_earlier),
                line = context.getString(R.string.ask_message_line),
                total = context.getString(R.string.ask_message_total),
                partial = context.getString(R.string.ask_message_partial),
                upi = context.getString(R.string.ask_message_upi),
                footer = context.getString(R.string.ask_message_footer),
                theirShare = context.getString(R.string.ask_message_their_share),
                myShare = context.getString(R.string.ask_message_my_share),
                asGuest = context.getString(R.string.ask_message_as_guest),
                iSent = context.getString(R.string.ask_message_i_sent),
                theySent = context.getString(R.string.ask_message_they_sent),
            )
        }
    }

    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /**
     * @param asked what this ask is for: the settle sheet's amount, which can be less than the
     *   whole [hisaab] (eng review D4). The lines always add up to the whole.
     * @param upiId the payer's own UPI ID, or null when they haven't added one.
     */
    fun build(
        friendName: String,
        hisaab: HisaabItemiser.Hisaab,
        asked: Double,
        upiId: String?,
        zone: ZoneId = ZoneId.systemDefault(),
        strings: Strings = Strings(),
    ): String =
        buildString {
            append(String.format(strings.header, firstName(friendName))).append('\n')
            if (hisaab.carriedOver <= -0.005) append(String.format(strings.carriedOver, Money.format(hisaab.carriedOver))).append('\n')
            if (abs(hisaab.earlier) >= 0.005) append(String.format(strings.earlier, Money.format(hisaab.earlier))).append('\n')
            hisaab.lines.forEach { append(String.format(strings.line, describe(it, zone, strings))).append('\n') }
            append(String.format(strings.total, Money.format(hisaab.total))).append('\n')
            if (abs(asked - hisaab.total) >= 0.005) {
                append(String.format(strings.partial, Money.format(asked), Money.format(hisaab.total))).append('\n')
            }
            upiId?.let { append(String.format(strings.upi, it)).append('\n') }
            append(strings.footer)
        }

    fun build(
        friendName: String,
        hisaab: HisaabItemiser.Hisaab,
        asked: Double,
        upiId: String?,
        context: Context,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = build(friendName, hisaab, asked, upiId, zone, Strings.from(context))

    /** "Asked today", "Asked yesterday", "Asked 3 days ago": calendar days, not 24-hour blocks. */
    fun askedLabel(context: Context, askedAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val days = ChronoUnit.DAYS.between(dateOf(askedAt, zone), dateOf(now, zone)).coerceAtLeast(0)
        return when (days) {
            0L -> context.getString(R.string.ask_status_today)
            1L -> context.getString(R.string.ask_status_yesterday)
            else -> context.getString(R.string.ask_status_days, days)
        }
    }

    private fun describe(line: HisaabItemiser.Line, zone: ZoneId, strings: Strings): String {
        val date = dateOf(line.date, zone).format(day)
        val amount = Money.format(line.amount)
        return when (line.kind) {
            HisaabItemiser.Kind.THEIR_SHARE -> String.format(strings.theirShare, line.title, date, amount)
            HisaabItemiser.Kind.MY_SHARE -> String.format(strings.myShare, line.title, date, amount)
            HisaabItemiser.Kind.AS_GUEST -> String.format(strings.asGuest, line.title, date, amount)
            HisaabItemiser.Kind.I_SENT -> String.format(strings.iSent, date, amount)
            HisaabItemiser.Kind.THEY_SENT -> String.format(strings.theySent, date, amount)
        }
    }

    private fun dateOf(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun firstName(name: String) = name.trim().substringBefore(' ').ifEmpty { name }
}
