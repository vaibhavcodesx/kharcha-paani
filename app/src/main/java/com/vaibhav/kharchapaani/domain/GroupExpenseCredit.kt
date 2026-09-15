package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction

/**
 * Who added a group expense and who paid for it, in one line. The group's activity used to say only
 * "Added by Priya", even when Rohan paid, although the payer is what the balances are computed from.
 * Both are shown when they differ ("Added by Priya · Paid by Rohan"); one when they're the same.
 */
object GroupExpenseCredit {

    fun line(expense: FullTransaction, members: List<PersonEntity>): String {
        val tx = expense.transaction
        val adder = tx.ownerProfileId?.let { owner -> members.firstOrNull { it.profileId == owner }?.let { Who.Member(it.id, it.name) } ?: Who.Former } ?: Who.Me
        val payer = expense.split?.paidByPersonId?.let { paidBy ->
            if (paidBy == ME) Who.Me else members.firstOrNull { it.id == paidBy }?.let { Who.Member(it.id, it.name) } ?: Who.Former
        } ?: adder

        return when {
            adder == payer && adder == Who.Me -> "You paid"
            adder == payer -> "Paid by ${payer.asObject()}"
            else -> "Added by ${adder.asObject()} · Paid by ${payer.asObject()}"
        }
    }

    private sealed interface Who {
        data object Me : Who
        data object Former : Who
        data class Member(val id: Long, val name: String) : Who

        fun asObject(): String = when (this) {
            Me -> "you"
            Former -> "a former member"
            is Member -> name
        }
    }

    private const val ME = -1L
}
