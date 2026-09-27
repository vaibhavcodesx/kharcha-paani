package com.vaibhav.kharchapaani.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity

/**
 * Sync bookkeeping for shared groups (step 4). Every query that picks rows to push repeats the
 * "shared group" condition, so a LOCAL_ONLY group's data can never be sent even if a row's own
 * state were wrong. Pulled rows are written inside a Room transaction after checking the local
 * state, so the read-check-write can't interleave with a local edit.
 */
@Dao
interface GroupSyncDao {

    @Query("SELECT * FROM groups WHERE syncState = 'PENDING' ORDER BY updatedAt")
    suspend fun pendingGroups(): List<GroupEntity>

    @Query("UPDATE groups SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncState = 'PENDING'")
    suspend fun markGroupSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long): Int

    @Query("UPDATE groups SET syncState = 'FAILED' WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncState = 'PENDING'")
    suspend fun markGroupFailedIfUnchanged(id: Long, expectedUpdatedAt: Long): Int

    @Query("SELECT * FROM groups WHERE syncState = 'SYNCED'")
    suspend fun syncedGroups(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun groupById(id: Long): GroupEntity?

    @Query("SELECT * FROM groups WHERE remoteId = :remoteId LIMIT 1")
    suspend fun groupByRemoteId(remoteId: String): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembers(groupId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM transactions WHERE groupId = :groupId")
    suspend fun deleteTransactionsInGroup(groupId: Long)

    @Query("DELETE FROM settlements WHERE groupId = :groupId")
    suspend fun deleteSettlementsInGroup(groupId: Long)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroup(groupId: Long)

    @Query("SELECT * FROM people WHERE profileId = :profileId LIMIT 1")
    suspend fun personByProfileId(profileId: String): PersonEntity?

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun personById(id: Long): PersonEntity?

    /** A friend with no account, matched the way the server's unique index matches: trimmed, any case. */
    @Query("SELECT * FROM people WHERE profileId IS NULL AND lower(trim(name)) = lower(trim(:name)) ORDER BY id LIMIT 1")
    suspend fun localPersonByName(name: String): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(person: PersonEntity): Long

    @Query("UPDATE people SET name = :name, avatarUrl = :avatarUrl, avatarEmoji = :avatarEmoji, upiId = :upiId WHERE id = :id")
    suspend fun updatePersonProfile(id: Long, name: String, avatarUrl: String?, avatarEmoji: String?, upiId: String?)

    @Query(
        "SELECT t.* FROM transactions t JOIN groups g ON g.id = t.groupId " +
            "WHERE t.syncState = 'PENDING' AND g.syncState = 'SYNCED' " +
            // Mine, or a groupmate's that I paid for and edited (item 11).
            "AND (t.ownerProfileId IS NULL OR EXISTS (SELECT 1 FROM splits s WHERE s.transactionId = t.id AND s.paidByPersonId = -1)) " +
            "ORDER BY t.updatedAt LIMIT :limit"
    )
    suspend fun pendingGroupExpenses(limit: Int): List<TransactionEntity>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM splits WHERE transactionId = :transactionId LIMIT 1")
    suspend fun splitForTransaction(transactionId: Long): SplitEntity?

    @Query("SELECT * FROM split_shares WHERE splitId = :splitId")
    suspend fun sharesForSplit(splitId: Long): List<SplitShareEntity>

    @Query("DELETE FROM splits WHERE transactionId = :transactionId")
    suspend fun deleteSplitForTransaction(transactionId: Long)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSplit(split: SplitEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertShares(shares: List<SplitShareEntity>)

    @Query(
        "SELECT s.* FROM settlements s JOIN groups g ON g.id = s.groupId " +
            "WHERE s.syncState = 'PENDING' AND g.syncState = 'SYNCED' ORDER BY s.updatedAt LIMIT :limit"
    )
    suspend fun pendingSettlements(limit: Int): List<SettlementEntity>

    @Query("UPDATE settlements SET syncState = 'SYNCED' WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncState = 'PENDING'")
    suspend fun markSettlementSyncedIfUnchanged(id: Long, expectedUpdatedAt: Long): Int

    @Query("UPDATE settlements SET syncState = 'FAILED' WHERE id = :id AND updatedAt = :expectedUpdatedAt AND syncState = 'PENDING'")
    suspend fun markSettlementFailedIfUnchanged(id: Long, expectedUpdatedAt: Long): Int

    @Query("SELECT * FROM settlements WHERE id = :id")
    suspend fun settlementById(id: Long): SettlementEntity?

    @Query("SELECT * FROM settlements WHERE remoteId = :remoteId LIMIT 1")
    suspend fun settlementByRemoteId(remoteId: String): SettlementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Update
    suspend fun updateSettlement(settlement: SettlementEntity)

    @Query("DELETE FROM settlements WHERE id = :id AND syncState = 'SYNCED'")
    suspend fun deleteSettlementIfSynced(id: Long): Int

    // ---- Settlement confirmation (design item 10) ----

    /** My answers saved on this phone and not yet sent. */
    @Query("SELECT * FROM settlements WHERE pendingResponse IS NOT NULL ORDER BY id LIMIT :limit")
    suspend fun settlementsWithUnsentAnswers(limit: Int): List<SettlementEntity>

    @Query("UPDATE settlements SET pendingResponse = NULL WHERE id = :id")
    suspend fun clearPendingResponse(id: Long)

    /** Shared-group settlements someone else recorded that I still have to answer. */
    @Query("SELECT * FROM settlements WHERE status = 'PENDING' AND recordedByPersonId IS NOT NULL AND recordedByPersonId != -1 AND pendingResponse IS NULL AND (fromPersonId = -1 OR toPersonId = -1)")
    suspend fun settlementsAwaitingMyAnswer(): List<SettlementEntity>

    @Query("UPDATE settlements SET remindedStage = :stage WHERE id = :id")
    suspend fun setRemindedStage(id: Long, stage: Int)
}
