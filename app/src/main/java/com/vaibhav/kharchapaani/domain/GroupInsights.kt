package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt

/** One member's part of what a group has spent. */
data class MemberShare(
    val personId: Long,
    val name: String,
    val colorHex: String,
    val amount: Double,
    /** Of the group's total spending, 0..1. */
    val fraction: Float,
)

/** What a group's own screen shows: who spent what, who owes whom, and where the money went. */
object GroupInsights {

    const val ME = -1L

    /**
     * You are the brand amber, and no colour in the friend palette sits near it, so "You" can never
     * collide with a member on the donut or in an avatar stack.
     */
    const val MY_COLOR = "#FF7A3D"

    /**
     * Each member's share of the group's spending, largest first. A member's share is what the
     * splits say is theirs; an expense with no split is all the payer's, as the balances treat it.
     */
    fun memberShares(expenses: List<FullTransaction>, members: List<PersonEntity>, myName: String = "You"): List<MemberShare> {
        val paisa = mutableMapOf<Long, Long>()
        for (expense in expenses) {
            if (expense.transaction.type.equals("income", ignoreCase = true)) continue
            val split = expense.split
            if (split == null) {
                paisa[ME] = (paisa[ME] ?: 0L) + SplitRounding.toPaisa(expense.transaction.amount)
            } else {
                expense.shares.forEach { share ->
                    paisa[share.personId] = (paisa[share.personId] ?: 0L) + SplitRounding.toPaisa(share.shareAmount)
                }
            }
        }
        val total = paisa.values.sum().coerceAtLeast(0L)
        if (total == 0L) return emptyList()

        val names = members.associateBy { it.id }
        return paisa.filterValues { it > 0L }
            .map { (personId, amount) ->
                MemberShare(
                    personId = personId,
                    name = if (personId == ME) myName else names[personId]?.name ?: "Former member",
                    colorHex = if (personId == ME) MY_COLOR else names[personId]?.avatarColor ?: "#D5D8DC",
                    amount = amount / 100.0,
                    fraction = amount.toFloat() / total,
                )
            }
            .sortedWith(compareByDescending<MemberShare> { it.amount }.thenBy { it.personId })
    }

    /** Who owes whom inside this group, after settlements, in as few payments as the greedy pass finds. */
    fun owesWhom(expenses: List<FullTransaction>, settlements: List<SettlementEntity>, members: List<PersonEntity>): List<SimplifiedDebt> {
        val names = members.associateBy { it.id }
        return DebtSimplifier.simplifyPositions(GroupBalances.netPositions(expenses, settlements)) { personId ->
            if (personId == ME) "You" else names[personId]?.name ?: "Former member"
        }
    }

    /** The group's spending by category (everyone's, not just mine), largest first. */
    fun categoryTotals(expenses: List<FullTransaction>): List<CategorySummary> {
        val paisa = mutableMapOf<String, Long>()
        val counts = mutableMapOf<String, Int>()
        for (expense in expenses) {
            val tx = expense.transaction
            if (tx.type.equals("income", ignoreCase = true)) continue
            paisa[tx.category] = (paisa[tx.category] ?: 0L) + SplitRounding.toPaisa(tx.amount)
            counts[tx.category] = (counts[tx.category] ?: 0) + 1
        }
        val total = paisa.values.sum()
        if (total == 0L) return emptyList()
        return paisa.map { (category, amount) ->
            CategorySummary(
                category = category,
                amount = amount / 100.0,
                percentage = amount.toFloat() / total * 100f,
                count = counts[category] ?: 1,
            )
        }.sortedWith(compareByDescending<CategorySummary> { it.amount }.thenBy { it.category })
    }
}
