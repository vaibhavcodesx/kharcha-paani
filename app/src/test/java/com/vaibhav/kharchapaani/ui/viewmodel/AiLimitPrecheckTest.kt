package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * When the mic, Bill photo or Statement button is tapped, a paused AI or a used-up daily cap is said
 * at once, before anything is recorded, photographed or picked. If the server cannot answer, the
 * button carries on and the real call decides.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiLimitPrecheckTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val asked = mutableListOf<String>()
    private var answer: Boolean? = true

    private val gemini = GeminiRepository(
        client = OkHttpClient.Builder().build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        runBlocking { AiSettings(application).setEnabled(true) }
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(
            application, db, "11111111-2222-3333-4444-555555555555",
            geminiRepo = gemini,
            aiReadAllowed = { feature -> asked += feature; answer },
        )
        store.put("main", viewModel)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        val job = viewModel.viewModelScope.coroutineContext[Job]!!
        store.clear()
        until { job.isCompleted }
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) { dispatcher.scheduler.runCurrent(); delay(20) } }

    @Test
    fun `when reads are allowed the button carries on and nothing is said`() = runBlocking {
        val ran = AtomicInteger()
        answer = true
        viewModel.guardAiRead(AiFeature.Bill) { ran.incrementAndGet() }
        until { ran.get() == 1 }
        assertEquals(listOf("bill"), asked)
        assertNull(viewModel.statusMessage.value)
    }

    @Test
    fun `when AI is paused or the cap is used the button stops and says so at once`() = runBlocking {
        val ran = AtomicInteger()
        answer = false
        for (feature in listOf(AiFeature.Voice, AiFeature.Bill, AiFeature.Statement)) {
            viewModel.guardAiRead(feature) { ran.incrementAndGet() }
        }
        until { asked.size == 3 && viewModel.statusMessage.value != null }
        assertEquals(listOf("quick_add", "bill", "statement"), asked)
        assertEquals("none of the three starts", 0, ran.get())
        assertTrue(viewModel.statusMessage.value!!.contains("used up"))
    }

    @Test
    fun `when the server cannot be asked the button carries on`() = runBlocking {
        val ran = AtomicInteger()
        answer = null
        viewModel.guardAiRead(AiFeature.Statement) { ran.incrementAndGet() }
        until { ran.get() == 1 }
        assertNull(viewModel.statusMessage.value)
    }

    @Test
    fun `with AI turned off nothing is asked and the button carries on to its own nudge`() = runBlocking {
        val ran = AtomicInteger()
        viewModel.setAiEnabled(false)
        until { !viewModel.aiEnabled.value }
        viewModel.guardAiRead(AiFeature.Bill) { ran.incrementAndGet() }
        until { ran.get() == 1 }
        assertTrue("no server question while AI is off", asked.isEmpty())
        assertFalse(viewModel.statusMessage.value != null)
        assertNotNull(viewModel)
    }
}
