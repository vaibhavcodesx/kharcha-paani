package com.vaibhav.kharchapaani.data.sync

import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity

/** The local side of sync, kept narrow so the engine's rules can be tested without SQLite. */
interface TransactionSyncStore {
    suspend fun <R> inTransaction(block: suspend () -> R): R
    suspend fun pendingUpserts(limit: Int): List<TransactionEntity>
    suspend fun markSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long): Boolean
    suspend fun markFailedIfUnchanged(id: Long, expectedUpdatedAt: Long): Boolean
    suspend fun pendingDeletes(limit: Int): List<String>
    suspend fun clearPendingDeletes(remoteIds: List<String>)
    suspend fun findByRemoteId(remoteId: String): TransactionEntity?
    suspend fun hasPendingDelete(remoteId: String): Boolean
    suspend fun insertFromRemote(entity: TransactionEntity)

    /** Applies a pulled row only if the local copy has no unsent change. */
    suspend fun updateFromRemoteIfSynced(localId: Long, remote: TransactionEntity): Boolean
    suspend fun deleteIfSynced(localId: Long): Boolean
    suspend fun getMeta(key: String): String?
    suspend fun putMeta(key: String, value: String)
}

class RoomTransactionSyncStore(private val db: AppDatabase) : TransactionSyncStore {
    private val dao = db.syncDao()

    override suspend fun <R> inTransaction(block: suspend () -> R): R = db.withTransaction { block() }
    override suspend fun pendingUpserts(limit: Int) = dao.pendingPersonalTransactions(limit)
    override suspend fun markSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long) =
        dao.markSyncedIfUnchanged(id, expectedUpdatedAt) == 1
    override suspend fun markFailedIfUnchanged(id: Long, expectedUpdatedAt: Long) =
        dao.markFailedIfUnchanged(id, expectedUpdatedAt) == 1
    override suspend fun pendingDeletes(limit: Int) = dao.pendingDeletes(limit)
    override suspend fun clearPendingDeletes(remoteIds: List<String>) = dao.clearPendingDeletes(remoteIds)
    override suspend fun findByRemoteId(remoteId: String) = dao.transactionByRemoteId(remoteId)
    override suspend fun hasPendingDelete(remoteId: String) = dao.hasPendingDelete(remoteId)
    override suspend fun insertFromRemote(entity: TransactionEntity) {
        dao.insertTransaction(entity)
    }
    override suspend fun updateFromRemoteIfSynced(localId: Long, remote: TransactionEntity) =
        dao.updateFromRemoteIfSynced(
            id = localId,
            type = remote.type,
            amount = remote.amount,
            category = remote.category,
            merchant = remote.merchant,
            paymentMethod = remote.paymentMethod,
            date = remote.date,
            note = remote.note,
            createdAt = remote.createdAt,
            updatedAt = remote.updatedAt,
        ) == 1
    override suspend fun deleteIfSynced(localId: Long) = dao.deleteIfSynced(localId) == 1
    override suspend fun getMeta(key: String) = dao.meta(key)
    override suspend fun putMeta(key: String, value: String) = dao.putMeta(SyncMetaEntity(key, value))
}
