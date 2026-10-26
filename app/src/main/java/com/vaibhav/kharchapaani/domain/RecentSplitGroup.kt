package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails

/** The last group with an expense shared by this account and [friend], ordered by when added. */
internal fun recentSplitGroup(groups: List<GroupWithDetails>, friend: PersonEntity): Long? =
    groups.asSequence()
        .filter { !it.group.isArchived }
        .filter { !it.group.isShared || friend.profileId == null || it.members.any { member -> member.id == friend.id } }
        .mapNotNull { group ->
            val latest = group.transactions.asSequence()
                .filter { it.transaction.type.equals("expense", ignoreCase = true) && it.transaction.isSplit && it.split != null }
                .filter { it.includesPerson(-1L) && it.includesPerson(friend.id) }
                .maxOfOrNull { it.transaction.createdAt }
            latest?.let { group.group.id to it }
        }
        .maxByOrNull { it.second }?.first

private fun FullTransaction.includesPerson(personId: Long): Boolean =
    split?.paidByPersonId == personId || shares.any {
        (if (it.isGuest) it.guestPersonId else it.personId) == personId
    }
