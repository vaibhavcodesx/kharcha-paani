package com.vaibhav.kharchapaani.data.sync

import android.util.Log
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * One push-then-pull pass for the signed-in account's personal transactions.
 *
 * Conflict rule (approved): whichever change reaches the server last wins, and a change still
 * queued on this device beats anything pulled. Pulled rows never overwrite a PENDING/FAILED row
 * or resurrect a row with a queued delete; pushes mark a row synced only if it wasn't edited
 * while the push was in flight.
 *
 * Every failure except [RowsRejectedException] propagates so the worker retries with backoff.
 * Rejected rows are isolated one by one and marked FAILED so a bad row can't block the queue.
 */
class TransactionSyncEngine(
    private val store: TransactionSyncStore,
    private val remote: TransactionRemote,
    private val ownerId: String,
) {

    suspend fun sync() {
        push()
        pull()
        // "Last synced" is SyncWorker's to stamp, once every engine has got through: stamped here,
        // it read "just now" while the group sync failed on every pass (correctness audit, 2026-10-24).
    }

    internal suspend fun push() {
        pushUpserts()
        pushDeletes()
    }

    private suspend fun pushUpserts() {
        repeat(MAX_PUSH_BATCHES) {
            val batch = store.pendingUpserts(PUSH_BATCH_SIZE)
            if (batch.isEmpty()) return
            try {
                remote.upsert(batch.map { it.toUpload(ownerId) })
                batch.forEach { store.markSyncedIfUnchanged(it.id, it.updatedAt) }
            } catch (e: RowsRejectedException) {
                // One bad row fails the whole request; resend individually so the rest still sync.
                batch.forEach { pushSingle(it) }
            }
            if (batch.size < PUSH_BATCH_SIZE) return
        }
    }

    private suspend fun pushSingle(row: TransactionEntity) {
        try {
            remote.upsert(listOf(row.toUpload(ownerId)))
            store.markSyncedIfUnchanged(row.id, row.updatedAt)
        } catch (e: RowsRejectedException) {
            Log.w(TAG, "Server rejected transaction ${row.remoteId} (${e.statusCode}): ${e.message}")
            store.markFailedIfUnchanged(row.id, row.updatedAt)
        }
    }

    private suspend fun pushDeletes() {
        repeat(MAX_PUSH_BATCHES) {
            val ids = store.pendingDeletes(PUSH_BATCH_SIZE)
            if (ids.isEmpty()) return
            try {
                remote.softDelete(ids)
            } catch (e: RowsRejectedException) {
                // One refused row fails the whole update; send them singly so the rest still go.
                ids.forEach { deleteSingle(it) }
            }
            store.clearPendingDeletes(ids)
            if (ids.size < PUSH_BATCH_SIZE) return
        }
    }

    /**
     * A refused delete is dropped rather than retried forever, which would stall every later sync.
     * The row still exists on the server, so group expenses are pulled again from the start and it
     * reappears here, matching what the other members see.
     */
    private suspend fun deleteSingle(remoteId: String) {
        try {
            remote.softDelete(listOf(remoteId))
        } catch (e: RowsRejectedException) {
            Log.w(TAG, "Server refused to delete transaction $remoteId (${e.statusCode}): ${e.message}")
            store.deleteMeta(GroupSyncEngine.GROUP_EXPENSES_PULL_CURSOR)
        }
    }

    /**
     * Pages use `updated_at >= cursor`: rows sharing the boundary timestamp are fetched again,
     * which is harmless because applying a row is idempotent. A single push writes at most
     * [PUSH_BATCH_SIZE] rows with one server timestamp, well under [PULL_PAGE_SIZE], so a
     * full page always moves the cursor forward.
     */
    @OptIn(ExperimentalTime::class)
    internal suspend fun pull() {
        val storedCursor = store.getMeta(PULL_CURSOR)
        // Back off the first page a little: a slow server transaction can commit after a later
        // one, with an earlier updated_at than the cursor already saved.
        var since = storedCursor?.let { (Instant.parse(it) - CURSOR_OVERLAP).toString() }

        repeat(MAX_PULL_PAGES) {
            val page = remote.changedSince(since, PULL_PAGE_SIZE)
            if (page.isEmpty()) return

            val newest = page.maxBy { Instant.parse(it.updatedAt) }.updatedAt
            store.inTransaction {
                page.forEach { apply(it) }
                val current = store.getMeta(PULL_CURSOR)
                if (current == null || Instant.parse(newest) > Instant.parse(current)) {
                    store.putMeta(PULL_CURSOR, newest)
                }
            }

            if (page.size < PULL_PAGE_SIZE) return
            if (since != null && Instant.parse(newest) <= Instant.parse(since)) {
                Log.w(TAG, "Pull page didn't advance past $since; stopping this pass")
                return
            }
            since = newest
        }
    }

    private suspend fun apply(row: RemoteTransaction) {
        val local = store.findByRemoteId(row.id)
        if (row.deletedAt != null) {
            if (local?.syncState == SyncState.SYNCED) store.deleteIfSynced(local.id)
            return
        }
        when {
            local == null -> if (!store.hasPendingDelete(row.id)) store.insertFromRemote(row.toSyncedEntity())
            local.syncState == SyncState.SYNCED -> store.updateFromRemoteIfSynced(local.id, row.toSyncedEntity())
            // PENDING/FAILED: the local change wins and goes up on the next push.
            // LOCAL_ONLY: a row that became a split/group expense here; its delete is queued.
            else -> Unit
        }
    }

    companion object {
        const val PULL_CURSOR = "transactions_pull_cursor"
        const val LAST_SYNCED_AT = "last_synced_at"

        internal const val PUSH_BATCH_SIZE = 200
        internal const val PULL_PAGE_SIZE = 1000
        private const val MAX_PUSH_BATCHES = 50
        private const val MAX_PULL_PAGES = 50
        private val CURSOR_OVERLAP = 5.seconds
        private const val TAG = "TransactionSync"
    }
}
