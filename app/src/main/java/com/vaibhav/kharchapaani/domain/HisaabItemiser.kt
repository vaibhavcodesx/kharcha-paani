package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import kotlin.math.abs

/**
 * The lines behind what a friend owes you, for asking them to pay (docs/designs/ask-to-pay.md).
 *
 * Built from the friend's history rows ([com.vaibhav.kharchapaani.data.repository.LedgerRepository.getLedgerForPerson]),
 * which already carry guest shares and count a settlement only once it's confirmed, and which
 * PersonLedgerTest holds equal to the balance (eng review D7). Only the stretch since the friend was
 * last square is listed: older history is already settled and would only muddy the ask.
 */
object HisaabItemiser {

    /** More lines than this and the oldest fold into one "Earlier" line. */
    const val MAX_LINES = 8

    enum class Kind {
        /** You paid; this is their share. */
        THEIR_SHARE,

        /** They paid; this is your share, so it comes off. */
        MY_SHARE,

        /** They came along as your guest on a group expense. */
        AS_GUEST,

        /** Money you sent them (a payment back or a loan): adds to what they owe. */
        I_SENT,

        /** Money they sent you: comes off. */
        THEY_SENT,
    }

    /** One line: [amount] is how it moves what they owe you, so it's negative when it comes off. */
    data class Line(val title: String, val kind: Kind, val date: Long, val amount: Double)

    /**
     * What they owe, line by line. [carriedOver] is the balance at the point they were last square
     * or in credit (zero or negative), [earlier] the folded oldest lines; together with [lines] they
     * add up to [total], which is the friend's net balance.
     */
    data class Hisaab(val carriedOver: Double, val earlier: Double, val lines: List<Line>, val total: Double)

    /** The ask for a friend's history rows, or null when they don't owe you anything. */
    fun itemise(rows: List<PersonLedgerItem>, maxLines: Int = MAX_LINES): Hisaab? {
        val walk = walk(rows)
        if (walk.total < 0.005) return null
        val tail = walk.counted.drop(walk.start).map(::lineOf)
        val cut = (tail.size - (maxLines - 1)).takeIf { tail.size > maxLines } ?: 0
        return Hisaab(
            carriedOver = walk.carried,
            earlier = roundPaisa(tail.take(cut).sumOf { it.amount }),
            lines = tail.drop(cut),
            total = walk.total,
        )
    }

    /**
     * When the friend was last square or in credit, by the date of the row that got them there; null
     * if they never were. An ask made before then is about a debt that's since been cleared.
     */
    fun lastZeroAt(rows: List<PersonLedgerItem>): Long? {
        val walk = walk(rows)
        return if (walk.start == 0) null else walk.counted[walk.start - 1].date
    }

    private class Walk(val counted: List<PersonLedgerItem>, val start: Int, val carried: Double, val total: Double)

    private fun walk(rows: List<PersonLedgerItem>): Walk {
        // Rows that don't move the balance (a payment waiting to be confirmed, or marked not right)
        // aren't part of what's owed. Oldest first; the row id breaks a tie on the same date.
        val counted = rows.filter { abs(it.netChange) >= 0.005 }.sortedWith(compareBy({ it.date }, { it.id }))
        var running = 0.0
        var start = 0
        var carried = 0.0
        counted.forEachIndexed { index, row ->
            running = roundPaisa(running + row.netChange)
            if (running < 0.005) {
                start = index + 1
                carried = running
            }
        }
        return Walk(counted, start, carried, running)
    }

    private fun lineOf(row: PersonLedgerItem): Line {
        val kind = when {
            row.isSettlement -> if (row.netChange > 0) Kind.I_SENT else Kind.THEY_SENT
            row.id.startsWith("guest_") -> Kind.AS_GUEST
            row.netChange > 0 -> Kind.THEIR_SHARE
            else -> Kind.MY_SHARE
        }
        return Line(title = row.title, kind = kind, date = row.date, amount = row.netChange)
    }
}
