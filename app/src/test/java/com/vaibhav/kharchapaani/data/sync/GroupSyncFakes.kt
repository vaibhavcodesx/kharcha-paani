package com.vaibhav.kharchapaani.data.sync

import kotlinx.serialization.json.Json
import java.io.IOException
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Behaves like the Supabase schema for groups as far as sync can tell: updated_at is stamped on
 * every write by a strictly increasing clock, joining or leaving bumps the group, visibility follows
 * membership, save_group_expense enforces exact share sums and membership, and deletes are soft.
 */
@OptIn(ExperimentalTime::class)
class FakeGroupRemote(private val self: String) : GroupRemote {
    val groups = linkedMapOf<String, RemoteGroup>()
    val expenses = linkedMapOf<String, RemoteGroupTransaction>()
    val settlements = linkedMapOf<String, RemoteSettlement>()
    val savedExpenses = mutableListOf<GroupExpenseUpload>()
    val profiles = mutableMapOf<String, RemoteProfile>()
    var online = true
    /** Deleted accounts' tombstone names, as former_member_names hands them back. */
    val formerMembers = mutableMapOf<String, String>()

    override suspend fun formerMemberNames(profileIds: List<String>): List<RemoteProfile> {
        checkOnline()
        return profileIds.mapNotNull { id -> formerMembers[id]?.let { RemoteProfile(id = id, displayName = it) } }
    }
    private var clockMillis = 1_800_000_000_000L

    fun tick(): String = Instant.fromEpochMilliseconds(clockMillis++).toString()

    private fun checkOnline() {
        if (!online) throw IOException("offline")
    }

    private fun isMember(groupId: String, profileId: String) =
        groups[groupId]?.members?.any { it.profileId == profileId } == true

    private fun requireActiveMember(groupId: String) {
        val group = groups[groupId] ?: throw RowsRejectedException(403, "new row violates row-level security policy")
        if (!isMember(groupId, self) || group.archivedAt != null) {
            throw RowsRejectedException(403, "new row violates row-level security policy")
        }
    }

    override suspend fun visibleGroups(): List<RemoteGroup> {
        checkOnline()
        // RLS: members (and the creator) see the group; members are only listed to members.
        return groups.values
            .filter { isMember(it.id, self) || it.createdBy == self }
            // Profiles are embedded by a join, so they're read as they are now, not as they were at joining.
            .map { group -> if (isMember(group.id, self)) group.copy(members = group.members.map { it.copy(profiles = profiles[it.profileId]) }) else group.copy(members = emptyList()) }
    }

    override suspend fun createGroup(id: String, name: String, emoji: String?) {
        checkOnline()
        val existing = groups[id]
        if (existing != null) {
            if (existing.createdBy == self) return
            throw RowsRejectedException(409, "group id already in use")
        }
        if (name.isBlank() || name.length > 60) throw RowsRejectedException(400, "group name must be 1 to 60 characters")
        val stamp = tick()
        groups[id] = RemoteGroup(
            id = id, name = name, emoji = emoji, createdBy = self, createdAt = stamp, updatedAt = stamp,
            members = listOf(RemoteGroupMember(self, profiles[self])),
        )
    }

    override suspend fun updateGroup(id: String, name: String, emoji: String?, description: String, colorHex: String, archivedAt: String?) {
        checkOnline()
        val group = groups[id] ?: return
        if (group.createdBy != self) return // RLS: matches no rows
        groups[id] = group.copy(name = name, emoji = emoji, description = description, colorHex = colorHex, archivedAt = archivedAt, updatedAt = tick())
    }

