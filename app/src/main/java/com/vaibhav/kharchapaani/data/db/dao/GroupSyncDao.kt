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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPerson(person: PersonEntity): Long

    @Query("UPDATE people SET name = :name, avatarUrl = :avatarUrl WHERE id = :id")
    suspend fun updatePersonProfile(id: Long, name: String, avatarUrl: String?)

    @Query(
        "SELECT t.* FROM transactions t JOIN groups g ON g.id = t.groupId " +
            "WHERE t.syncState = 'PENDING' AND t.ownerProfileId IS NULL AND g.syncState = 'SYNCED' " +
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
}
