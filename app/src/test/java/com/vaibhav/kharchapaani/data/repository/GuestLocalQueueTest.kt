package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The local capability must alter persistence while retaining the production paisa allocation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestLocalQueueTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val repository = LedgerRepository(db, localMode = true)

    @After fun close() { db.close() }

    private suspend fun seed(): List<Long> {
        db.personDao().insertPerson(PersonEntity(id = 11, name = "First friend", avatarColor = "#123456"))
        db.personDao().insertPerson(PersonEntity(id = 22, name = "Second friend", avatarColor = "#654321"))
        val personal = repository.saveTransactionWithSplits(TransactionEntity(
            type = "expense", amount = 12.50, category = "Food", merchant = "Tea", paymentMethod = "Cash",
        ))
        val split = repository.saveTransactionWithSplits(TransactionEntity(
            type = "expense", amount = 100.0, category = "Food", merchant = "Dinner", paymentMethod = "Cash",
        ), paidByPersonId = -1, shares = listOf(
            SplitShareEntity(splitId = 0, personId = -1, shareAmount = 100.0 / 3),
            SplitShareEntity(splitId = 0, personId = 11, shareAmount = 100.0 / 3),
            SplitShareEntity(splitId = 0, personId = 22, shareAmount = 100.0 / 3),
        ))
        val iou = repository.recordSettlement(fromPersonId = 11, toPersonId = -1, amount = 15.25, isIou = true)
        return listOf(personal, split, iou)
    }

    @Test fun `guest creation stores only local rows while allocating the exact hundred rupees`() = runBlocking {
        seed()
        val shares = db.splitDao().getAllSplitSharesDirect().associate { it.personId to it.shareAmount }
        assertEquals(mapOf(-1L to 33.34, 11L to 33.33, 22L to 33.33), shares)
        assertEquals(100.0, shares.values.sum(), 0.0)
        assertEquals(listOf(SyncState.LOCAL_ONLY, SyncState.LOCAL_ONLY),
            db.transactionDao().getAllTransactionsDirect().map { it.syncState })
        assertEquals(listOf(SyncState.LOCAL_ONLY), db.settlementDao().getAllSettlementsDirect().map { it.syncState })
        assertEquals(0, db.syncDao().observeUnsyncedChangeCount().first())
        assertTrue(db.syncDao().pendingDeletes(100).isEmpty())
    }

    @Test fun `guest expense split and IOU deletion leave no upload tombstones`() = runBlocking {
        val ids = seed()
        repository.deleteTransaction(ids[0])
        repository.deleteTransaction(ids[1])
        repository.deleteSettlement(ids[2])
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())
        assertTrue(db.splitDao().getAllSplitsDirect().isEmpty())
        assertTrue(db.splitDao().getAllSplitSharesDirect().isEmpty())
        assertTrue(db.settlementDao().getAllSettlementsDirect().isEmpty())
        // Inspect all entity types: the split's tombstone is separate from its expense's tombstone.
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM pending_remote_deletes").use {
            assertTrue(it.moveToFirst())
            assertEquals(0L, it.getLong(0))
        }
        assertEquals(0, db.syncDao().observeUnsyncedChangeCount().first())
    }
}
