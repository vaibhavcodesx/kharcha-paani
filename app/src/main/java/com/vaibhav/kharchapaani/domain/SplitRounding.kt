package com.vaibhav.kharchapaani.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money rounding for shared splits (decision 6): every share is whole paisa and the shares add up
 * to the amount exactly, which the server's `save_group_expense` checks without rounding.
 */
object SplitRounding {

    /** Equal shares, rounded down to paisa, with the leftover paisa on the payer's share. */
    fun equal(amount: Double, participantIds: List<Long>, payerId: Long): Map<Long, Double> {
        require(participantIds.isNotEmpty()) { "an equal split needs at least one participant" }
        require(participantIds.toSet().size == participantIds.size) { "a participant is listed twice" }
        val total = paisa(amount)
        val each = total / participantIds.size
        return settleLeftover(total, participantIds.associateWith { each }, payerId)
    }

    /**
     * Rounds shares entered as exact amounts or percentages to paisa, then moves whatever paisa
     * that rounding gained or lost onto the payer's share. Gaps bigger than rounding (shares that
     * don't cover the amount) are the caller's to reject, so this refuses them.
     */
    fun normalize(amount: Double, shares: Map<Long, Double>, payerId: Long): Map<Long, Double> {
        require(shares.isNotEmpty()) { "a split needs at least one share" }
        val total = paisa(amount)
        val rounded = shares.mapValues { (_, share) -> paisa(share) }
        val drift = total - rounded.values.sum()
        require(kotlin.math.abs(drift) <= rounded.size) {
            "shares differ from the amount by more than rounding: ${toRupees(drift)}"
        }
        return settleLeftover(total, rounded, payerId)
    }

    /**
     * Shares proportional to [weights] (percentages that may not total exactly 100, such as
     * 33.3 x 3), rounded down to paisa with the leftover on the payer.
     */
    fun byWeights(amount: Double, weights: Map<Long, Double>, payerId: Long): Map<Long, Double> {
        require(weights.isNotEmpty() && weights.values.all { it >= 0 }) { "weights must be non-negative" }
        val totalWeight = weights.values.sum()
        require(totalWeight > 0) { "at least one weight must be positive" }
        val total = paisa(amount)
        val floored = weights.mapValues { (_, weight) ->
            BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(weight))
                .divide(BigDecimal.valueOf(totalWeight), 0, RoundingMode.FLOOR)
                .longValueExact()
        }
        return settleLeftover(total, floored, payerId)
    }

    fun toPaisa(rupees: Double): Long = paisa(rupees)

    private fun settleLeftover(total: Long, sharesInPaisa: Map<Long, Long>, payerId: Long): Map<Long, Double> {
        val leftover = total - sharesInPaisa.values.sum()
        // A payer who isn't in the split (paid purely for others) can't absorb it, so the largest
        // share does; ties go to the first listed, keeping the result deterministic.
        val absorber = if (payerId in sharesInPaisa) payerId else sharesInPaisa.maxBy { it.value }.key
        val adjusted = sharesInPaisa.mapValues { (id, share) -> if (id == absorber) share + leftover else share }
        require(adjusted.values.all { it >= 0 }) { "rounding would make a share negative" }
        return adjusted.mapValues { (_, share) -> toRupees(share) }
    }

    private fun paisa(rupees: Double): Long =
        BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

    private fun toRupees(paisa: Long): Double = BigDecimal.valueOf(paisa, 2).toDouble()
}
