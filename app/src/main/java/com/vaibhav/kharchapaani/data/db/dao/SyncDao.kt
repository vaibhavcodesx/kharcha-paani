package com.vaibhav.kharchapaani.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sync bookkeeping for personal transactions. Step 3 only syncs rows with no group and no split;
 * that filter is repeated in every query as defence in depth, not just decided at write time.
 */
@Dao
interface SyncDao {

    @Query(
        "SELECT * FROM transactions WHERE syncState = 'PENDING' AND groupId IS NULL AND isSplit = 0 " +
            "ORDER BY updatedAt LIMIT :limit"
    )
    suspend fun pendingPersonalTransactions(limit: Int): List<TransactionEntity>

    /** Returns 0 if the row changed while its push was in flight, leaving it PENDING. */
    @Query("UPDATE transactions SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncState = 'PENDING'")
    suspend fun markSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long): Int

    @Query("UPDATE transactions SET syncState = 'FAILED' WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncState = 'PENDING'")
    suspend fun markFailedIfUnchanged(id: Long, expectedUpdatedAt: Long): Int

    @Query("SELECT * FROM transactions WHERE remoteId = :remoteId LIMIT 1")
    suspend fun transactionByRemoteId(remoteId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    /** Only overwrites rows with no unsent local change; a local edit made during a pull wins. */
    @Query(
        "UPDATE transactions SET type = :type, amount = :amount, category = :category, merchant = :merchant, " +
            "paymentMethod = :paymentMethod, date = :date, note = :note, createdAt = :createdAt, updatedAt = :updatedAt " +
            "WHERE id = :id AND syncState = 'SYNCED'"
    )
    suspend fun updateFromRemoteIfSynced(
        id: Long,
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        paymentMethod: String,
        date: Long,
        note: String,
        createdAt: Long,
        updatedAt: Long,
    ): Int

    @Query("DELETE FROM transactions WHERE id = :id AND syncState = 'SYNCED'")
    suspend fun deleteIfSynced(id: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingDelete(delete: PendingRemoteDeleteEntity)

    @Query("SELECT remoteId FROM pending_remote_deletes ORDER BY deletedAt LIMIT :limit")
    suspend fun pendingDeletes(limit: Int): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_remote_deletes WHERE remoteId = :remoteId)")
    suspend fun hasPendingDelete(remoteId: String): Boolean

    @Query("DELETE FROM pending_remote_deletes WHERE remoteId IN (:remoteIds)")
    suspend fun clearPendingDeletes(remoteIds: List<String>)

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    suspend fun meta(key: String): String?

    @Query("SELECT value FROM sync_meta WHERE `key` = :key")
    fun observeMeta(key: String): Flow<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMeta(meta: SyncMetaEntity)

    @Query(
        "SELECT (SELECT COUNT(*) FROM transactions WHERE syncState = 'PENDING' AND groupId IS NULL AND isSplit = 0) + " +
            "(SELECT COUNT(*) FROM pending_remote_deletes)"
    )
    fun observeUnsyncedChangeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions WHERE syncState = 'FAILED'")
    fun observeFailedCount(): Flow<Int>

    /** "Retry" for rows the server rejected, e.g. after a server-side fix. */
    @Query("UPDATE transactions SET syncState = 'PENDING' WHERE syncState = 'FAILED' AND groupId IS NULL AND isSplit = 0")
    suspend fun requeueFailed(): Int
}
