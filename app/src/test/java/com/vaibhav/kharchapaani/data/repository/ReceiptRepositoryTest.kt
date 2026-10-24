package com.vaibhav.kharchapaani.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.sync.personalTransaction
import kotlinx.coroutines.flow.first
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
import java.util.UUID

/** Bill photos against real Room and real files (docs/designs/bill-photos.md). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = ReceiptStore(File(context.filesDir, "receipts-test-${UUID.randomUUID()}"))
    private val receipts = ReceiptRepository(db, store)
    private val ledger = LedgerRepository(db)

    @After
    fun tearDown() {
        db.close()
        store.dir.deleteRecursively()
    }

    private fun bill(width: Int = 3000, height: Int = 4000): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }

    private suspend fun expense(merchant: String = "DMart"): Long =
        ledger.saveTransactionWithSplits(personalTransaction(merchant, syncState = SyncState.LOCAL_ONLY, amount = 2340.0))

    @Test
    fun `a kept photo is at most 2000 px on its long side, a JPEG with no EXIF`() = runBlocking {
        val staged = store.stage(bill())!!

        assertEquals(1500, staged.width)
        assertEquals(2000, staged.height)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(staged.file.path, bounds)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertEquals(2000, maxOf(bounds.outWidth, bounds.outHeight))
        // Written from pixels: no APP1 "Exif" block, so no location or phone details can ride along.
        val head = staged.file.readBytes().take(4096).toByteArray().toString(Charsets.ISO_8859_1)
        assertFalse(head.contains("Exif"))
    }

    @Test
    fun `a smaller picture is kept at its own size`() = runBlocking {
        val staged = store.stage(bill(800, 600))!!

        assertEquals(800, staged.width)
        assertEquals(600, staged.height)
    }

    @Test
    fun `attaching moves the photo in and the expense shows as having one`() = runBlocking {
        val id = expense()
        val staged = store.stage(bill())!!

        assertTrue(receipts.attach(id, staged))

        val (row, file) = receipts.photoOf(id)!!
        assertTrue(file.exists())
        assertFalse("no longer staged", staged.file.exists())
        assertEquals(file.length(), row.bytes)
        assertEquals(setOf(id), receipts.transactionIds.first())
    }

    @Test
    fun `replacing deletes the old file`() = runBlocking {
        val id = expense()
        receipts.attach(id, store.stage(bill())!!)
        val (_, first) = receipts.photoOf(id)!!

        receipts.attach(id, store.stage(bill(1000, 1000))!!)

        val (row, second) = receipts.photoOf(id)!!
        assertFalse(first.exists())
        assertTrue(second.exists())
        assertEquals(1000, row.width)
        assertEquals(1, db.receiptDao().forTransaction(id).size)
    }

    @Test
    fun `removing takes the row and the file`() = runBlocking {
        val id = expense()
        receipts.attach(id, store.stage(bill())!!)
        val (_, file) = receipts.photoOf(id)!!

        receipts.remove(id)

        assertNull(receipts.photoOf(id))
        assertFalse(file.exists())
    }

    @Test
    fun `deleting the expense takes its row by cascade, and its file once told`() = runBlocking {
        val id = expense()
        receipts.attach(id, store.stage(bill())!!)
        val (_, file) = receipts.photoOf(id)!!

        // What MainViewModel.commitTransactionDelete does once Undo has gone.
        val names = receipts.fileNamesOf(id)
        ledger.deleteTransaction(id)
        receipts.deleteFiles(names)

        assertTrue(db.receiptDao().allFileNames().isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun `the sweep deletes orphans and old staged photos, and keeps what's referenced or new`() = runBlocking {
        val id = expense()
        receipts.attach(id, store.stage(bill())!!)
        val (_, kept) = receipts.photoOf(id)!!
        val hourAndABitAgo = System.currentTimeMillis() - 61 * 60 * 1000L
        val orphan = File(store.dir, "gone.jpg").apply { writeText("x"); setLastModified(hourAndABitAgo) }
        val freshOrphan = File(store.dir, "just-moved-in.jpg").apply { writeText("x") }
        val oldStaged = store.stage(bill(10, 10))!!.file.apply { setLastModified(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000L) }
        val newStaged = store.stage(bill(10, 10))!!.file

        val deleted = receipts.sweepOrphans()

        assertEquals(2, deleted)
        assertTrue(kept.exists())
        assertFalse(orphan.exists())
        assertTrue("a file moved in a moment ago may be about to get its row", freshOrphan.exists())
        assertFalse(oldStaged.exists())
        assertTrue(newStaged.exists())
    }

    @Test
    fun `attaching to an expense that's gone keeps nothing`() = runBlocking {
        val staged = store.stage(bill())!!

        assertFalse(receipts.attach(transactionId = 9_999L, photo = staged))

        assertTrue(db.receiptDao().allFileNames().isEmpty())
        assertNotNull(store.dir.listFiles())
        assertTrue(store.dir.listFiles()!!.none { it.isFile })
    }
}
