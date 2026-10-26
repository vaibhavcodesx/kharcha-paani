package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import java.util.Locale

enum class GroupSort(val label: String) {
    LatestActivity("Latest activity"), Name("Name"), OwedToYou("Owed to you"), YouOwe("You owe"), Newest("Newest");

    fun apply(groups: List<GroupWithDetails>): List<GroupWithDetails> {
        val order: Comparator<GroupWithDetails> = when (this) {
            LatestActivity -> compareByDescending<GroupWithDetails> { it.latestActivity() }
            Name -> compareBy { it.group.name.lowercase(Locale.ROOT) }
            OwedToYou -> compareByDescending { it.netBalanceForMe }
            YouOwe -> compareBy { it.netBalanceForMe }
            Newest -> compareByDescending { it.group.createdAt }
        }
        return groups.sortedWith(order.thenBy { it.group.id })
    }
}

private fun GroupWithDetails.latestActivity(): Long = maxOf(
    group.createdAt, group.updatedAt,
    transactions.maxOfOrNull { maxOf(it.transaction.createdAt, it.transaction.updatedAt) } ?: 0L,
    settlements.maxOfOrNull { maxOf(it.createdAt, it.updatedAt, it.respondedAt ?: 0L) } ?: 0L,
)
