package com.vaibhav.kharchapaani.data.sync

import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** In-memory stand-in for the Room store, with the same conditional-write semantics as SyncDao. */
class FakeSyncStore : TransactionSyncStore {
    val rows = linkedMapOf<Long, TransactionEntity>()
    val pendingDeletes = linkedSetOf<String>()
    val meta = mutableMapOf<String, String>()
    private var nextId = 1L

    fun add(row: TransactionEntity): TransactionEntity {
        val stored = row.copy(id = nextId++)
        rows[stored.id] = stored
        return stored
    }

    fun byRemoteId(remoteId: String) = rows.values.firstOrNull { it.remoteId == remoteId }

    override suspend fun <R> inTransaction(block: suspend () -> R): R = block()

    override suspend fun pendingUpserts(limit: Int) = rows.values
        .filter { it.syncState == SyncState.PENDING && it.groupId == null && !it.isSplit }
        .sortedBy { it.updatedAt }
        .take(limit)

    override suspend fun markSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long) =
        transitionIfUnchanged(id, expectedUpdatedAt, SyncState.SYNCED)

    override suspend fun markFailedIfUnchanged(id: Long, expectedUpdatedAt: Long) =
        transitionIfUnchanged(id, expectedUpdatedAt, SyncState.FAILED)

    private fun transitionIfUnchanged(id: Long, expectedUpdatedAt: Long, to: SyncState): Boolean {
        val row = rows[id] ?: return false
        if (row.updatedAt != expectedUpdatedAt || row.syncState != SyncState.PENDING) return false
        rows[id] = row.copy(syncState = to)
        return true
    }

    override suspend fun pendingDeletes(limit: Int) = pendingDeletes.take(limit)
    override suspend fun clearPendingDeletes(remoteIds: List<String>) {
        pendingDeletes.removeAll(remoteIds.toSet())
    }

    override suspend fun findByRemoteId(remoteId: String) = byRemoteId(remoteId)
    override suspend fun hasPendingDelete(remoteId: String) = remoteId in pendingDeletes
    override suspend fun insertFromRemote(entity: TransactionEntity) {
        add(entity)
    }

    override suspend fun updateFromRemoteIfSynced(localId: Long, remote: TransactionEntity): Boolean {
        val row = rows[localId] ?: return false
        if (row.syncState != SyncState.SYNCED) return false
        rows[localId] = remote.copy(id = localId)
        return true
    }

    override suspend fun deleteIfSynced(localId: Long): Boolean {
        if (rows[localId]?.syncState != SyncState.SYNCED) return false
        rows.remove(localId)
        return true
    }

    override suspend fun deleteMeta(key: String) {
        meta.remove(key)
    }

    override suspend fun getMeta(key: String) = meta[key]
    override suspend fun putMeta(key: String, value: String) {
        meta[key] = value
    }
}

/**
 * Behaves like Supabase for this table: the server stamps updated_at on every write (a strictly
 * increasing clock here), deletes are soft, and pulls are `updated_at >= since` oldest first.
 */
@OptIn(ExperimentalTime::class)
class FakeTransactionRemote(private val ownerId: String) : TransactionRemote {
    val server = linkedMapOf<String, RemoteTransaction>()
    var online = true
    var rejectIds = emptySet<String>()
    /** Deletes RLS refuses, e.g. an expense in a group that has since been archived. */
    var refuseDeleteIds = emptySet<String>()
    var onUpsert: suspend (List<TransactionUpload>) -> Unit = {}
    val upsertCalls = mutableListOf<List<String>>()
    private var clockMillis = 1_800_000_000_000L

    private fun tick(): String = Instant.fromEpochMilliseconds(clockMillis++).toString()

    override suspend fun upsert(rows: List<TransactionUpload>) {
        if (!online) throw IOException("offline")
        upsertCalls += rows.map { it.id }
        if (rows.any { it.id in rejectIds }) throw RowsRejectedException(400, "check constraint")
        onUpsert(rows)
        val stamp = tick()
        rows.forEach { up ->
            server[up.id] = RemoteTransaction(
                id = up.id, ownerId = up.ownerId, type = up.type, amount = up.amount,
                category = up.category, merchant = up.merchant, paymentMethod = up.paymentMethod,
                date = up.date, note = up.note, createdAt = up.createdAt,
                updatedAt = stamp, deletedAt = up.deletedAt,
            )
        }
    }

    override suspend fun softDelete(remoteIds: List<String>) {
        if (!online) throw IOException("offline")
        // One refused row fails the whole UPDATE, as in Postgres.
        if (remoteIds.any { it in refuseDeleteIds }) throw RowsRejectedException(403, "new row violates row-level security policy")
        val stamp = tick()
        remoteIds.forEach { id -> server[id]?.let { server[id] = it.copy(deletedAt = stamp, updatedAt = stamp) } }
    }

    override suspend fun changedSince(since: String?, limit: Int): List<RemoteTransaction> {
        if (!online) throw IOException("offline")
        return server.values
            .filter { it.ownerId == ownerId }
            .filter { since == null || Instant.parse(it.updatedAt) >= Instant.parse(since) }
            .sortedBy { Instant.parse(it.updatedAt) }
            .take(limit)
    }

    /** A change made on another device. */
    fun writeFromOtherDevice(row: RemoteTransaction) {
        server[row.id] = row.copy(updatedAt = tick())
    }

    fun deleteFromOtherDevice(id: String) {
        val stamp = tick()
        server[id] = server.getValue(id).copy(deletedAt = stamp, updatedAt = stamp)
    }
}

fun personalTransaction(
    merchant: String,
    syncState: SyncState = SyncState.PENDING,
    updatedAt: Long = 1L,
    amount: Double = 100.0,
) = TransactionEntity(
    type = "expense",
    amount = amount,
    category = "Food",
    merchant = merchant,
    paymentMethod = "UPI",
    date = 1_700_000_000_000L,
    createdAt = 1_700_000_000_000L,
    updatedAt = updatedAt,
    syncState = syncState,
)
