package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.GroupWithDetails

/**
 * Where a split, settlement or IOU with other people can sync. The server only shares records inside
 * a shared group whose members include everyone involved (decision 1), so a record with a groupmate
 * needs one of those groups, and a record with a friend who has no account can only stay local.
 */
object SharedRecordGroups {

    data class Options(
        /** Active shared groups every other participant belongs to, newest first. */
        val groups: List<GroupEntity>,
        /** Participants who aren't on Kharcha Paani; while any are included, nothing can sync. */
        val withoutAccount: List<PersonEntity>,
        /** At least one other participant has an account, so a group decides whether this syncs. */
        val involvesGroupmates: Boolean,
    ) {
        val canSync: Boolean get() = withoutAccount.isEmpty() && groups.isNotEmpty()

        /** Preselected when there's no real choice to make. */
        val onlyGroup: GroupEntity? get() = groups.singleOrNull()
    }

    fun optionsFor(participantIds: Collection<Long>, people: List<PersonEntity>, groups: List<GroupWithDetails>): Options {
        val others = participantIds.filter { it != ME }.toSet()
        val byId = people.associateBy { it.id }
        val eligible = groups
            .filter { it.group.isShared && !it.group.isArchived }
            .filter { details -> others.all { id -> details.members.any { it.id == id } } }
            .map { it.group }
            .sortedByDescending { it.createdAt }
        return Options(
            groups = if (others.isEmpty()) emptyList() else eligible,
            withoutAccount = others.mapNotNull { byId[it] }.filter { it.profileId == null },
            involvesGroupmates = others.any { byId[it]?.profileId != null },
        )
    }

    private const val ME = -1L
}
