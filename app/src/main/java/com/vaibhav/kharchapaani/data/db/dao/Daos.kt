package com.vaibhav.kharchapaani.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY name ASC")
    fun getAllPeople(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people ORDER BY name ASC")
    suspend fun getAllPeopleDirect(): List<PersonEntity>

    @Query("SELECT * FROM people WHERE id = :id LIMIT 1")
    suspend fun getPersonById(id: Long): PersonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity): Long

    @Update
    suspend fun updatePerson(person: PersonEntity)

    @Delete
    suspend fun deletePerson(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deletePersonById(id: Long)
}

@Dao
interface GroupDao {
    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>

    @Query("SELECT * FROM groups ORDER BY createdAt DESC")
    suspend fun getAllGroupsDirect(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    fun getGroupById(groupId: Long): Flow<GroupEntity?>

    @Query("SELECT * FROM groups WHERE id = :groupId LIMIT 1")
    suspend fun getGroupByIdDirect(groupId: Long): GroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity): Long

    @Update
    suspend fun updateGroup(group: GroupEntity)

    @Delete
    suspend fun deleteGroup(group: GroupEntity)

    @Query("DELETE FROM groups WHERE id = :groupId")
    suspend fun deleteGroupById(groupId: Long)

    // Group Members
    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun getMembersForGroup(groupId: Long): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembersForGroupDirect(groupId: Long): List<GroupMemberEntity>

    @Query("SELECT * FROM group_members")
    fun getAllGroupMembers(): Flow<List<GroupMemberEntity>>

    @Query("SELECT * FROM group_members")
    suspend fun getAllGroupMembersDirect(): List<GroupMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroupMembers(members: List<GroupMemberEntity>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembersForGroup(groupId: Long)

    @Query("DELETE FROM group_members WHERE personId = :personId")
    suspend fun deleteMemberByPersonId(personId: Long)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    suspend fun getAllTransactionsDirect(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE groupId = :groupId ORDER BY date DESC, createdAt DESC")
    fun getTransactionsForGroup(groupId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionById(id: Long): Flow<TransactionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)
}

@Dao
interface SplitDao {
    @Query("SELECT * FROM splits WHERE transactionId = :txId LIMIT 1")
    fun getSplitForTransaction(txId: Long): Flow<SplitEntity?>

    @Query("SELECT * FROM splits WHERE transactionId = :txId LIMIT 1")
    suspend fun getSplitByTransactionIdDirect(txId: Long): SplitEntity?

    @Query("SELECT * FROM splits")
    fun getAllSplits(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM splits")
    suspend fun getAllSplitsDirect(): List<SplitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplit(split: SplitEntity): Long

    @Query("DELETE FROM splits WHERE transactionId = :txId")
    suspend fun deleteSplitByTransactionId(txId: Long)

    @Query("SELECT * FROM split_shares WHERE splitId = :splitId")
    fun getSharesForSplit(splitId: Long): Flow<List<SplitShareEntity>>

    @Query("SELECT * FROM split_shares WHERE splitId = :splitId")
    suspend fun getSharesForSplitDirect(splitId: Long): List<SplitShareEntity>

    @Query("SELECT * FROM split_shares")
    fun getAllSplitShares(): Flow<List<SplitShareEntity>>

    @Query("SELECT * FROM split_shares")
    suspend fun getAllSplitSharesDirect(): List<SplitShareEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSplitShares(shares: List<SplitShareEntity>)

    @Query("DELETE FROM split_shares WHERE splitId = :splitId")
    suspend fun deleteSharesForSplit(splitId: Long)

    @Query("DELETE FROM split_shares WHERE personId = :personId")
    suspend fun deleteSharesForPerson(personId: Long)
}

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements ORDER BY date DESC, createdAt DESC")
    fun getAllSettlements(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements ORDER BY date DESC, createdAt DESC")
    suspend fun getAllSettlementsDirect(): List<SettlementEntity>

    @Query("SELECT * FROM settlements WHERE fromPersonId = :personId OR toPersonId = :personId ORDER BY date DESC")
    fun getSettlementsForPerson(personId: Long): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlements WHERE groupId = :groupId ORDER BY date DESC")
    fun getSettlementsForGroup(groupId: Long): Flow<List<SettlementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity): Long

    @Delete
    suspend fun deleteSettlement(settlement: SettlementEntity)

    @Query("DELETE FROM settlements WHERE id = :id")
    suspend fun deleteSettlementById(id: Long)

    @Query("DELETE FROM settlements WHERE fromPersonId = :personId OR toPersonId = :personId")
    suspend fun deleteSettlementsForPerson(personId: Long)
}
