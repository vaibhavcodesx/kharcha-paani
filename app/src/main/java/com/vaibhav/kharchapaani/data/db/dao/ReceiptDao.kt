package com.vaibhav.kharchapaani.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vaibhav.kharchapaani.data.db.entity.ReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Insert
    suspend fun insert(receipt: ReceiptEntity): Long

    @Query("SELECT * FROM receipts WHERE transactionId = :transactionId ORDER BY id")
    suspend fun forTransaction(transactionId: Long): List<ReceiptEntity>

    @Query("DELETE FROM receipts WHERE transactionId = :transactionId")
    suspend fun deleteForTransaction(transactionId: Long)

    /** The expenses that have a bill photo, for the feed's icon. */
    @Query("SELECT DISTINCT transactionId FROM receipts")
    fun observeTransactionIds(): Flow<List<Long>>

    @Query("UPDATE receipts SET remotePath = :remotePath WHERE id = :id")
    suspend fun setRemotePath(id: Long, remotePath: String?)

    @Query("UPDATE receipts SET width = :width, height = :height, bytes = :bytes WHERE id = :id")
    suspend fun setDownloaded(id: Long, width: Int, height: Int, bytes: Long)

    @Query("DELETE FROM receipts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM receipts")
    suspend fun all(): List<ReceiptEntity>

    /** Every file a row still points at; anything else in the folder is an orphan. */
    @Query("SELECT fileName FROM receipts")
    suspend fun allFileNames(): List<String>
}
