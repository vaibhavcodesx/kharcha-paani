package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.domain.BackupTransaction
import com.vaibhav.kharchapaani.domain.LocalBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Picking a backup used to restore it on the spot. Now it is read, checked and shown, and nothing
 * goes in until Restore is tapped.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RestorePreviewTest {
    private val copyContext = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555")
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun file(text: String): Uri = Uri.fromFile(File.createTempFile("backup", ".json", application.cacheDir).apply { writeText(text) })

    private val backup = LocalBackup(
        exportedAt = 1_760_000_000_000L,
        appVersion = "1.4.0",
        transactions = listOf(
            BackupTransaction(type = "expense", amount = 120.0, category = "Food", merchant = "Chai Point", paymentMethod = "UPI", date = 2_000L, createdAt = 2_000L, groupName = "Flat"),
        ),
    )

    @Test
    fun `a picked backup is shown first, and only Restore puts it in`() = runBlocking {
        viewModel.importLocalBackup(application, file(LocalBackup.encode(backup)))

        val shown = withTimeout(10_000) { viewModel.restorePreview.first { it != null } }
        assertEquals("1 expense", shown!!.summary(copyContext))
        assertTrue("nothing restored before it's confirmed", db.transactionDao().getAllTransactionsDirect().isEmpty())

        viewModel.confirmRestore()
        withTimeout(10_000) { while (db.transactionDao().getAllTransactionsDirect().isEmpty()) kotlinx.coroutines.delay(20) }

        assertNull(viewModel.restorePreview.value)
        assertEquals("Chai Point", db.transactionDao().getAllTransactionsDirect().single().merchant)
    }

    @Test
    fun `cancelling restores nothing`() = runBlocking {
        viewModel.importLocalBackup(application, file(LocalBackup.encode(backup)))
        withTimeout(10_000) { viewModel.restorePreview.first { it != null } }

        viewModel.dismissRestore()

        assertNull(viewModel.restorePreview.value)
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())
    }

    @Test
    fun `a backup with nonsense in it says what, and is never offered`() = runBlocking {
        val tampered = backup.copy(transactions = listOf(backup.transactions.single().copy(amount = -500.0)))

        viewModel.importLocalBackup(application, file(LocalBackup.encode(tampered)))
        val message = withTimeout(10_000) { viewModel.statusMessage.first { it != null } }

        assertEquals("That backup can't be restored: an expense (Chai Point) has no proper amount. Nothing was changed.", message)
        assertNull(viewModel.restorePreview.value)
    }
}
