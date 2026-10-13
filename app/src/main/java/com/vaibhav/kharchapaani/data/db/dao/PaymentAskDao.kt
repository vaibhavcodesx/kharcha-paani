package com.vaibhav.kharchapaani.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.vaibhav.kharchapaani.data.db.entity.PaymentAskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentAskDao {
    @Insert
    suspend fun insert(ask: PaymentAskEntity): Long

    /** Newest first, so the first row per friend is their latest ask. */
    @Query("SELECT * FROM payment_asks ORDER BY sentAt DESC, id DESC")
    fun observeAll(): Flow<List<PaymentAskEntity>>
}
