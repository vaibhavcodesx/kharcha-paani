package com.vaibhav.kharchapaani.data.sync

import android.util.Log
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.ReceiptEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.receipts.CloudCopy
import com.vaibhav.kharchapaani.data.receipts.ReceiptRemote
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import io.github.jan.supabase.exceptions.RestException
import kotlinx.serialization.json.Json

/**
 * Bill photos to and from the cloud (docs/designs/bill-photo-sync.md). Runs after the ledger
 * engines in a sync pass, so every expense it looks at already has its server row.
 *
 * 1. Deletes queued by the phone (a photo taken off, an expense deleted).
 * 2. Uploads this account's photos that aren't up yet, a few per pass.
 * 3. Applies the sharing switch: the path on a group expense is what lets the split see it.
 * 4. Learns about photos others shared, as rows whose file comes down only when opened.
 *
 * Nothing here may stop the ledger from syncing, so the caller runs it after the pass counts.
 */
class ReceiptSyncEngine(
    private val db: AppDatabase,
    private val store: ReceiptStore,
    private val remote: ReceiptRemote,
    private val selfId: String,
    private val shareInSplits: Boolean,
    private val encode: (java.io.File) -> CloudCopy.Encoded? = { CloudCopy.encode(it) },
) {
    private val dao = db.receiptDao()

    suspend fun sync() {
        drainDeletes()
        val transactions = db.transactionDao().getAllTransactionsDirect().associateBy { it.id }
        upload(transactions)
        applySharing(transactions)
        learnShared(transactions)
    }

    private suspend fun drainDeletes() {
        val queued = ReceiptDeleteQueue.read(db)
        if (queued.isEmpty()) return
        // Only our own folder can be deleted; a groupmate's photo we merely saw just leaves this phone.
        remote.delete(queued.filter { it.startsWith("$selfId/") })
        ReceiptDeleteQueue.remove(db, queued)
    }

    private suspend fun upload(transactions: Map<Long, TransactionEntity>) {
        val waiting = dao.all().filter { it.remotePath == null }.mapNotNull { row ->
            val tx = transactions[row.transactionId] ?: return@mapNotNull null
            // Only expenses this account added, once the server has them (the path names their id).
            if (!tx.isOwnedByMe || tx.syncState != SyncState.SYNCED) return@mapNotNull null
            row to tx
        }
        for ((row, tx) in waiting.take(MAX_UPLOADS_PER_PASS)) {
            val file = store.file(row.fileName)
            if (!file.exists()) continue
            val copy = encode(file) ?: continue
            val path = CloudCopy.path(selfId, tx.remoteId, copy.extension)
            remote.upload(path, copy.bytes, copy.contentType)
            dao.setRemotePath(row.id, path)
            if (tx.groupId == null || shareInSplits) remote.setSharedPath(tx.remoteId, path)
        }
    }

    /** When the switch changed since it was last applied, set or clear the path on our group expenses. */
    private suspend fun applySharing(transactions: Map<Long, TransactionEntity>) {
        val applied = db.syncDao().meta(SHARING_APPLIED)?.toBooleanStrictOrNull()
        if (applied == shareInSplits) return
        dao.all().forEach { row ->
            val path = row.remotePath ?: return@forEach
            val tx = transactions[row.transactionId] ?: return@forEach
            if (tx.groupId == null || !tx.isOwnedByMe || !path.startsWith("$selfId/")) return@forEach
            remote.setSharedPath(tx.remoteId, if (shareInSplits) path else null)
        }
        db.syncDao().putMeta(SyncMetaEntity(SHARING_APPLIED, shareInSplits.toString()))
    }

    /**
     * Photos on expenses here that someone shared (or our own backup on a new phone) become rows with
     * the file still in the cloud. A groupmate's photo whose sharing was turned off leaves this phone.
     */
    private suspend fun learnShared(transactions: Map<Long, TransactionEntity>) {
        val rows = dao.all().groupBy { it.transactionId }
        // Groupmates' expenses every pass; our own only once per install, which is what brings our
        // backed-up photos back on a new phone. Asking about every expense every pass would cost a
        // request per hundred expenses each time.
        val restoredOnce = db.syncDao().meta(RESTORED_ONCE) == "true"
        val candidates = transactions.values.filter { tx ->
            tx.syncState == SyncState.SYNCED && tx.remoteId.isNotEmpty() &&
                ((tx.groupId != null && !tx.isOwnedByMe) || (!restoredOnce && rows[tx.id].isNullOrEmpty()))
        }
        val shared = if (candidates.isEmpty()) emptyMap() else remote.sharedPaths(candidates.map { it.remoteId })
        for (tx in candidates) {
            val path = shared[tx.remoteId]
            val local = rows[tx.id].orEmpty()
            if (path != null && local.isEmpty()) {
                dao.insert(ReceiptEntity(transactionId = tx.id, fileName = path.substringAfterLast('/'),
                    width = 0, height = 0, bytes = 0, remotePath = path))
            } else if (path == null) {
                // Someone else's photo that is no longer shared: respect that and let it go.
                local.filter { it.remotePath != null && !it.remotePath.startsWith("$selfId/") }.forEach {
                    dao.delete(it.id)
                    store.delete(listOf(it.fileName))
                }
            }
        }
        if (!restoredOnce) db.syncDao().putMeta(SyncMetaEntity(RESTORED_ONCE, "true"))
    }

    companion object {
        private const val TAG = "ReceiptSync"
        const val MAX_UPLOADS_PER_PASS = 10
        const val SHARING_APPLIED = "receipts_sharing_applied"
        const val RESTORED_ONCE = "receipts_restored_once"

        /** Runs [engine] without ever failing the sync pass it's part of. */
        suspend fun runQuietly(engine: ReceiptSyncEngine) {
            try {
                engine.sync()
            } catch (e: RestException) {
                Log.w(TAG, "Bill photos not synced this time (HTTP ${e.statusCode})")
            } catch (e: java.io.IOException) {
                Log.w(TAG, "Bill photos not synced this time (offline)")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Bill photos not synced this time", e)
            }
        }
    }
}

/** Cloud copies to delete on the next sync: kept in sync_meta so a delete made offline isn't lost. */
object ReceiptDeleteQueue {
    private const val KEY = "receipt_remote_deletes"

    suspend fun read(db: AppDatabase): List<String> =
        db.syncDao().meta(KEY)?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    suspend fun add(db: AppDatabase, paths: Collection<String>) {
        if (paths.isEmpty()) return
        val next = (read(db) + paths).distinct()
        db.syncDao().putMeta(SyncMetaEntity(KEY, Json.encodeToString(next)))
    }

    suspend fun remove(db: AppDatabase, paths: Collection<String>) {
        val next = read(db) - paths.toSet()
        db.syncDao().putMeta(SyncMetaEntity(KEY, Json.encodeToString(next)))
    }
}
