package com.vaibhav.kharchapaani.data.sync

import android.graphics.Bitmap
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.ReceiptEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.receipts.CloudCopy
import com.vaibhav.kharchapaani.data.receipts.ReceiptRemote
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.repository.ReceiptRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Bill photo sync safety net tests (W12, #266).
 *
 * Starts from a real Room database and [ReceiptRepository] and [ReceiptSyncEngine]:
 * 1. Replacing a photo queues the old cloud copy for deletion, and cleans up the old local file.
 * 2. Deleting an expense queues its cloud copies, and the queue survives the same transaction as the delete.
 * 3. The delete queue is strictly transactional: rolling back a transaction also rolls back queued deletes.
 * 4. A queued path is deduplicated and deleted at most once across multiple sync passes.
 * 5. Turning "Share bill photos" off clears the shared path on the next run of [ReceiptSyncEngine].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptSyncSafetyTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dir = File(context.cacheDir, "receipt-safety-${System.nanoTime()}").apply { mkdirs() }
    private val store = ReceiptStore(dir)
    private val remote = FakeReceiptRemote()
    private val myUserId = "00000000-0000-4000-8000-0000000000a1"

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private class FakeReceiptRemote : ReceiptRemote {
        val objects = mutableMapOf<String, ByteArray>()
        val paths = mutableMapOf<String, String?>()
        val deleted = mutableListOf<String>()

        override suspend fun upload(path: String, bytes: ByteArray, contentType: String) {
            objects[path] = bytes
        }

        override suspend fun download(path: String): ByteArray = objects.getValue(path)

        override suspend fun delete(paths: List<String>) {
            deleted += paths
            paths.forEach { objects.remove(it) }
        }

        override suspend fun deleteFolder(ownerId: String) {
            objects.keys.removeAll { it.startsWith("$ownerId/") }
        }

        override suspend fun setSharedPath(transactionRemoteId: String, path: String?) {
            paths[transactionRemoteId] = path
        }

        override suspend fun sharedPaths(transactionRemoteIds: List<String>): Map<String, String> =
            transactionRemoteIds.mapNotNull { id -> paths[id]?.let { id to it } }.toMap()
    }

    private fun engine(share: Boolean = true) = ReceiptSyncEngine(
        db = db,
        store = store,
        remote = remote,
        selfId = myUserId,
        shareInSplits = share,
        encode = { CloudCopy.Encoded(it.readBytes(), "webp", "image/webp") }
    )

    private suspend fun insertExpense(
        id: Long,
        remoteId: String,
        groupId: Long? = null,
        syncState: SyncState = SyncState.SYNCED,
        ownerProfileId: String? = null
    ) {
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = id,
                type = "expense",
                amount = 250.0,
                category = "Food",
                merchant = "Cafe",
                paymentMethod = "UPI",
                groupId = groupId,
                remoteId = remoteId,
                syncState = syncState,
                ownerProfileId = ownerProfileId
            )
        )
    }

    private suspend fun stagePhoto(): ReceiptStore.Staged {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        return store.stage(bitmap)!!
    }

    @Test
    fun `replacing a photo queues the old cloud copy for deletion`() = runBlocking {
        val repo = ReceiptRepository(db, store)
        insertExpense(1L, "tx-remote-1")

        // 1. Attach first photo and simulate that it synced to cloud
        val photo1 = stagePhoto()
        assertTrue(repo.attach(1L, photo1))
        val row1 = db.receiptDao().forTransaction(1L).single()
        val remotePath1 = "$myUserId/tx-remote-1/old-copy.webp"
        db.receiptDao().setRemotePath(row1.id, remotePath1)

        val localFile1 = store.file(row1.fileName)
        assertTrue("local file 1 exists before replace", localFile1.exists())
        assertTrue("queue is initially empty", ReceiptDeleteQueue.read(db).isEmpty())

        // 2. Attach second photo, replacing the first photo
        val photo2 = stagePhoto()
        assertTrue(repo.attach(1L, photo2))

        // Assertions:
        // - Old remote path is queued for deletion
        val queue = ReceiptDeleteQueue.read(db)
        assertEquals(listOf(remotePath1), queue)

        // - Old local file is deleted by store.delete()
        assertFalse("old local file was removed", localFile1.exists())

        // - New photo is attached to the transaction with no remotePath yet
        val row2 = db.receiptDao().forTransaction(1L).single()
        assertTrue(row2.id != row1.id)
        assertNull(row2.remotePath)
        assertTrue(store.file(row2.fileName).exists())
    }

    @Test
    fun `deleting the expense queues its cloud copies and queue survives the same transaction as delete`() = runBlocking {
        val repo = ReceiptRepository(db, store)
        insertExpense(2L, "tx-remote-2")

        // Attach photo and set cloud path
        val photo = stagePhoto()
        assertTrue(repo.attach(2L, photo))
        val row = db.receiptDao().forTransaction(2L).single()
        val remotePath = "$myUserId/tx-remote-2/photo-to-delete.webp"
        db.receiptDao().setRemotePath(row.id, remotePath)

        // Delete expense/photo through repo.remove()
        repo.remove(2L)

        // Queue has the remote path
        val queued = ReceiptDeleteQueue.read(db)
        assertEquals(listOf(remotePath), queued)

        // Photo row is gone
        assertTrue(db.receiptDao().forTransaction(2L).isEmpty())
    }

    @Test
    fun `delete queue is transactional and rolls back when transaction fails`() = runBlocking {
        insertExpense(3L, "tx-remote-3")
        val initialQueue = ReceiptDeleteQueue.read(db)
        assertEquals(emptyList<String>(), initialQueue)

        // Transaction that aborts
        runCatching {
            db.withTransaction {
                ReceiptDeleteQueue.add(db, listOf("$myUserId/tx-remote-3/rollback.webp"))
                throw IllegalStateException("transaction simulated failure")
            }
        }

        // Must NOT survive a rolled back transaction
        val queueAfterRollback = ReceiptDeleteQueue.read(db)
        assertTrue("rolled back queue addition does not persist", queueAfterRollback.isEmpty())
    }

    @Test
    fun `a queued path is deleted at most once across sync runs`() = runBlocking {
        val remotePath = "$myUserId/tx-remote-4/unique.webp"
        remote.objects[remotePath] = ByteArray(64) { 1 }

        // Adding duplicate paths to queue is deduplicated
        ReceiptDeleteQueue.add(db, listOf(remotePath, remotePath))
        assertEquals(1, ReceiptDeleteQueue.read(db).size)

        // Run sync pass 1: drains deletes
        engine().sync()

        // Remote received delete exactly once
        assertEquals(listOf(remotePath), remote.deleted)
        assertTrue("queue was emptied after sync", ReceiptDeleteQueue.read(db).isEmpty())
        assertFalse("remote object was removed", remote.objects.containsKey(remotePath))

        // Run sync pass 2: nothing should be deleted again
        engine().sync()
        assertEquals("deleted list did not grow on second pass", 1, remote.deleted.size)
    }

    @Test
    fun `turning Share bill photos off clears the shared path on the next run of ReceiptSyncEngine`() = runBlocking {
        db.groupDao().insertGroup(GroupEntity(id = 10L, name = "Trip"))
        val remoteTxId = "tx-remote-group-5"
        insertExpense(5L, remoteTxId, groupId = 10L, syncState = SyncState.SYNCED)

        val photo = stagePhoto()
        val repo = ReceiptRepository(db, store)
        assertTrue(repo.attach(5L, photo))

        val remotePath = "$myUserId/$remoteTxId/bill.webp"
        val row = db.receiptDao().forTransaction(5L).single()
        db.receiptDao().setRemotePath(row.id, remotePath)
        remote.objects[remotePath] = ByteArray(128) { 2 }

        // 1. Run engine with shareInSplits = true -> sets shared path
        engine(share = true).sync()
        assertEquals(remotePath, remote.paths[remoteTxId])

        // 2. Run engine with shareInSplits = false -> clears shared path
        engine(share = false).sync()
        assertNull("turning share off clears the shared path", remote.paths[remoteTxId])

        // Cloud object itself remains backed up in the user's account
        assertTrue("cloud object still exists in account folder", remote.objects.containsKey(remotePath))

        // 3. Repeated sync with share off does not re-add shared path
        engine(share = false).sync()
        assertNull(remote.paths[remoteTxId])
    }
}
