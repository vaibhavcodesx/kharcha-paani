package com.vaibhav.kharchapaani.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.domain.UpiLink
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.SplitRounding
import java.util.UUID
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One push-then-pull pass for the signed-in account's shared groups, their expenses and their
 * settlements. Runs after [TransactionSyncEngine], which already pushes this account's transaction
 * tombstones (group expenses included: a soft delete is the same update either way).
 *
 * Same conflict rules as personal sync: last write to reach the server wins, and a change still
 * queued here beats anything pulled. "Me" is person -1 locally and [selfProfileId] remotely; every
 * other participant is a person row carrying their profile id.
 */
class GroupSyncEngine(
    private val db: AppDatabase,
    private val remote: GroupRemote,
    private val selfProfileId: String,
) {
    private val dao = db.groupSyncDao()
    private val syncDao = db.syncDao()

    suspend fun sync() {
        pushGroups()
        pushGroupExpenses()
        pushSettlements()
        pushSettlementDeletes()
        pullGroups()
        pullGroupExpenses()
        pullSettlements()
    }

    internal suspend fun pushGroups() {
        for (group in dao.pendingGroups()) {
            try {
                // Only the creator can write a group (RLS). Anyone else's queued edit is dropped by
                // marking it synced; the next pull restores the server's version.
                if (group.createdByProfileId == null || group.createdByProfileId == selfProfileId) {
                    remote.createGroup(group.remoteId, group.name, group.emoji)
                    remote.updateGroup(
                        id = group.remoteId,
                        name = group.name,
                        emoji = group.emoji,
                        description = group.description,
                        colorHex = group.colorHex,
                        archivedAt = group.archivedAt?.let(::isoTimestamp),
                    )
                }
                dao.markGroupSyncedIfUnchanged(group.id, group.updatedAt)
            } catch (e: RowsRejectedException) {
                Log.w(TAG, "Server rejected group ${group.remoteId} (${e.statusCode}): ${e.message}")
                dao.markGroupFailedIfUnchanged(group.id, group.updatedAt)
            }
        }
    }

    internal suspend fun pushGroupExpenses() {
        repeat(MAX_BATCHES) {
            val batch = dao.pendingGroupExpenses(BATCH_SIZE)
            if (batch.isEmpty()) return
            // Every row leaves PENDING (synced or failed) unless edited mid-push, so batches advance.
            batch.forEach { pushGroupExpense(it) }
            if (batch.size < BATCH_SIZE) return
        }
    }

    private suspend fun pushGroupExpense(tx: TransactionEntity) {
        val upload = buildGroupExpense(tx)
        if (upload == null) {
            syncDao.markFailedIfUnchanged(tx.id, tx.updatedAt)
            return
        }
        try {
            remote.saveGroupExpense(upload)
            syncDao.markSyncedIfUnchanged(tx.id, tx.updatedAt)
        } catch (e: RowsRejectedException) {
            Log.w(TAG, "Server rejected group expense ${tx.remoteId} (${e.statusCode}): ${e.message}")
            syncDao.markFailedIfUnchanged(tx.id, tx.updatedAt)
        }
    }

    /** Null when the expense can't be expressed on the server, e.g. a share for a local-only friend. */
    @OptIn(ExperimentalTime::class)
    internal suspend fun buildGroupExpense(tx: TransactionEntity): GroupExpenseUpload? {
        val group = tx.groupId?.let { dao.groupById(it) } ?: return null
        val split = dao.splitForTransaction(tx.id)
        val shares = split?.let { dao.sharesForSplit(it.id) }.orEmpty()
        val payerId = split?.paidByPersonId ?: ME

        val payerProfile = profileIdOf(payerId) ?: return null.also { Log.w(TAG, "${tx.remoteId}: payer has no profile") }
        val sharesByPerson = if (shares.isEmpty()) {
            // An unsplit group expense is the payer's own spending: one share, all theirs.
            mapOf(payerId to tx.amount)
        } else {
            if (shares.map { it.personId }.toSet().size != shares.size) return null
            shares.associate { it.personId to it.shareAmount }
        }
        val normalized = try {
            SplitRounding.normalize(tx.amount, sharesByPerson, payerId)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "${tx.remoteId}: ${e.message}")
            return null
        }
        val percentages = shares.associate { it.personId to it.percentage }
        val sharePayloads = normalized.map { (personId, amount) ->
            val profileId = profileIdOf(personId) ?: return null.also { Log.w(TAG, "${tx.remoteId}: a share holder has no profile") }
            SharePayload(profileId, money(amount), percentages[personId])
        }

        return GroupExpenseUpload(
            transaction = GroupTransactionPayload(
                id = tx.remoteId,
                type = tx.type,
                amount = money(tx.amount),
                category = tx.category,
                merchant = tx.merchant,
                paymentMethod = tx.paymentMethod,
                date = isoTimestamp(tx.date),
                note = tx.note,
                groupId = group.remoteId,
                createdAt = isoTimestamp(tx.createdAt),
            ),
            split = SplitPayload(
                id = split?.remoteId ?: UUID.randomUUID().toString(),
                paidBy = payerProfile,
                splitType = split?.splitType ?: "exact",
                totalAmount = money(tx.amount),
            ),
            shares = sharePayloads,
        )
    }

    internal suspend fun pushSettlements() {
        repeat(MAX_BATCHES) {
            val batch = dao.pendingSettlements(BATCH_SIZE)
            if (batch.isEmpty()) return
            val uploads = batch.mapNotNull { settlement ->
                val upload = buildSettlement(settlement)
                if (upload == null) dao.markSettlementFailedIfUnchanged(settlement.id, settlement.updatedAt)
                upload?.let { settlement to it }
            }
            try {
                if (uploads.isNotEmpty()) remote.upsertSettlements(uploads.map { it.second })
                uploads.forEach { (row, _) -> dao.markSettlementSyncedIfUnchanged(row.id, row.updatedAt) }
            } catch (e: RowsRejectedException) {
                // One bad row fails the whole request; resend individually so the rest still sync.
                uploads.forEach { (row, upload) ->
                    try {
                        remote.upsertSettlements(listOf(upload))
                        dao.markSettlementSyncedIfUnchanged(row.id, row.updatedAt)
                    } catch (single: RowsRejectedException) {
                        Log.w(TAG, "Server rejected settlement ${row.remoteId} (${single.statusCode}): ${single.message}")
                        dao.markSettlementFailedIfUnchanged(row.id, row.updatedAt)
                    }
                }
            }
            if (batch.size < BATCH_SIZE) return
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun buildSettlement(settlement: SettlementEntity): SettlementUpload? {
        val group = settlement.groupId?.let { dao.groupById(it) } ?: return null
        return SettlementUpload(
            id = settlement.remoteId,
            fromProfileId = profileIdOf(settlement.fromPersonId) ?: return null,
            toProfileId = profileIdOf(settlement.toPersonId) ?: return null,
            amount = money(settlement.amount).toDouble(),
            date = isoTimestamp(settlement.date),
            note = settlement.note,
            isIou = settlement.isIou,
            groupId = group.remoteId,
            createdAt = isoTimestamp(settlement.createdAt),
            deletedAt = null,
        )
    }

    internal suspend fun pushSettlementDeletes() {
        repeat(MAX_BATCHES) {
            val ids = syncDao.pendingDeletes(BATCH_SIZE, PendingRemoteDeleteEntity.TYPE_SETTLEMENT)
            if (ids.isEmpty()) return
            try {
                remote.softDeleteSettlements(ids)
            } catch (e: RowsRejectedException) {
                ids.forEach { id ->
                    try {
                        remote.softDeleteSettlements(listOf(id))
                    } catch (single: RowsRejectedException) {
                        // Refused for good (archived group, or no longer a member): drop it and pull
                        // settlements again so the row, which still exists, comes back here.
                        Log.w(TAG, "Server refused to retract settlement $id (${single.statusCode}): ${single.message}")
                        syncDao.deleteMeta(SETTLEMENTS_PULL_CURSOR)
                    }
                }
            }
            syncDao.clearPendingDeletes(ids)
            if (ids.size < BATCH_SIZE) return
        }
    }

    /**
     * Groups are few, so each pass reads them all: that catches joins, renames, archiving and
     * membership changes, and lets groups this account no longer belongs to be removed locally.
     */
    @OptIn(ExperimentalTime::class)
    internal suspend fun pullGroups() {
        // A creator who left can still read the group row, but it isn't theirs any more.
        val visible = remote.visibleGroups().filter { group -> group.members.any { it.profileId == selfProfileId } }
        var joinedGroup = false

        db.withTransaction {
            for (remoteGroup in visible) {
                val memberIds = remoteGroup.members
                    .filter { it.profileId != selfProfileId }
                    .map { upsertPerson(it.profileId, it.profiles) }
                val pulled = GroupEntity(
                    name = remoteGroup.name,
                    description = remoteGroup.description,
                    emoji = remoteGroup.emoji ?: DEFAULT_EMOJI,
                    colorHex = remoteGroup.colorHex,
                    createdAt = parseTimestamp(remoteGroup.createdAt),
                    remoteId = remoteGroup.id,
                    updatedAt = parseTimestamp(remoteGroup.updatedAt),
                    syncState = SyncState.SYNCED,
                    archivedAt = remoteGroup.archivedAt?.let(::parseTimestamp),
                    createdByProfileId = remoteGroup.createdBy,
                )
                val local = dao.groupByRemoteId(remoteGroup.id)
                val groupId = when {
                    local == null -> {
                        joinedGroup = true
                        dao.insertGroup(pulled)
                    }
                    local.syncState == SyncState.SYNCED -> local.id.also { dao.updateGroup(pulled.copy(id = it)) }
                    // A remoteId is never shared with a local-only group; nothing to reconcile.
                    local.syncState == SyncState.LOCAL_ONLY -> continue
                    // PENDING/FAILED: the local edit wins, but membership is the server's alone.
                    else -> local.id
                }
                dao.deleteMembers(groupId)
                dao.insertMembers((listOf(ME) + memberIds).map { GroupMemberEntity(groupId = groupId, personId = it) })
            }

            val visibleIds = visible.map { it.id }.toSet()
            for (group in dao.syncedGroups()) {
                if (group.remoteId !in visibleIds) purgeGroup(group.id)
            }
        }

        if (joinedGroup) {
            // A newly visible group's history predates the cursors, so pull everything again.
            // Applying a pulled row is idempotent, so re-reading known rows is only extra work.
            syncDao.deleteMeta(GROUP_EXPENSES_PULL_CURSOR)
            syncDao.deleteMeta(SETTLEMENTS_PULL_CURSOR)
        }
    }

    /** Removes a group this account can no longer see, with everything recorded in it. */
    internal suspend fun purgeGroup(groupId: Long) = db.withTransaction {
        dao.deleteTransactionsInGroup(groupId)
        dao.deleteSettlementsInGroup(groupId)
        dao.deleteGroup(groupId)
    }

    internal suspend fun pullGroupExpenses() = pullPaged(
        cursorKey = GROUP_EXPENSES_PULL_CURSOR,
        fetch = remote::groupExpensesChangedSince,
        updatedAt = { it.updatedAt },
        apply = { applyGroupExpense(it) },
    )

    internal suspend fun pullSettlements() = pullPaged(
        cursorKey = SETTLEMENTS_PULL_CURSOR,
        fetch = remote::settlementsChangedSince,
        updatedAt = { it.updatedAt },
        apply = { applySettlement(it) },
    )

    private suspend fun applyGroupExpense(row: RemoteGroupTransaction) {
        val group = dao.groupByRemoteId(row.groupId)?.takeIf { it.isShared } ?: return
        val local = syncDao.transactionByRemoteId(row.id)
        if (row.deletedAt != null) {
            if (local?.syncState == SyncState.SYNCED) syncDao.deleteIfSynced(local.id)
            return
        }
        // PENDING/FAILED: the local change wins. A queued delete must not be resurrected.
        if (local != null && local.syncState != SyncState.SYNCED) return
        if (local == null && syncDao.hasPendingDelete(row.id)) return

        val split = row.split
        val pulled = TransactionEntity(
            type = row.type,
            amount = row.amount,
            category = row.category,
            merchant = row.merchant,
            paymentMethod = row.paymentMethod,
            date = parseTimestamp(row.date),
            note = row.note,
            isSplit = split != null,
            groupId = group.id,
            createdAt = parseTimestamp(row.createdAt),
            remoteId = row.id,
            updatedAt = parseTimestamp(row.updatedAt),
            syncState = SyncState.SYNCED,
            ownerProfileId = row.ownerId.takeIf { it != selfProfileId },
        )
        val pulledSplit = split?.let {
            SplitEntity(
                transactionId = local?.id ?: 0L,
                totalAmount = it.totalAmount,
                paidByPersonId = personIdFor(it.paidBy),
                splitType = it.splitType,
                remoteId = it.id,
            )
        }
        val pulledShares = row.activeShares.map { share ->
            SplitShareEntity(
                splitId = 0L,
                personId = personIdFor(share.profileId),
                shareAmount = share.shareAmount,
                percentage = share.percentage,
            )
        }

        // Pull pages overlap and every edit re-sends the whole expense, so most pulls of a known
        // expense change nothing. Rewriting it anyway replaced its split and shares with new rows and
        // re-emitted every ledger flow on each sync.
        if (local != null && isUnchanged(local, pulled, pulledSplit, pulledShares)) return

        val transactionId = if (local == null) {
            syncDao.insertTransaction(pulled)
        } else {
            dao.updateTransaction(pulled.copy(id = local.id))
            dao.deleteSplitForTransaction(local.id)
            local.id
        }
        if (pulledSplit == null) return

        val splitId = dao.insertSplit(pulledSplit.copy(transactionId = transactionId))
        dao.insertShares(pulledShares.map { it.copy(splitId = splitId) })
    }

    /** updated_at alone moving (a bump with identical content) isn't a change worth writing. */
    private suspend fun isUnchanged(
        local: TransactionEntity,
        pulled: TransactionEntity,
        pulledSplit: SplitEntity?,
        pulledShares: List<SplitShareEntity>,
    ): Boolean {
        if (pulled.copy(id = local.id, updatedAt = local.updatedAt) != local) return false
        val localSplit = dao.splitForTransaction(local.id)
        if (pulledSplit == null || localSplit == null) return pulledSplit == null && localSplit == null
        if (pulledSplit.copy(id = localSplit.id, transactionId = localSplit.transactionId) != localSplit) return false
        fun key(share: SplitShareEntity) = Triple(share.personId, share.shareAmount, share.percentage)
        return dao.sharesForSplit(localSplit.id).groupingBy(::key).eachCount() == pulledShares.groupingBy(::key).eachCount()
    }

    private suspend fun applySettlement(row: RemoteSettlement) {
        val group = row.groupId?.let { dao.groupByRemoteId(it) }?.takeIf { it.isShared } ?: return
        val local = dao.settlementByRemoteId(row.id)
        if (row.deletedAt != null) {
            if (local?.syncState == SyncState.SYNCED) dao.deleteSettlementIfSynced(local.id)
            return
        }
        if (local != null && local.syncState != SyncState.SYNCED) return
        if (local == null && syncDao.hasPendingDelete(row.id)) return

        val pulled = SettlementEntity(
            fromPersonId = personIdFor(row.fromProfileId),
            toPersonId = personIdFor(row.toProfileId),
            amount = row.amount,
            date = parseTimestamp(row.date),
            note = row.note,
            isIou = row.isIou,
            groupId = group.id,
            createdAt = parseTimestamp(row.createdAt),
            remoteId = row.id,
            updatedAt = parseTimestamp(row.updatedAt),
            syncState = SyncState.SYNCED,
        )
        when {
            local == null -> dao.insertSettlement(pulled)
            pulled.copy(id = local.id, updatedAt = local.updatedAt) == local -> Unit
            else -> dao.updateSettlement(pulled.copy(id = local.id))
        }
    }

    /**
     * Same paging as [TransactionSyncEngine.pull]: `updated_at >= cursor` pages, a small overlap on
     * the first page for late-committing server transactions, idempotent re-application.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun <T> pullPaged(
        cursorKey: String,
        fetch: suspend (since: String?, limit: Int) -> List<T>,
        updatedAt: (T) -> String,
        apply: suspend (T) -> Unit,
    ) {
        var since = syncDao.meta(cursorKey)?.let { (Instant.parse(it) - CURSOR_OVERLAP).toString() }
        repeat(MAX_BATCHES) {
            val page = fetch(since, PULL_PAGE_SIZE)
            if (page.isEmpty()) return

            val newest = page.maxBy { Instant.parse(updatedAt(it)) }.let(updatedAt)
            db.withTransaction {
                page.forEach { apply(it) }
                val current = syncDao.meta(cursorKey)
                if (current == null || Instant.parse(newest) > Instant.parse(current)) {
                    syncDao.putMeta(SyncMetaEntity(cursorKey, newest))
                }
            }

            if (page.size < PULL_PAGE_SIZE) return
            if (since != null && Instant.parse(newest) <= Instant.parse(since)) {
                Log.w(TAG, "$cursorKey page didn't advance past $since; stopping this pass")
                return
            }
            since = newest
        }
    }

    private suspend fun profileIdOf(personId: Long): String? =
        if (personId == ME) selfProfileId else dao.personById(personId)?.profileId

    /** Someone who has left the group can still appear on old expenses; they get a placeholder row. */
    private suspend fun personIdFor(profileId: String): Long =
        if (profileId == selfProfileId) ME else dao.personByProfileId(profileId)?.id ?: upsertPerson(profileId, null)

    private suspend fun upsertPerson(profileId: String, profile: RemoteProfile?): Long {
        val name = profile?.displayName?.trim()?.takeIf { it.isNotEmpty() }
        val existing = dao.personByProfileId(profileId)
        if (existing == null) {
            return dao.insertPerson(
                PersonEntity(
                    name = name ?: FALLBACK_NAME,
                    avatarColor = AVATAR_COLORS[abs(profileId.hashCode() % AVATAR_COLORS.size)],
                    profileId = profileId,
                    avatarUrl = profile?.avatarUrl,
                    avatarEmoji = profile?.avatarEmoji,
                    // Re-validated rather than stored as sent: this string was typed on someone
                    // else's phone and ends up inside a upi://pay link.
                    upiId = profile?.upiId?.let(UpiLink::cleanId),
                )
            )
        }
        // A hidden profile (null) keeps the name we already know rather than blanking it.
        val upiId = profile?.upiId?.let(UpiLink::cleanId)
        val changed = name != existing.name || profile?.avatarUrl != existing.avatarUrl ||
            profile?.avatarEmoji != existing.avatarEmoji || upiId != existing.upiId
        if (name != null && changed) {
            dao.updatePersonProfile(existing.id, name, profile?.avatarUrl, profile?.avatarEmoji, upiId)
        }
        return existing.id
    }

    @OptIn(ExperimentalTime::class)
    private fun isoTimestamp(epochMillis: Long): String = Instant.fromEpochMilliseconds(epochMillis).toString()

    companion object {
        const val GROUP_EXPENSES_PULL_CURSOR = "group_expenses_pull_cursor"
        const val SETTLEMENTS_PULL_CURSOR = "settlements_pull_cursor"

        private const val ME = -1L
        internal const val BATCH_SIZE = 200
        internal const val PULL_PAGE_SIZE = 1000
        private const val MAX_BATCHES = 50
        private val CURSOR_OVERLAP = 5.seconds
        private const val DEFAULT_EMOJI = "👥"
        private const val FALLBACK_NAME = "Group member"
        // The same palette AddFriendSheet offers, so synced members look like local friends. Only
        // ever read when a person is first inserted, so changing it never recolours anyone already
        // on the phone.
        private val AVATAR_COLORS = listOf("#F2D06B", "#C6F24C", "#86E37F", "#5BD6B0", "#70C1FF", "#A79BF5", "#E092E8", "#FF8FB0")
        private const val TAG = "GroupSync"
    }
}
