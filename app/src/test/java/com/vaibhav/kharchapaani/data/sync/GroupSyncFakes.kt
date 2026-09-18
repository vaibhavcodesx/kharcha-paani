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
        if (!isMember(tx.groupId, expense.split.paidBy) || expense.shares.any { !isMember(tx.groupId, it.profileId) }) {
            throw RowsRejectedException(403, "row-level security")
        }
        savedExpenses += expense

        val stamp = tick()
        val previous = existing?.split
        val keptIds = expense.shares.map { it.profileId }.toSet()
        val removed = previous?.shares.orEmpty()
            .filter { it.deletedAt == null && it.profileId !in keptIds }
            .map { it.copy(deletedAt = stamp) }
        val history = previous?.shares.orEmpty().filter { it.deletedAt != null }
        val split = RemoteSplit(
            id = previous?.id ?: expense.split.id,
            paidBy = expense.split.paidBy,
            splitType = expense.split.splitType,
            totalAmount = expense.split.totalAmount.toDouble(),
            shares = history + removed + expense.shares.map { share ->
                RemoteShare(
                    id = previous?.shares?.firstOrNull { it.profileId == share.profileId && it.deletedAt == null }?.id ?: "share-${share.profileId}-$stamp",
                    profileId = share.profileId,
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
            settlements[row.id] = RemoteSettlement(
                id = row.id, fromProfileId = row.fromProfileId, toProfileId = row.toProfileId, amount = row.amount,
                date = row.date, note = row.note, isIou = row.isIou, groupId = row.groupId,
                createdAt = settlements[row.id]?.createdAt ?: row.createdAt, updatedAt = stamp, deletedAt = row.deletedAt,
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
        remoteIds.forEach { id ->
            val row = settlements[id] ?: return@forEach
            if (row.fromProfileId == self || row.toProfileId == self) {
                settlements[id] = row.copy(deletedAt = stamp, updatedAt = stamp)
            }
        }
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
    ) {
        val stamp = tick()
        val split = RemoteSplit(
            id = "split-$id",
            paidBy = paidBy,
            splitType = "exact",
            totalAmount = amount,
            shares = shares.map { (profile, share) -> RemoteShare("share-$id-$profile", profile, share) } +
                removedShares.map { (profile, share) -> RemoteShare("removed-$id-$profile", profile, share, deletedAt = stamp) },
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

    fun settlementBy(id: String, groupId: String, from: String, to: String, amount: Double) {
        val stamp = tick()
        settlements[id] = RemoteSettlement(
            id = id, fromProfileId = from, toProfileId = to, amount = amount, date = stamp, groupId = groupId,
            createdAt = stamp, updatedAt = stamp,
        )
    }

    fun retractSettlement(id: String) {
        val stamp = tick()
        settlements[id] = settlements.getValue(id).copy(deletedAt = stamp, updatedAt = stamp)
    }
}
