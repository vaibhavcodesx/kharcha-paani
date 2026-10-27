package com.vaibhav.kharchapaani.domain

import android.content.Context
import com.vaibhav.kharchapaani.R

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction

/**
 * Who added a group expense and who paid for it, in one line. The group's activity used to say only
 * "Added by Priya", even when Rohan paid, although the payer is what the balances are computed from.
 * Both are shown when they differ ("Added by Priya · Paid by Rohan"); one when they're the same.
 */
object GroupExpenseCredit {

    fun line(context: Context, expense: FullTransaction, members: List<PersonEntity>): String {
        val tx = expense.transaction
        val adder = tx.ownerProfileId?.let { owner -> members.firstOrNull { it.profileId == owner }?.let { Who.Member(it.id, it.name) } ?: Who.Former } ?: Who.Me
        val payer = expense.split?.paidByPersonId?.let { paidBy ->
            if (paidBy == ME) Who.Me else members.firstOrNull { it.id == paidBy }?.let { Who.Member(it.id, it.name) } ?: Who.Former
        } ?: adder

        return when {
            adder == payer && adder == Who.Me -> context.getString(R.string.group_description_self_paid)
            adder == payer -> context.getString(R.string.group_description_payer, payer.asObject(context))
            else -> context.getString(R.string.group_description_adder_payer, adder.asObject(context), payer.asObject(context))
        }
    }

    private sealed interface Who {
        data object Me : Who
        data object Former : Who
        data class Member(val id: Long, val name: String) : Who

        fun asObject(context: Context): String = when (this) {
            Me -> context.getString(R.string.group_description_you_object)
            Former -> context.getString(R.string.group_description_former)
            is Member -> name
        }
    }

    private const val ME = -1L
}