    override suspend fun saveGroupExpense(expense: GroupExpenseUpload) {
        checkOnline()
        val tx = expense.transaction
        requireActiveMember(tx.groupId)
        val existing = expenses[tx.id]
        // The adder, or the payer keeping themselves as payer (migration payer_can_edit_group_expenses).
        val payerEdit = existing?.split?.paidBy == self && expense.split.paidBy == self
        if (existing != null && existing.ownerId != self && !payerEdit) throw RowsRejectedException(403, "row-level security")
        val shareTotal = expense.shares.fold(BigDecimal.ZERO) { acc, share -> acc + share.shareAmount }
        if (shareTotal.compareTo(tx.amount) != 0 || expense.split.totalAmount.compareTo(tx.amount) != 0) {
            throw RowsRejectedException(400, "shares ($shareTotal) must equal the amount (${tx.amount})")
        }
        // Staged migration group_expense_guests: each share is one member or one guest.
        if (expense.shares.any { (it.profileId == null) == (it.localName.isNullOrBlank()) }) {
            throw RowsRejectedException(400, "each share names one member or one guest")
        }
        if (!isMember(tx.groupId, expense.split.paidBy) || expense.shares.any { it.profileId != null && !isMember(tx.groupId, it.profileId) }) {
            throw RowsRejectedException(403, "row-level security")
        }
        // Only the host (the expense's owner) changes guests; anyone else sends them back as they are.
        fun guestKey(name: String?, amount: Double) = name!!.trim().lowercase() + "=" + BigDecimal.valueOf(amount).setScale(2)
        val guestsNow = existing?.split?.shares.orEmpty().filter { it.deletedAt == null && it.localName != null }.map { guestKey(it.localName, it.shareAmount) }.sorted()
        val guestsSent = expense.shares.filter { it.localName != null }.map { guestKey(it.localName, it.shareAmount.toDouble()) }.sorted()
        if (guestsNow != guestsSent && (existing?.ownerId ?: self) != self) throw RowsRejectedException(403, "only the member who added this expense can change their guests")
        savedExpenses += expense

        val stamp = tick()
        val previous = existing?.split
        val keptIds = expense.shares.mapNotNull { it.profileId }.toSet()
        val keptGuests = guestsSent.toSet()
        val removed = previous?.shares.orEmpty()
            .filter { it.deletedAt == null && if (it.localName != null) guestKey(it.localName, it.shareAmount) !in keptGuests else it.profileId !in keptIds }
            .map { it.copy(deletedAt = stamp) }
        val history = previous?.shares.orEmpty().filter { it.deletedAt != null }
        val split = RemoteSplit(
            id = previous?.id ?: expense.split.id,
            paidBy = expense.split.paidBy,
            splitType = expense.split.splitType,
            totalAmount = expense.split.totalAmount.toDouble(),
            shares = history + removed + expense.shares.mapIndexed { index, share ->
                RemoteShare(
                    id = previous?.shares?.firstOrNull { share.profileId != null && it.profileId == share.profileId && it.deletedAt == null }?.id
                        ?: "share-${share.profileId ?: share.localName}-$stamp-$index",
                    profileId = share.profileId,
                    localName = share.localName,
                    shareAmount = share.shareAmount.toDouble(),
                    percentage = share.percentage,
                )
            },
        )
        expenses[tx.id] = RemoteGroupTransaction(
            id = tx.id, ownerId = existing?.ownerId ?: self, type = tx.type, amount = tx.amount.toDouble(),
            category = tx.category, merchant = tx.merchant, paymentMethod = tx.paymentMethod, date = tx.date,
            note = tx.note, groupId = tx.groupId, createdAt = existing?.createdAt ?: tx.createdAt,
            updatedAt = stamp, deletedAt = null, splitJson = Json.encodeToJsonElement(RemoteSplit.serializer(), split),
        )
    }

    override suspend fun groupExpensesChangedSince(since: String?, limit: Int): List<RemoteGroupTransaction> {
        checkOnline()
        return expenses.values
            .filter { isMember(it.groupId, self) }
            .filter { since == null || Instant.parse(it.updatedAt) >= Instant.parse(since) }
            .sortedBy { Instant.parse(it.updatedAt) }
            .take(limit)
    }

    override suspend fun upsertSettlements(rows: List<SettlementUpload>) {
        checkOnline()
        rows.forEach { row ->
            requireActiveMember(row.groupId)
            val partyIsSelf = row.fromProfileId == self || row.toProfileId == self
            if (!partyIsSelf || row.fromProfileId == row.toProfileId ||
                !isMember(row.groupId, row.fromProfileId) || !isMember(row.groupId, row.toProfileId)
            ) {
                throw RowsRejectedException(403, "row-level security")
            }
        }
        val stamp = tick()
        rows.forEach { row ->
            val existing = settlements[row.id]
            settlements[row.id] = RemoteSettlement(
                id = row.id, fromProfileId = row.fromProfileId, toProfileId = row.toProfileId, amount = row.amount,
                date = row.date, note = row.note, isIou = row.isIou, groupId = row.groupId,
                createdAt = existing?.createdAt ?: row.createdAt, updatedAt = stamp, deletedAt = row.deletedAt,
                // Staged migration settlement_confirmation: a new one is pending, recorded by the
                // caller; a re-send leaves its status and recorder as they are.
                status = existing?.status ?: "pending",
                recordedBy = existing?.recordedBy ?: self,
                respondedAt = existing?.respondedAt,
                responseNote = existing?.responseNote,
            )
        }
    }

    override suspend fun softDeleteSettlements(remoteIds: List<String>) {
        checkOnline()
        val stamp = tick()
        // RLS: the new row must still be in an active group this account belongs to; one refusal fails the update.
        remoteIds.mapNotNull { settlements[it] }
            .filter { it.fromProfileId == self || it.toProfileId == self }
            .forEach { row -> row.groupId?.let { requireActiveMember(it) } }
        // Pending or rejected: only the recorder may withdraw it; the other party answers instead.
        remoteIds.mapNotNull { settlements[it] }
            .filter { (it.fromProfileId == self || it.toProfileId == self) && it.status != "confirmed" && it.recordedBy != null && it.recordedBy != self }
            .forEach { throw RowsRejectedException(403, "only the person who recorded this settlement can withdraw it") }
        remoteIds.forEach { id ->
            val row = settlements[id] ?: return@forEach
            if (row.fromProfileId == self || row.toProfileId == self) {
                settlements[id] = row.copy(deletedAt = stamp, updatedAt = stamp)
            }
        }
    }

