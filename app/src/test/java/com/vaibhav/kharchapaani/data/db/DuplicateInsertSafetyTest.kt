package com.vaibhav.kharchapaani.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Guards against SQLite REPLACE semantics: with foreign keys on, REPLACE deletes the conflicting
 * parent row, which cascades to its children. Re-inserting an existing parent must fail loudly and
 * leave the children alone. With REPLACE these tests fail twice over: no exception is thrown and
 * the child rows disappear.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DuplicateInsertSafetyTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "duplicate-insert-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()

    private lateinit var dinner: TransactionEntity
    private var splitId = 0L
    private var groupId = 0L

    @Before
    fun seedParentsWithChildren() = runBlocking {
        val txId = db.transactionDao().insertTransaction(transaction("Dinner"))
        dinner = db.transactionDao().getTransactionByIdDirect(txId)!!
        splitId = db.splitDao().insertSplit(SplitEntity(transactionId = txId, totalAmount = 300.0))
        db.splitDao().insertSplitShares(
            listOf(-1L, 2L, 3L).map { SplitShareEntity(splitId = splitId, personId = it, shareAmount = 100.0) }
        )
        groupId = db.groupDao().insertGroup(GroupEntity(name = "Goa"))
        db.groupDao().insertGroupMembers(listOf(-1L, 2L, 3L).map { GroupMemberEntity(groupId = groupId, personId = it) })
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `re-inserting an existing transaction id fails and keeps its split and shares`() = runBlocking {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.transactionDao().insertTransaction(dinner.copy(merchant = "Overwritten")) }
        }

        assertEquals("Dinner", db.transactionDao().getTransactionByIdDirect(dinner.id)?.merchant)
        assertChildrenIntact()
    }

    @Test
    fun `a new row reusing an existing remoteId is rejected and the original keeps its children`() = runBlocking {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.transactionDao().insertTransaction(transaction("Copy").copy(remoteId = dinner.remoteId)) }
        }

        assertEquals(1, db.transactionDao().getAllTransactionsDirect().size)
        assertChildrenIntact()
    }

    @Test
    fun `a batch import containing an existing id fails as a whole`() = runBlocking {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.transactionDao().insertTransactions(listOf(transaction("New row"), dinner)) }
        }

        assertEquals(listOf("Dinner"), db.transactionDao().getAllTransactionsDirect().map { it.merchant })
        assertChildrenIntact()
    }

    @Test
    fun `re-inserting an existing split id fails and keeps its shares`() = runBlocking {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.splitDao().insertSplit(SplitEntity(id = splitId, transactionId = dinner.id, totalAmount = 1.0)) }
        }

        assertChildrenIntact()
    }

    @Test
    fun `re-inserting an existing group id fails and keeps its members`() = runBlocking {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { db.groupDao().insertGroup(GroupEntity(id = groupId, name = "Goa renamed")) }
        }

        assertEquals("Goa", db.groupDao().getGroupByIdDirect(groupId)?.name)
        assertEquals(3, db.groupDao().getMembersForGroupDirect(groupId).size)
    }

    private suspend fun assertChildrenIntact() {
        assertEquals(1, db.splitDao().getAllSplitsDirect().size)
        assertEquals(3, db.splitDao().getSharesForSplitDirect(splitId).size)
    }

    private fun transaction(merchant: String) = TransactionEntity(
        type = "expense",
        amount = 300.0,
        category = "Food",
        merchant = merchant,
        paymentMethod = "UPI",
        isSplit = true,
    )
}
