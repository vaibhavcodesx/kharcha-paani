package com.vaibhav.kharchapaani.data.sync

import android.util.Log
import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.dao.GroupSyncDao
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.PersonColours
import com.vaibhav.kharchapaani.domain.roundPaisa
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Splits and IOUs with friends who have no account, to and from this account's private tables
 * (`local_splits`, `local_settlements`). Runs after [TransactionSyncEngine], so a split's personal
 * expense is usually on the phone before the split that hangs off it is pulled. Not always: another
 * phone can save one between the two pulls, so a split that arrives first holds the cursor until
 * its expense does (see [pullPaged]).
 *
 * Only rows already PENDING are pushed. Nothing here ever turns a LOCAL_ONLY row into PENDING: the
 * history already on a phone stays there unless its owner opts in (docs/local-friend-sync-v2.md).
 *
 * Same conflict rules as the other engines: a change still queued here beats a pulled one, pushes
 * mark a row synced only if it wasn't edited in flight, and a row the server refuses is FAILED
 * rather than retried forever.
 */
class LocalLedgerSyncEngine(
    private val db: AppDatabase,
    private val remote: LocalLedgerRemote,
) {
    private val dao = db.groupSyncDao()
    private val syncDao = db.syncDao()

    suspend fun sync() {
        push()
        pull()
    }

    internal suspend fun push() {
        pushTombstones(PendingRemoteDeleteEntity.TYPE_LOCAL_SPLIT) { keys ->
            remote.retractLocalSplits(keys.map(::transactionIdOfSplitTombstone))
        }
        syncDao.pendingPersonalSplits(BATCH_SIZE).forEach { pushSplit(it) }
        pushTombstones(PendingRemoteDeleteEntity.TYPE_LOCAL_SETTLEMENT, remote::retractSettlements)
        syncDao.pendingLocalSettlements(BATCH_SIZE).forEach { pushSettlement(it) }
    }

    private suspend fun pushTombstones(type: String, send: suspend (List<String>) -> Unit) {
        val ids = syncDao.pendingDeletes(BATCH_SIZE, type)
        if (ids.isEmpty()) return
        try {
            send(ids)
        } catch (e: RowsRejectedException) {
            // Only RLS can refuse a retraction of our own rows, and retrying won't change its mind.
            Log.w(TAG, "Server refused to retract $type rows (${e.statusCode}): ${e.message}")
        }
        syncDao.clearPendingDeletes(ids)
    }

    private suspend fun pushSplit(tx: TransactionEntity) {
        val payload = buildSplit(tx)
        if (payload == null) {
            syncDao.markFailedIfUnchanged(tx.id, tx.updatedAt)
            return
        }
        try {
            remote.saveLocalSplit(payload.first, payload.second)
            syncDao.markSyncedIfUnchanged(tx.id, tx.updatedAt)
        } catch (e: RowsRejectedException) {
            Log.w(TAG, "Server rejected local split ${tx.remoteId} (${e.statusCode}): ${e.message}")
            syncDao.markFailedIfUnchanged(tx.id, tx.updatedAt)
        }
    }

    /**
     * Null when the split can't be stored as a private one: someone in it has an account (that
     * belongs in a shared group), or the shares don't add up to the paisa, which the server would
     * refuse anyway. Marked FAILED, never sent as a plain expense without its split.
     */
    @OptIn(ExperimentalTime::class)
    internal suspend fun buildSplit(tx: TransactionEntity): Pair<LocalTransactionPayload, LocalSplitPayload>? {
        if (tx.groupId != null || !tx.isSplit) return null
        val split = dao.splitForTransaction(tx.id) ?: return null
        val shares = dao.sharesForSplit(split.id)
        if (shares.isEmpty()) return null
        if (abs(roundPaisa(shares.sumOf { it.shareAmount }) - roundPaisa(tx.amount)) > 0.001) {
            Log.w(TAG, "${tx.remoteId}: shares don't add up to the amount")
            return null
        }
        val payer = nameOf(split.paidByPersonId) ?: return null
        val sharePayloads = shares.map { share ->
            LocalSharePayload(
                localName = (nameOf(share.personId) ?: return null).value,
                shareAmount = roundPaisa(share.shareAmount),
                percentage = share.percentage,
            )
        }
        return LocalTransactionPayload(
            id = tx.remoteId,
            type = tx.type,
            amount = roundPaisa(tx.amount),
            category = tx.category,
            merchant = tx.merchant,
            paymentMethod = tx.paymentMethod,
            date = Instant.fromEpochMilliseconds(tx.date).toString(),
            note = tx.note,
            createdAt = Instant.fromEpochMilliseconds(tx.createdAt).toString(),
        ) to LocalSplitPayload(
            id = split.remoteId,
            splitType = split.splitType,
            totalAmount = roundPaisa(tx.amount),
            paidByLocalName = payer.value,
            shares = sharePayloads,
        )
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun pushSettlement(settlement: SettlementEntity) {
        val from = nameOf(settlement.fromPersonId)
        val to = nameOf(settlement.toPersonId)
        if (from == null || to == null || (from.value == null && to.value == null)) {
            dao.markSettlementFailedIfUnchanged(settlement.id, settlement.updatedAt)
            return
        }
        val payload = LocalSettlementPayload(
            id = settlement.remoteId,
            fromLocalName = from.value,
            toLocalName = to.value,
            amount = roundPaisa(settlement.amount),
            isIou = settlement.isIou,
            date = Instant.fromEpochMilliseconds(settlement.date).toString(),
            note = settlement.note,
            createdAt = Instant.fromEpochMilliseconds(settlement.createdAt).toString(),
            deletedAt = null,
        )
        try {
            remote.upsertSettlements(listOf(payload))
            dao.markSettlementSyncedIfUnchanged(settlement.id, settlement.updatedAt)
        } catch (e: RowsRejectedException) {
            Log.w(TAG, "Server rejected local settlement ${settlement.remoteId} (${e.statusCode}): ${e.message}")
            dao.markSettlementFailedIfUnchanged(settlement.id, settlement.updatedAt)
        }
    }

    /** A wrapped name: `Name(null)` is the owner; a null result means "has an account, can't send". */
    @JvmInline
    private value class Name(val value: String?)

    private suspend fun nameOf(personId: Long): Name? {
        if (personId == ME) return Name(null)
        val person = dao.personById(personId) ?: return null
        if (person.profileId != null) return null
        return person.name.trim().takeIf { it.isNotEmpty() }?.let(::Name)
    }

    internal suspend fun pull() {
        pullPaged(SPLITS_CURSOR, remote::splitsChangedSince, { it.updatedAt }) { applySplit(it) }
        pullPaged(SETTLEMENTS_CURSOR, remote::settlementsChangedSince, { it.updatedAt }) { applySettlement(it); true }
    }

    /** False when the split has to wait for its expense, and be pulled again. */
    @OptIn(ExperimentalTime::class)
    private suspend fun applySplit(row: RemoteLocalSplit): Boolean {
        val tx = syncDao.transactionByRemoteId(row.transactionId) ?: run {
            // Nothing to hang the split on. Gone for good when it was retracted, or its expense was
            // deleted here; otherwise the expense hasn't arrived yet. A day on, it isn't coming.
            val settled = row.deletedAt != null || syncDao.hasPendingDelete(row.transactionId) ||
                Clock.System.now() - Instant.parse(row.updatedAt) > WAIT_FOR_EXPENSE
            Log.w(TAG, "local split ${row.id}: no expense ${row.transactionId} here${if (settled) "" else ", waiting for it"}")
            return settled
        }
        // A change still queued here wins, and goes up on the next push.
        if (tx.syncState != SyncState.SYNCED || tx.groupId != null) return true

        dao.deleteSplitForTransaction(tx.id)
        if (row.deletedAt != null) {
            if (tx.isSplit) dao.updateTransaction(tx.copy(isSplit = false))
            return true
        }
        val splitId = dao.insertSplit(
            SplitEntity(
                transactionId = tx.id,
                totalAmount = row.totalAmount,
                paidByPersonId = LocalPeople.idFor(dao, row.paidByLocalName),
                splitType = row.splitType,
                remoteId = row.id,
            )
        )
        dao.insertShares(
            row.shares.map {
                SplitShareEntity(
                    splitId = splitId,
                    personId = LocalPeople.idFor(dao, it.localName),
                    shareAmount = it.shareAmount,
                    percentage = it.percentage,
                )
            }
        )
        if (!tx.isSplit) dao.updateTransaction(tx.copy(isSplit = true))
        return true
    }

    private suspend fun applySettlement(row: RemoteLocalSettlement) {
        val local = dao.settlementByRemoteId(row.id)
        if (row.deletedAt != null) {
            if (local?.syncState == SyncState.SYNCED) dao.deleteSettlementIfSynced(local.id)
            return
        }
        if (local != null && local.syncState != SyncState.SYNCED) return
        if (local == null && syncDao.hasPendingDelete(row.id)) return

        val pulled = SettlementEntity(
            id = local?.id ?: 0L,
            fromPersonId = LocalPeople.idFor(dao, row.fromLocalName),
            toPersonId = LocalPeople.idFor(dao, row.toLocalName),
            amount = row.amount,
            date = parseTimestamp(row.date),
            note = row.note,
            isIou = row.isIou,
            groupId = null,
            createdAt = parseTimestamp(row.createdAt),
            remoteId = row.id,
            updatedAt = parseTimestamp(row.updatedAt),
            syncState = SyncState.SYNCED,
        )
        if (local == null) dao.insertSettlement(pulled) else dao.updateSettlement(pulled)
    }

    /**
     * Pulls rows changed since the cursor, oldest first, a page at a time. [apply] returns false for
     * a row it couldn't use yet; the cursor then stays at that row, so the next pull fetches it
     * again (`updated_at >= since`) instead of moving past it for good.
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun <T> pullPaged(
        cursorKey: String,
        fetch: suspend (String?, Int) -> List<T>,
        updatedAt: (T) -> String,
        apply: suspend (T) -> Boolean,
    ) {
        var since = syncDao.meta(cursorKey)?.let { (Instant.parse(it) - CURSOR_OVERLAP).toString() }
        // The oldest row still waiting, across every page of this pull.
        var waiting: Instant? = null
        repeat(MAX_PAGES) {
            val page = fetch(since, PAGE_SIZE)
            if (page.isEmpty()) return
            val newest = page.maxBy { Instant.parse(updatedAt(it)) }.let(updatedAt)
            db.withTransaction {
                page.forEach { row ->
                    if (!apply(row)) {
                        val at = Instant.parse(updatedAt(row))
                        if (waiting.let { it == null || at < it }) waiting = at
                    }
                }
                val held = waiting
                val current = syncDao.meta(cursorKey)
                if (held != null) {
                    syncDao.putMeta(SyncMetaEntity(cursorKey, held.toString()))
                } else if (current == null || Instant.parse(newest) > Instant.parse(current)) {
                    syncDao.putMeta(SyncMetaEntity(cursorKey, newest))
                }
            }
            if (page.size < PAGE_SIZE) return
            if (since != null && Instant.parse(newest) <= Instant.parse(since)) return
            since = newest
        }
    }

    companion object {
        const val SPLITS_CURSOR = "local_splits_pull_cursor"
        const val SETTLEMENTS_CURSOR = "local_settlements_pull_cursor"
        private const val ME = -1L
        private const val BATCH_SIZE = 200
        private const val PAGE_SIZE = 1000
        private const val MAX_PAGES = 50
        private val CURSOR_OVERLAP = 5.seconds
        /** How long a split waits for its expense before it's let go. The race it covers is seconds. */
        private val WAIT_FOR_EXPENSE = 1.days
        private const val TAG = "LocalLedgerSync"
    }
}

/**
 * Friends with no account, found by name the way the server matches them (trimmed, any case), or
 * created if this phone has never seen them — which is what a reinstall looks like. One helper, so
 * the group pull and the private-ledger pull can't drift apart on what "the same friend" means.
 */
internal object LocalPeople {
    /** Null or blank is the owner. */
    suspend fun idFor(dao: GroupSyncDao, name: String?): Long {
        val clean = name?.trim()?.takeIf { it.isNotEmpty() } ?: return -1L
        return dao.localPersonByName(clean)?.id
            ?: dao.insertPerson(PersonEntity(name = clean, avatarColor = PersonColours.stable(clean)))
    }
}
