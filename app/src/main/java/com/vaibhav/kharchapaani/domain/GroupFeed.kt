package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction

/** One line in a group's history: an expense, or money moving between two of its members. */
sealed interface GroupFeedEntry {
    val date: Long

    data class Expense(val transaction: FullTransaction) : GroupFeedEntry {
        override val date: Long get() = transaction.transaction.date
    }

    /** A settlement or an IOU. Both are the same row; [SettlementEntity.isIou] tells them apart. */
    data class Movement(val settlement: SettlementEntity) : GroupFeedEntry {
        override val date: Long get() = settlement.date
    }
}

/**
 * A group's expenses and its settlements as one list, newest first.
 *
 * The group sheet used to render `transactions` and nothing else, so a settlement recorded inside a
 * group — or an IOU — simply did not appear anywhere in it. The balances at the top of the sheet
 * *did* count them, which is the worst version of the problem: the figures moved and nothing on the
 * screen explained why.
 */
object GroupFeed {

    fun of(
        transactions: List<FullTransaction>,
        settlements: List<SettlementEntity>,
    ): List<GroupFeedEntry> =
        (transactions.map(GroupFeedEntry::Expense) + settlements.map(GroupFeedEntry::Movement))
            .sortedByDescending { it.date }

    /**
     * "Rohan paid you", "You paid Rohan", "Rohan lent you".
     *
     * Named from this account's side because that is whose sheet it is. A movement between two
     * other members — possible in a group of three or more — is written with both names.
     */
    fun describe(settlement: SettlementEntity, members: List<PersonEntity>, me: Long = ME): String {
        val nameOf = members.associate { it.id to it.name }
        fun name(id: Long) = if (id == me) "You" else nameOf[id] ?: "Someone"
        val from = name(settlement.fromPersonId)
        val to = name(settlement.toPersonId)

        return when {
            settlement.isIou && settlement.toPersonId == me -> "$from lent you"
            settlement.isIou && settlement.fromPersonId == me -> "You lent $to"
            settlement.isIou -> "$from lent $to"
            settlement.toPersonId == me -> "$from paid you"
            settlement.fromPersonId == me -> "You paid $to"
            else -> "$from paid $to"
        }
    }

    /** Money coming towards this account reads as positive, as everywhere else in the app. */
    fun isIncoming(settlement: SettlementEntity, me: Long = ME): Boolean =
        settlement.toPersonId == me

    private const val ME = -1L
}
