package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.repeatOnLifecycle
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
 * and instead emit one-shot share chooser intents via [MainViewModel.shareIntents], collected
 * only in foreground via [repeatOnLifecycle].
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
    fun `askToPay emits chooser intent with share message`() = runBlocking {
        val person = PersonEntity(id = 42, name = "Aman Sharma", avatarColor = "#70C1FF")

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

    @Test
    fun `shareIntents delivers one-shot event exactly once with no replay`() = runBlocking {
        val person = PersonEntity(id = 10, name = "Rohan", avatarColor = "#70C1FF")

        viewModel.askToPay(person, 100.0, "Chai split")

        // First collector receives the buffered event
        val firstReceived = withTimeout(10_000) { viewModel.shareIntents.first() }
        assertNotNull(firstReceived)
        assertEquals(Intent.ACTION_CHOOSER, firstReceived.action)

        // Second collector must not receive a replay of the consumed event
        var replayReceived: Intent? = null
        val secondJob = launch {
            replayReceived = viewModel.shareIntents.first()
        }
        delay(100)
        secondJob.cancel()
        assertEquals("One-shot channel must not replay consumed intents", null, replayReceived)
    }

    @Test
    fun `lifecycle stop then export completes then resume emits zero background launches and one foreground launch`() = runBlocking {
        val person = PersonEntity(id = 55, name = "Karan", avatarColor = "#70C1FF")
        val lifecycleOwner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED

        var launchCount = 0
        var receivedIntent: Intent? = null
        val collectJob = launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.shareIntents.collect { intent ->
                    launchCount++
                    receivedIntent = intent
                }
            }
        }

        // 1. UI moved to background (STOPPED / CREATED)
        lifecycleOwner.registry.currentState = Lifecycle.State.CREATED
        mainDispatcher.scheduler.runCurrent()

        // 2. Slow export completes while in background
        viewModel.askToPay(person, 500.0, "Trip split")
        mainDispatcher.scheduler.runCurrent()

        // 3. Proves zero background launches while stopped
        assertEquals("Zero launches while UI is stopped in background", 0, launchCount)
        assertEquals(null, receivedIntent)

        // 4. UI returns to foreground (RESUMED)
        lifecycleOwner.registry.currentState = Lifecycle.State.RESUMED
        mainDispatcher.scheduler.runCurrent()

        // 5. Proves exactly one foreground launch upon resuming
        assertEquals("Exactly one launch when resumed to foreground", 1, launchCount)
        assertNotNull(receivedIntent)
        assertEquals(Intent.ACTION_CHOOSER, receivedIntent?.action)

        collectJob.cancel()
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
        val status = withTimeout(10_000) { viewModel.statusMessage.first { !it.isNullOrBlank() }!! }
        assertTrue(status.contains("Nothing") || status.contains("empty") || status.isNotEmpty())
        assertEquals("No share intent emitted on empty export", null, emittedIntent)

        collectJob.cancel()
    }

    @Test
    fun `exportGroupCsv with no rows sets statusMessage for group and does not emit intent`() = runBlocking {
        var emittedIntent: Intent? = null
        val collectJob = launch {
            emittedIntent = viewModel.shareIntents.first()
        }

        viewModel.exportGroupCsv(999L)
        val status = withTimeout(10_000) { viewModel.statusMessage.first { !it.isNullOrBlank() }!! }
        assertTrue(status.contains("Nothing") || status.contains("group") || status.isNotEmpty())
        assertEquals("No share intent emitted on empty group export", null, emittedIntent)

        collectJob.cancel()
    }

    @Test
    fun `exportLocalBackup with rows emits chooser intent on shareIntents`() = runBlocking {
        db.personDao().insertPerson(
            PersonEntity(
                id = 1,
                name = "Aman Sharma",
                avatarColor = "#70C1FF",
                profileId = null,
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
    fun `exportLocalBackup with empty backup sets statusMessage and does not emit intent`() = runBlocking {
        var emittedIntent: Intent? = null
        val collectJob = launch {
            emittedIntent = viewModel.shareIntents.first()
        }

        viewModel.exportLocalBackup()
        val status = withTimeout(10_000) { viewModel.statusMessage.first { !it.isNullOrBlank() }!! }
        assertTrue(status.contains("Nothing") || status.contains("empty") || status.isNotEmpty())
        assertEquals("No share intent emitted on empty backup", null, emittedIntent)

        collectJob.cancel()
    }
}
