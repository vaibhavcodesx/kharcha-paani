package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.ExportRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REL L3: Verifies that export and ask actions do not capture an Activity across suspension,
 * and instead emit one-shot share chooser intents via [MainViewModel.shareIntents].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShareIntentsTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val mainDispatcher = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private var mainDispatcherInstalled = false

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mainDispatcherInstalled = true
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", viewModel)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        try {
            if (::viewModel.isInitialized) {
                val job = viewModel.viewModelScope.coroutineContext[Job]!!
                store.clear()
                withTimeout(10_000) {
                    while (!job.isCompleted) {
                        mainDispatcher.scheduler.runCurrent()
                        if (!job.isCompleted) delay(20)
                    }
                }
                assertTrue("Fixture must stop MainViewModel before Room closes", job.isCompleted)
            }
        } finally {
            if (::db.isInitialized) db.close()
            if (mainDispatcherInstalled) Dispatchers.resetMain()
        }
    }

    @Test
    fun `exportCsv with rows emits chooser intent on shareIntents`() = runBlocking {
        db.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense",
                amount = 250.0,
                category = "Food",
                merchant = "Dhaba",
                paymentMethod = "UPI",
                date = System.currentTimeMillis(),
            ),
        )

        var emittedIntent: Intent? = null
        val collectJob = launch {
            emittedIntent = viewModel.shareIntents.first()
        }

        viewModel.exportCsv(ExportRange.Everything)
        withTimeout(10_000) { collectJob.join() }

        assertNotNull(emittedIntent)
        assertEquals(Intent.ACTION_CHOOSER, emittedIntent?.action)
        @Suppress("DEPRECATION")
        val target = emittedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target?.action)
        assertEquals("text/csv", target?.type)
        assertTrue(target?.hasExtra(Intent.EXTRA_STREAM) == true)
    }

    @Test
    fun `exportCsv with no rows sets statusMessage and does not emit intent`() = runBlocking {
        var emittedIntent: Intent? = null
        val collectJob = launch {
            emittedIntent = viewModel.shareIntents.first()
        }

        viewModel.exportCsv(ExportRange.Everything)
        val status = withTimeout(10_000) { viewModel.statusMessage.first { it != null } }
        assertTrue(status.contains("Nothing") || status.contains("empty") || status.isNotEmpty())
        assertEquals("No share intent emitted on empty export", null, emittedIntent)

        collectJob.cancel()
    }

    @Test
    fun `exportLocalBackup with rows emits chooser intent on shareIntents`() = runBlocking {
        db.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense",
                amount = 120.0,
                category = "Travel",
                merchant = "Metro",
                paymentMethod = "Card",
                date = System.currentTimeMillis(),
            ),
        )

        var emittedIntent: Intent? = null
        val collectJob = launch {
            emittedIntent = viewModel.shareIntents.first()
        }

        viewModel.exportLocalBackup()
        withTimeout(10_000) { collectJob.join() }

        assertNotNull(emittedIntent)
        assertEquals(Intent.ACTION_CHOOSER, emittedIntent?.action)
        @Suppress("DEPRECATION")
        val target = emittedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target?.action)
        assertEquals("application/json", target?.type)
        assertTrue(target?.hasExtra(Intent.EXTRA_STREAM) == true)
    }

    @Test
    fun `askToPay emits chooser intent with share message`() = runBlocking {
        val person = PersonEntity(id = 42, name = "Aman Sharma", phone = "9876543210")

        var emittedIntent: Intent? = null
        val collectJob = launch {
            emittedIntent = viewModel.shareIntents.first()
        }

        viewModel.askToPay(person, 350.0, "Hey Aman, ₹350 for dinner")
        withTimeout(10_000) { collectJob.join() }

        assertNotNull(emittedIntent)
        assertEquals(Intent.ACTION_CHOOSER, emittedIntent?.action)
        @Suppress("DEPRECATION")
        val target = emittedIntent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target?.action)
        val text = target?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        assertTrue("Intent text should contain message", text.contains("Hey Aman, ₹350 for dinner"))
    }
}
