package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The add sheet's bill photo through the real view model and Room: nothing is kept until Save, a
 * saved photo comes back when the expense is edited, and removing it on an edit takes it off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BillPhotoFlowTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
        File(application.filesDir, "receipts").deleteRecursively()
    }

    /** What the gallery hands back: a picture file. */
    private fun picture(): Uri {
        val file = File.createTempFile("bill", ".jpg", application.cacheDir)
        file.outputStream().use { out ->
            Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return Uri.fromFile(file)
    }

    private suspend fun keptPhoto(): BillPhoto.Kept =
        withTimeout(10_000) { viewModel.billPhoto.first { it is BillPhoto.Kept } } as BillPhoto.Kept

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(20) }

    @Test
    fun `a photo picked for a new expense is kept only once it saves`() = runBlocking {
        viewModel.openManualAdd()
        viewModel.updateAmount("2340")
        viewModel.updateMerchant("DMart")

        viewModel.attachBillPhoto(application, picture())
        val staged = keptPhoto()
        assertNotNull(staged.staged)
        assertTrue("picking it is a change worth asking about before discarding", viewModel.hasUnsavedEntry())
        assertTrue(db.receiptDao().allFileNames().isEmpty())

        viewModel.saveCurrentTransaction()
        // The sheet closes once the save, photo included, has finished.
        until { !viewModel.isConfirmSheetOpen.value }

        val tx = db.transactionDao().getAllTransactionsDirect().single()
        val row = db.receiptDao().forTransaction(tx.id).single()
        assertTrue(File(File(application.filesDir, "receipts").listFiles()!!.single(), row.fileName).exists())
        assertEquals(BillPhoto.None, viewModel.billPhoto.value)
    }

    @Test
    fun `closing the sheet keeps nothing`() = runBlocking {
        viewModel.openManualAdd()
        viewModel.attachBillPhoto(application, picture())
        val staged = keptPhoto().staged!!

        viewModel.dismissConfirmSheet()

        assertFalse(staged.file.exists())
        assertEquals(BillPhoto.None, viewModel.billPhoto.value)
        assertTrue(db.receiptDao().allFileNames().isEmpty())
    }

    @Test
    fun `editing shows the saved photo, and removing it there takes it off`() = runBlocking {
        viewModel.openManualAdd()
        viewModel.updateAmount("2340")
        viewModel.attachBillPhoto(application, picture())
        keptPhoto()
        viewModel.saveCurrentTransaction()
        // Until the sheet closes, as a person would: the save is still finishing after the photo's
        // row is written, and editing before then raced its clean-up (failed on the 1.5.0 release run).
        until { !viewModel.isConfirmSheetOpen.value }
        assertTrue(db.receiptDao().allFileNames().isNotEmpty())
        val full = withTimeout(10_000) { viewModel.rawTransactions.first { it.isNotEmpty() } }.single()

        viewModel.openEditTransaction(full)
        val existing = keptPhoto()
        assertEquals("the expense's own photo, not a new one", null, existing.staged)
        assertFalse(viewModel.hasUnsavedEntry())

        viewModel.removeBillPhoto()
        assertTrue(viewModel.hasUnsavedEntry())
        viewModel.saveCurrentTransaction()
        until { !viewModel.isConfirmSheetOpen.value }

        assertTrue(db.receiptDao().allFileNames().isEmpty())
        assertFalse(existing.file.exists())
    }
}