    val answers = mutableListOf<Pair<String, Boolean>>()

    override suspend fun respondToSettlement(remoteId: String, confirm: Boolean, note: String?) {
        checkOnline()
        val row = settlements[remoteId]?.takeIf { it.deletedAt == null && (it.fromProfileId == self || it.toProfileId == self) }
            ?: throw RowsRejectedException(404, "settlement not found")
        if (row.status != "pending" || row.recordedBy == self) throw RowsRejectedException(400, "not yours to answer, or already answered")
        answers += remoteId to confirm
        val stamp = tick()
        settlements[remoteId] = row.copy(
            status = if (confirm) "confirmed" else "rejected", respondedAt = stamp, responseNote = note, updatedAt = stamp,
        )
    }

    override suspend fun settlementsChangedSince(since: String?, limit: Int): List<RemoteSettlement> {
        checkOnline()
        return settlements.values
            .filter { it.groupId != null && isMember(it.groupId!!, self) }
            .filter { since == null || Instant.parse(it.updatedAt) >= Instant.parse(since) }
            .sortedBy { Instant.parse(it.updatedAt) }
            .take(limit)
    }

    // ---- Changes made by other people ----

    fun profile(id: String, name: String?, emoji: String? = null) {
        profiles[id] = RemoteProfile(id, name, avatarEmoji = emoji)
    }

    fun groupCreatedBy(creator: String, id: String, name: String, vararg members: String) {
        val stamp = tick()
        groups[id] = RemoteGroup(
            id = id, name = name, createdBy = creator, createdAt = stamp, updatedAt = stamp,
            members = (listOf(creator) + members).distinct().map { RemoteGroupMember(it, profiles[it]) },
        )
    }

    fun join(groupId: String, profileId: String) {
        val group = groups.getValue(groupId)
        groups[groupId] = group.copy(members = group.members + RemoteGroupMember(profileId, profiles[profileId]), updatedAt = tick())
    }

    fun archive(groupId: String) {
        groups[groupId] = groups.getValue(groupId).copy(archivedAt = tick(), updatedAt = tick())
    }

    fun leave(groupId: String, profileId: String) {
        val group = groups.getValue(groupId)
        groups[groupId] = group.copy(members = group.members.filter { it.profileId != profileId }, updatedAt = tick())
    }

    fun expenseBy(
        owner: String,
        id: String,
        groupId: String,
        amount: Double,
        paidBy: String,
        shares: Map<String, Double>,
        removedShares: Map<String, Double> = emptyMap(),
        merchant: String = "Shack",
        localShares: Map<String, Double> = emptyMap(),
    ) {
        val stamp = tick()
        val split = RemoteSplit(
            id = "split-$id",
            paidBy = paidBy,
            splitType = "exact",
            totalAmount = amount,
            shares = shares.map { (profile, share) -> RemoteShare("share-$id-$profile", profileId = profile, shareAmount = share) } +
                removedShares.map { (profile, share) -> RemoteShare("removed-$id-$profile", profileId = profile, shareAmount = share, deletedAt = stamp) } +
                localShares.map { (name, share) -> RemoteShare("local-$id-$name", localName = name, shareAmount = share) },
        )
        expenses[id] = RemoteGroupTransaction(
            id = id, ownerId = owner, type = "expense", amount = amount, category = "Food", merchant = merchant,
            paymentMethod = "UPI", date = stamp, note = "", groupId = groupId, createdAt = stamp, updatedAt = stamp,
            splitJson = Json.encodeToJsonElement(RemoteSplit.serializer(), split),
        )
    }

    fun deleteExpense(id: String) {
        val stamp = tick()
        expenses[id] = expenses.getValue(id).copy(deletedAt = stamp, updatedAt = stamp)
    }

    /** A settlement someone else wrote. [recordedBy] set means a pending one waiting for an answer. */
    fun settlementBy(id: String, groupId: String, from: String, to: String, amount: Double, recordedBy: String? = null) {
        val stamp = tick()
        settlements[id] = RemoteSettlement(
            id = id, fromProfileId = from, toProfileId = to, amount = amount, date = stamp, groupId = groupId,
            createdAt = stamp, updatedAt = stamp,
            status = if (recordedBy != null) "pending" else "confirmed", recordedBy = recordedBy,
        )
    }

    /** The other party answers on their phone. */
    fun answeredBy(id: String, confirm: Boolean, note: String? = null) {
        val stamp = tick()
        settlements[id] = settlements.getValue(id).copy(
            status = if (confirm) "confirmed" else "rejected", respondedAt = stamp, responseNote = note, updatedAt = stamp,
        )
    }

    fun retractSettlement(id: String) {
        val stamp = tick()
        settlements[id] = settlements.getValue(id).copy(deletedAt = stamp, updatedAt = stamp)
    }
}
