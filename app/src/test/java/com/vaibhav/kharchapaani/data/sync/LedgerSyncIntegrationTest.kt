package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Real Room (runs on CI): repository writes, SyncDao's conditional writes, and one full engine pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerSyncIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "sync-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private var syncRequests = 0
    private val repository = LedgerRepository(db, onLocalTransactionChange = { syncRequests++ })
    private val dao get() = db.syncDao()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `a new personal expense is queued for sync and schedules a sync`() = runBlocking {
        val id = repository.saveTransactionWithSplits(personalTransaction("Chai", syncState = SyncState.LOCAL_ONLY))

        assertEquals(SyncState.PENDING, db.transactionDao().getTransactionByIdDirect(id)?.syncState)
        assertEquals(1, syncRequests)
    }

    @Test
    fun `split and group expenses stay local`() = runBlocking {
        val split = repository.saveTransactionWithSplits(
            personalTransaction("Dinner"),
            shares = listOf(SplitShareEntity(splitId = 0, personId = 5, shareAmount = 50.0)),
        )
        val group = repository.saveTransactionWithSplits(personalTransaction("Villa").copy(groupId = 3))

        assertEquals(SyncState.LOCAL_ONLY, db.transactionDao().getTransactionByIdDirect(split)?.syncState)
        assertEquals(SyncState.LOCAL_ONLY, db.transactionDao().getTransactionByIdDirect(group)?.syncState)
        assertEquals(emptyList<Any>(), dao.pendingPersonalTransactions(10))
    }

    @Test
    fun `editing keeps the row's remote identity and creation time`() = runBlocking {
        val id = repository.saveTransactionWithSplits(personalTransaction("Before"))
        val stored = db.transactionDao().getTransactionByIdDirect(id)!!

        // The edit sheet builds a brand-new entity, so remoteId/createdAt arrive as fresh defaults.
        repository.updateTransactionWithSplits(personalTransaction("After").copy(id = id, createdAt = 999))

        val edited = db.transactionDao().getTransactionByIdDirect(id)!!
        assertEquals("After", edited.merchant)
        assertEquals(stored.remoteId, edited.remoteId)
        assertEquals(stored.createdAt, edited.createdAt)
        assertEquals(SyncState.PENDING, edited.syncState)
    }

    @Test
    fun `a synced expense that becomes a split is taken down from the cloud`() = runBlocking {
        val id = repository.saveTransactionWithSplits(personalTransaction("Solo"))
        val remoteId = db.transactionDao().getTransactionByIdDirect(id)!!.remoteId
        dao.markSyncedIfUnchanged(id, db.transactionDao().getTransactionByIdDirect(id)!!.updatedAt)

        repository.updateTransactionWithSplits(
            personalTransaction("Now split").copy(id = id),
            shares = listOf(SplitShareEntity(splitId = 0, personId = 5, shareAmount = 50.0)),
        )

        assertEquals(SyncState.LOCAL_ONLY, db.transactionDao().getTransactionByIdDirect(id)?.syncState)
        assertTrue(dao.hasPendingDelete(remoteId))

        repository.updateTransactionWithSplits(personalTransaction("Solo again").copy(id = id))
        assertFalse("re-queuing the row cancels its queued delete", dao.hasPendingDelete(remoteId))
        assertEquals(SyncState.PENDING, db.transactionDao().getTransactionByIdDirect(id)?.syncState)
    }

    @Test
    fun `deleting queues a server delete only for rows the server may have`() = runBlocking {
        val synced = repository.saveTransactionWithSplits(personalTransaction("Synced"))
        val syncedRemoteId = db.transactionDao().getTransactionByIdDirect(synced)!!.remoteId
        val localOnly = repository.saveTransactionWithSplits(personalTransaction("Split").copy(groupId = 1))
        val localRemoteId = db.transactionDao().getTransactionByIdDirect(localOnly)!!.remoteId

        repository.deleteTransaction(synced)
        repository.deleteTransaction(localOnly)

        assertTrue(dao.hasPendingDelete(syncedRemoteId))
        assertFalse(dao.hasPendingDelete(localRemoteId))
    }

    @Test
    fun `conditional writes protect local changes`() = runBlocking {
        val id = repository.saveTransactionWithSplits(personalTransaction("Row"))
        val row = db.transactionDao().getTransactionByIdDirect(id)!!

        assertEquals("stale updatedAt must not mark synced", 0, dao.markSyncedIfUnchanged(id, row.updatedAt - 1))
        assertEquals(0, dao.updateFromRemoteIfSynced(id, "expense", 1.0, "X", "Remote", "UPI", 0, "", 0, 0))
        assertEquals(0, dao.deleteIfSynced(id))
        assertEquals("Row", db.transactionDao().getTransactionByIdDirect(id)?.merchant)

        assertEquals(1, dao.markSyncedIfUnchanged(id, row.updatedAt))
        assertEquals(1, dao.updateFromRemoteIfSynced(id, "expense", 1.0, "X", "Remote", "UPI", 0, "", 0, 0))
    }

    @Test
    fun `failed rows can be re-queued`() = runBlocking {
        val id = repository.saveTransactionWithSplits(personalTransaction("Rejected"))
        dao.markFailedIfUnchanged(id, db.transactionDao().getTransactionByIdDirect(id)!!.updatedAt)

        assertEquals(1, dao.requeueFailedTransactions())
        assertEquals(SyncState.PENDING, db.transactionDao().getTransactionByIdDirect(id)?.syncState)
    }

    @Test
    fun `a full push and pull pass works against the real database`() = runBlocking {
        val owner = "owner-1"
        val remote = FakeTransactionRemote(owner)
        val engine = TransactionSyncEngine(RoomTransactionSyncStore(db), remote, owner)
        val mine = repository.saveTransactionWithSplits(personalTransaction("Mine"))
        dao.insertPendingDelete(PendingRemoteDeleteEntity("never-uploaded"))
        remote.writeFromOtherDevice(
            RemoteTransaction(
                id = "from-laptop", ownerId = owner, type = "income", amount = 500.0, category = "Income (Other)",
                merchant = "Laptop", paymentMethod = "Cash", date = "2026-09-10T10:00:00Z", note = "hi",
                createdAt = "2026-09-10T10:00:00Z", updatedAt = "",
            )
        )

        engine.sync()

        assertEquals(SyncState.SYNCED, db.transactionDao().getTransactionByIdDirect(mine)?.syncState)
        val pulled = dao.transactionByRemoteId("from-laptop")!!
        assertEquals("Laptop", pulled.merchant)
        assertEquals(500.0, pulled.amount, 0.0)
        assertEquals(1_789_034_400_000L, pulled.date)
        assertEquals(SyncState.SYNCED, pulled.syncState)
        assertEquals(emptyList<String>(), dao.pendingDeletes(10))
    }
}
