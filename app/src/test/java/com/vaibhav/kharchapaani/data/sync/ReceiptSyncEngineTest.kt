package com.vaibhav.kharchapaani.data.sync

import android.graphics.Bitmap
import androidx.room.Room
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Bill photos to and from the cloud, against a real Room ledger and a fake bucket. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptSyncEngineTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val dir = File(context.cacheDir, "receipt-sync-${System.nanoTime()}").apply { mkdirs() }
    private val store = ReceiptStore(dir)
    private val remote = FakeRemote()
    private val me = "00000000-0000-4000-8000-0000000000a1"
    private val friend = "00000000-0000-4000-8000-0000000000b1"

    @After fun close() {
        db.close()
        dir.deleteRecursively()
    }

    private class FakeRemote : ReceiptRemote {
        val objects = mutableMapOf<String, ByteArray>()
        val paths = mutableMapOf<String, String?>()
        val deleted = mutableListOf<String>()
        var failNextPublish = false
        var uploadCount = 0
        var publishCount = 0
        override suspend fun upload(path: String, bytes: ByteArray, contentType: String) {
            uploadCount++
            objects[path] = bytes
        }
        override suspend fun download(path: String): ByteArray = objects.getValue(path)
        override suspend fun delete(paths: List<String>) { deleted += paths; paths.forEach { objects.remove(it) } }
        override suspend fun deleteFolder(ownerId: String) { objects.keys.removeAll { it.startsWith("$ownerId/") } }
        override suspend fun setSharedPath(transactionRemoteId: String, path: String?) {
            publishCount++
            if (failNextPublish) {
                failNextPublish = false
                throw java.io.IOException("publish failed")
            }
            paths[transactionRemoteId] = path
        }
        override suspend fun sharedPaths(transactionRemoteIds: List<String>) =
            transactionRemoteIds.mapNotNull { id -> paths[id]?.let { id to it } }.toMap()
    }

    private fun engine(share: Boolean = true) = ReceiptSyncEngine(db, store, remote, me, share,
        encode = { CloudCopy.Encoded(it.readBytes(), "webp", "image/webp") })

    private suspend fun expense(id: Long, remoteId: String, groupId: Long? = null, owner: String? = null, state: SyncState = SyncState.SYNCED) {
        db.transactionDao().insertTransaction(TransactionEntity(id = id, type = "expense", amount = 100.0, category = "Food",
            merchant = "Lunch", paymentMethod = "UPI", groupId = groupId, remoteId = remoteId, syncState = state, ownerProfileId = owner))
    }

    private suspend fun photo(transactionId: Long, name: String): ReceiptEntity {
        File(dir, name).writeBytes(ByteArray(2048) { it.toByte() })
        val id = db.receiptDao().insert(ReceiptEntity(transactionId = transactionId, fileName = name, width = 10, height = 10, bytes = 2048))
        return db.receiptDao().forTransaction(transactionId).single { it.id == id }
    }

    private suspend fun group(id: Long) {
        db.groupDao().insertGroup(GroupEntity(id = id, name = "Goa trip"))
    }

    @Test fun `a personal photo uploads into my folder and is backed up on its expense`() = runBlocking {
        expense(1, "20000000-0000-4000-8000-000000000001")
        photo(1, "a.jpg")
        engine().sync()
        val row = db.receiptDao().forTransaction(1).single()
        val path = row.remotePath!!
        assertTrue(path.startsWith("$me/20000000-0000-4000-8000-000000000001/") && path.endsWith(".webp"))
        assertTrue(remote.objects.containsKey(path))
        assertEquals(path, remote.paths["20000000-0000-4000-8000-000000000001"])
    }

    @Test fun `a failed group photo publication retries with the sharing switch unchanged`() = runBlocking {
        group(7)
        assertPublicationRetries(groupId = 7)
    }

    @Test fun `a failed personal photo publication retries for backup`() = runBlocking {
        assertPublicationRetries()
    }

    private suspend fun assertPublicationRetries(groupId: Long? = null) {
        // The switch was already applied before this new photo arrived.
        engine().sync()
        val remoteId = "20000000-0000-4000-8000-000000000001"
        expense(1, remoteId, groupId = groupId)
        photo(1, "a.jpg")
        remote.failNextPublish = true
        ReceiptSyncEngine.runQuietly(engine())
        val path = db.receiptDao().forTransaction(1).single().remotePath!!
        assertTrue(remote.objects.containsKey(path))
        assertNull(remote.paths[remoteId])
        assertEquals(1, remote.publishCount)

        // A fresh engine must remember the upload, but retry the failed publication.
        engine().sync()
        assertEquals(path, remote.paths[remoteId])
        assertEquals(2, remote.publishCount)
        assertEquals("retry must not upload the photo again", 1, remote.uploadCount)

        engine().sync()
        assertEquals("a successful publication is remembered", 2, remote.publishCount)
    }

    @Test fun `with sharing off a group photo backs up but the split can't see it`() = runBlocking {
        group(7)
        expense(1, "20000000-0000-4000-8000-000000000001", groupId = 7)
        photo(1, "a.jpg")
        engine(share = false).sync()
        assertNotNull(db.receiptDao().forTransaction(1).single().remotePath)
        assertEquals(1, remote.objects.size)
        assertNull(remote.paths["20000000-0000-4000-8000-000000000001"])
    }

    @Test fun `turning sharing on later shares photos already up`() = runBlocking {
        group(7)
        expense(1, "20000000-0000-4000-8000-000000000001", groupId = 7)
        photo(1, "a.jpg")
        engine(share = false).sync()
        engine(share = true).sync()
        assertEquals(db.receiptDao().forTransaction(1).single().remotePath, remote.paths["20000000-0000-4000-8000-000000000001"])
        remote.failNextPublish = true
        ReceiptSyncEngine.runQuietly(engine(share = false))
        assertNotNull("failed unsharing still needs a retry", remote.paths["20000000-0000-4000-8000-000000000001"])
        engine(share = false).sync()
        assertNull("turning it off again unshares", remote.paths["20000000-0000-4000-8000-000000000001"])
    }

    @Test fun `nothing uploads before the expense is on the server, or for someone else's expense`() = runBlocking {
        group(7)
        expense(1, "20000000-0000-4000-8000-000000000001", state = SyncState.PENDING)
        expense(2, "20000000-0000-4000-8000-000000000002", groupId = 7, owner = friend)
        photo(1, "a.jpg")
        photo(2, "b.jpg")
        engine().sync()
        assertTrue(remote.objects.isEmpty())
        assertTrue(db.receiptDao().all().all { it.remotePath == null })
    }

    @Test fun `a friend's shared photo arrives as a row that downloads when opened, and leaves when unshared`() = runBlocking {
        group(7)
        expense(2, "20000000-0000-4000-8000-000000000002", groupId = 7, owner = friend)
        val path = "$friend/20000000-0000-4000-8000-000000000002/cccccccc-0000-4000-8000-000000000001.webp"
        remote.objects[path] = ByteArray(512) { 7 }
        remote.paths["20000000-0000-4000-8000-000000000002"] = path
        engine().sync()
        val row = db.receiptDao().forTransaction(2).single()
        assertEquals(path, row.remotePath)
        assertTrue("not downloaded during sync", !store.file(row.fileName).exists())

        val repo = ReceiptRepository(db, store)
        val file = repo.download(row, remote)
        assertNotNull(file)
        assertEquals(512L, file!!.length())
        engine().sync()
        assertTrue("never uploaded back", remote.objects.keys.none { it.startsWith("$me/") })

        remote.paths["20000000-0000-4000-8000-000000000002"] = null
        engine().sync()
        assertTrue("the friend stopped sharing: it leaves this phone", db.receiptDao().forTransaction(2).isEmpty())
        assertTrue(!file.exists())
    }

    @Test fun `taking a photo off deletes my cloud copy on the next sync, never a friend's`() = runBlocking {
        expense(1, "20000000-0000-4000-8000-000000000001")
        photo(1, "a.jpg")
        engine().sync()
        val mine = db.receiptDao().forTransaction(1).single().remotePath!!
        ReceiptDeleteQueue.add(db, listOf("$friend/20000000-0000-4000-8000-000000000009/x.webp"))
        ReceiptRepository(db, store).remove(1)
        engine().sync()
        assertEquals(listOf(mine), remote.deleted)
        assertTrue(ReceiptDeleteQueue.read(db).isEmpty())
    }

    @Test fun `on a new phone my backed-up photos come back as rows to load`() = runBlocking {
        expense(1, "20000000-0000-4000-8000-000000000001")
        val path = "$me/20000000-0000-4000-8000-000000000001/dddddddd-0000-4000-8000-000000000001.webp"
        remote.paths["20000000-0000-4000-8000-000000000001"] = path
        engine().sync()
        assertEquals(path, db.receiptDao().forTransaction(1).single().remotePath)
    }

    @Test fun `the cloud copy is at most 1280 px and far under the bucket's limit`() {
        val big = Bitmap.createBitmap(2000, 3000, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFF0EEE6.toInt()) }
        val file = File(dir, "big.jpg").apply { outputStream().use { big.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
        val webp = CloudCopy.encode(file, sdk = 34)!!
        val jpeg = CloudCopy.encode(file, sdk = 28)!!
        assertEquals("webp", webp.extension)
        assertEquals("jpg", jpeg.extension)
        assertTrue(webp.bytes.size < CloudCopy.MAX_BYTES && jpeg.bytes.size < CloudCopy.MAX_BYTES)
        val decoded = android.graphics.BitmapFactory.decodeByteArray(webp.bytes, 0, webp.bytes.size)
        assertEquals(1280, maxOf(decoded.width, decoded.height))
    }

    @Test fun `a detailed photo fits within 200 KB in both cloud formats`() {
        val side = 1280
        val random = java.util.Random(300)
        val pixels = IntArray(side * side) { random.nextInt() or 0xFF000000.toInt() }
        val detailed = Bitmap.createBitmap(pixels, side, side, Bitmap.Config.ARGB_8888)
        val file = File(dir, "detailed.jpg")
        file.outputStream().use { detailed.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        detailed.recycle()

        for (sdk in listOf(28, 34)) {
            val encoded = checkNotNull(CloudCopy.encode(file, sdk = sdk))
            assertTrue("sdk $sdk: ${encoded.bytes.size} bytes", encoded.bytes.size in 1..200_000)
            assertNotNull(android.graphics.BitmapFactory.decodeByteArray(encoded.bytes, 0, encoded.bytes.size))
        }
    }
}
