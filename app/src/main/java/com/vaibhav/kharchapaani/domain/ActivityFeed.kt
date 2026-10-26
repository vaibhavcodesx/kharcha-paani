package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.ui.format.Money

/** Recent Activity: transactions and settlements/IOUs in one list, newest first. */
object ActivityFeed {

    const val ALL = "All"
    const val EXPENSES = "Expenses"
    const val INCOME = "Income"
    const val SPLITS = "Splits"

    fun build(
        transactions: List<FullTransaction>,
        settlements: List<SettlementEntity>,
        people: List<PersonEntity>,
        groups: List<GroupEntity>,
        query: String,
        filter: String,
    ): List<ActivityItem> {
        val term = query.trim()
        val names = people.associate { it.id to it.name }
        val ownerNames = people.mapNotNull { person -> person.profileId?.let { it to person.name } }.toMap()
        val groupNames = groups.associate { it.id to it.name }
        val archivedSharedGroups = groups.filter { it.isShared && it.isArchived }.map { it.id }.toSet()
        fun nameOf(id: Long) = if (id == ME) "You" else names[id] ?: "Friend"

        // Compare exact paisa, accepting the same grouping/rupee notation the app displays.
        // A sign describes direction on screen; searches find the magnitude in either direction.
        val amountText = term.replace(Regex("[₹,\\s]"), "")
        val unsigned = if (amountText.firstOrNull() in listOf('+', '-')) amountText.drop(1) else amountText
        val queryAmount = Money.parseInput(unsigned)
        fun matchesAmount(amount: Double) = queryAmount != null && amount.isFinite() &&
            roundPaisa(kotlin.math.abs(amount)) == queryAmount
        fun matchesPerson(id: Long?) = id != null && nameOf(id).contains(term, ignoreCase = true)

        val transactionItems = transactions
            .filter { fullTx ->
                val tx = fullTx.transaction
                val matchesQuery = term.isBlank() ||
                    tx.merchant.contains(term, ignoreCase = true) ||
                    tx.category.contains(term, ignoreCase = true) ||
                    tx.note.contains(term, ignoreCase = true) ||
                    matchesAmount(tx.amount) || matchesAmount(fullTx.myPersonalShare) ||
                    matchesPerson(fullTx.split?.paidByPersonId) ||
                    tx.ownerProfileId?.let(ownerNames::get)?.contains(term, ignoreCase = true) == true ||
                    fullTx.shares.any { share ->
                        matchesPerson(share.guestPersonId ?: share.personId) ||
                            share.guestName?.contains(term, ignoreCase = true) == true
                    }
                val matchesFilter = when (filter) {
                    EXPENSES -> tx.type == "expense" && !tx.isSplit
                    INCOME -> tx.type == "income"
                    SPLITS -> tx.isSplit
                    else -> true
                }
                matchesQuery && matchesFilter
            }
            .map { ActivityItem.Transaction(it, isReadOnly = !it.canEdit || it.transaction.groupId in archivedSharedGroups) }

        // Settlements and IOUs are money moving between people, so they belong with splits, not
        // with personal expenses or income.
        val settlementItems = if (filter == ALL || filter == SPLITS) {
            settlements
                .map {
                    ActivityItem.Settlement(
                        it, nameOf(it.fromPersonId), nameOf(it.toPersonId), it.groupId?.let(groupNames::get),
                        recordedByName = it.recordedByPersonId?.let { id -> if (id == ME) "You" else names[id] },
                    )
                }
                .filter { item ->
                    term.isBlank() ||
                        item.title.contains(term, ignoreCase = true) ||
                        item.settlement.note.contains(term, ignoreCase = true) ||
                        matchesAmount(item.settlement.amount) ||
                        item.groupName?.contains(term, ignoreCase = true) == true ||
                        (if (item.settlement.isIou) "iou" else "settlement").contains(term, ignoreCase = true)
                }
        } else {
            emptyList()
        }

        return (transactionItems + settlementItems)
            .sortedWith(compareByDescending<ActivityItem> { it.date }.thenByDescending { it.createdAt })
    }

    private const val ME = -1L
}
