package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction

/**
 * Everyone's position inside one group, not just positions relative to "me": in a shared group,
 * groupmates' expenses and settlements between two other people move balances too.
 */
object GroupBalances {

    /**
     * Net position per person id in paisa-exact rupees: positive means the group owes them,
     * negative means they owe the group. Positions always sum to zero.
     *
     * An expense counts only through its split (the payer is credited the total, each share holder
     * is debited their share). A group expense with no split is the payer's own spending and moves
     * nobody's balance.
     */
    fun netPositions(expenses: List<FullTransaction>, settlements: List<SettlementEntity>): Map<Long, Double> {
        val paisa = mutableMapOf<Long, Long>()
        fun add(id: Long, amount: Long) {
            paisa[id] = (paisa[id] ?: 0L) + amount
        }

        for (expense in expenses) {
            val split = expense.split ?: continue
            if (expense.transaction.type.equals("income", ignoreCase = true)) continue
            // Summed per person: a host's guests are separate rows under the host (design item 9),
            // and each is theirs to cover.
            val shares = expense.shares.groupBy { it.personId }.mapValues { (_, rows) -> rows.sumOf { SplitRounding.toPaisa(it.shareAmount) } }
            // The payer is credited what the shares actually cover, so the positions stay balanced
            // even for a legacy local split whose shares don't add up to its total.
            add(split.paidByPersonId, shares.values.sum())
            shares.forEach { (id, share) -> add(id, -share) }
        }
        // Only a confirmed settlement moves anyone's position; a pending or rejected one is a claim.
        for (settlement in settlements.filter { it.isConfirmed }) {
            val amount = SplitRounding.toPaisa(settlement.amount)
            add(settlement.fromPersonId, amount)
            add(settlement.toPersonId, -amount)
        }
        return paisa.filterValues { it != 0L }.mapValues { (_, value) -> value / 100.0 }
    }
}
