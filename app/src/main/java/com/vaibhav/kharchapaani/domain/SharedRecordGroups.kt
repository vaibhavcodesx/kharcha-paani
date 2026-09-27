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
        /**
         * For a split: friends with no account who come along as my guests (design item 9). Their
         * shares are mine to cover in the group and they owe them to me; they don't block syncing.
         */
        val guests: List<PersonEntity> = emptyList(),
    ) {
        val canSync: Boolean get() = withoutAccount.isEmpty() && groups.isNotEmpty()

        /** Preselected when there's no real choice to make. */
        val onlyGroup: GroupEntity? get() = groups.singleOrNull()
    }

    /**
     * @param splitPayerId set for a split: friends with no account may then come along as my guests,
     *   so only the members with an account need to be in the group. The payer is the exception: a
     *   guest can't have paid, so a split someone not on the app paid for stays on this phone.
     *   Settlements and IOUs pass null: they're between two people, and both must be members.
     */
    fun optionsFor(
        participantIds: Collection<Long>,
        people: List<PersonEntity>,
        groups: List<GroupWithDetails>,
        splitPayerId: Long? = null,
    ): Options {
        val others = participantIds.filter { it != ME }.toSet()
        val byId = people.associateBy { it.id }
        val withoutAccount = others.mapNotNull { byId[it] }.filter { it.profileId == null }
        val guestsAllowed = splitPayerId != null && byId[splitPayerId]?.profileId != null || splitPayerId == ME
        val guests = if (guestsAllowed) withoutAccount else emptyList()
        val mustBeMembers = others - guests.map { it.id }.toSet()
        val eligible = groups
            .filter { it.group.isShared && !it.group.isArchived }
            .filter { details -> mustBeMembers.all { id -> details.members.any { it.id == id } } }
            .map { it.group }
            .sortedByDescending { it.createdAt }
        return Options(
            groups = if (mustBeMembers.isEmpty()) emptyList() else eligible,
            withoutAccount = withoutAccount - guests.toSet(),
            involvesGroupmates = others.any { byId[it]?.profileId != null },
            guests = guests,
        )
    }

    private const val ME = -1L
}
