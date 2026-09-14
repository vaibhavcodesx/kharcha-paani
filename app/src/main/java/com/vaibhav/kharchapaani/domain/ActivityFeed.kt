package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction

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
        val names = people.associate { it.id to it.name }
        val groupNames = groups.associate { it.id to it.name }
        fun nameOf(id: Long) = if (id == ME) "You" else names[id] ?: "Friend"

        val transactionItems = transactions
            .filter { fullTx ->
                val tx = fullTx.transaction
                val matchesQuery = query.isBlank() ||
                    tx.merchant.contains(query, ignoreCase = true) ||
                    tx.category.contains(query, ignoreCase = true) ||
                    tx.note.contains(query, ignoreCase = true)
                val matchesFilter = when (filter) {
                    EXPENSES -> tx.type == "expense" && !tx.isSplit
                    INCOME -> tx.type == "income"
                    SPLITS -> tx.isSplit
                    else -> true
                }
                matchesQuery && matchesFilter
            }
            .map { ActivityItem.Transaction(it) }

        // Settlements and IOUs are money moving between people, so they belong with splits, not
        // with personal expenses or income.
        val settlementItems = if (filter == ALL || filter == SPLITS) {
            settlements
                .map { ActivityItem.Settlement(it, nameOf(it.fromPersonId), nameOf(it.toPersonId), it.groupId?.let(groupNames::get)) }
                .filter { item ->
                    query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.settlement.note.contains(query, ignoreCase = true) ||
                        item.groupName?.contains(query, ignoreCase = true) == true ||
                        (if (item.settlement.isIou) "iou" else "settlement").contains(query, ignoreCase = true)
                }
        } else {
            emptyList()
        }

        return (transactionItems + settlementItems)
            .sortedWith(compareByDescending<ActivityItem> { it.date }.thenByDescending { it.createdAt })
    }

    private const val ME = -1L
}
