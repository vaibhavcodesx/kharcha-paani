package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AiSettings
import com.vaibhav.kharchapaani.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AiChoiceLifecycleTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val settings = AppSettings(app)
    private val choice = AiSettings(app)
    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val userId = UUID.randomUUID().toString()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private var installed = false
    private var captured = false
    private var hadEnabled = false
    private var previousEnabled = true
    private var previousConsent: Boolean? = null
    private val release = CountDownLatch(1)
    private val requests = AtomicInteger()
    private val replies = AtomicInteger()
    private val activeCall = AtomicReference<Call>()
    private val reading = """{"type":"expense","amount":999,"merchant":"Late AI merchant","category":"Food","paymentMethod":"Cash","date":"","splitWith":[],"note":"late"}"""
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        activeCall.set(chain.call())
        check(release.await(10, TimeUnit.SECONDS)) { "Test never released the held response" }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":${JSONObject.quote(reading)}}]}}]}""".toResponseBody("application/json".toMediaType()))
            .build().also { replies.incrementAndGet() }
    }.build()

    private fun newVm(key: String): MainViewModel = MainViewModel(app, db, userId,
        geminiRepo = GeminiRepository(client = client,
            endpoint = { "https://example.invalid/models/$it:generateContent" },
            accessToken = { "test-token" }, configured = true,
            aiAllowed = { choice.enabledNow })).also { store.put(key, it); extraJobs += it.viewModelScope.coroutineContext[Job]!! }

    @Before fun setup(): Unit = runBlocking {
        Dispatchers.setMain(main)
        installed = true
        hadEnabled = prefs.contains("enabled")
        previousEnabled = prefs.getBoolean("enabled", true)
        captured = true
        previousConsent = settings.aiNoticeAccepted.first()
        choice.setEnabled(true)
        settings.setAiNoticeAccepted(false)
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        vm = newVm("first")
        main.scheduler.runCurrent()
    }

    private suspend fun until(ready: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            main.scheduler.runCurrent()
            ShadowLooper.idleMainLooper()
            main.scheduler.runCurrent()
            if (ready()) return@withTimeout
            delay(10)
        }
    }

    private suspend fun stopHeldRead(start: () -> Unit) {
        val existing = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        start()
        assertTrue(vm.isAiNoticeOpen.value)
        vm.acceptAiNotice()
        until { requests.get() == 1 }
        val started = vm.viewModelScope.coroutineContext[Job]!!.children.filter { it !in existing }.toList()
        assertTrue("The real VM must own the active read", started.isNotEmpty())
        vm.setAiEnabled(false)
        // Check cancellation while the interceptor is still blocked, before allowing any response.
        until { activeCall.get()?.isCanceled() == true && !choice.enabledNow }
        vm.updateAmount("125.50")
        vm.updateMerchant("Typed after switching off")
        release.countDown()
        until { replies.get() == 1 && client.dispatcher.runningCallsCount() == 0 && started.all { it.isCompleted } }
        assertFalse(vm.isParsing.value)
        assertFalse(vm.isImprovingWithAi.value)
        assertEquals("125.50", vm.amount.value)
        assertEquals("Typed after switching off", vm.merchant.value)
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())
        assertEquals(1, requests.get())
    }

    @Test fun `switching off cancels held text HTTP and prevents its late draft overwrite`(): Unit = runBlocking {
        stopHeldRead { vm.parseInputText("chai 40 cash") }
    }

    @Test fun `switching off cancels held receipt HTTP and prevents its late draft overwrite`(): Unit = runBlocking {
        val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try { stopHeldRead { vm.parseReceiptImage(image) } }
        finally { image.recycle() }
    }

    @Test fun `persisted off defeats a stale consent callback and enabling again grants no consent`(): Unit = runBlocking {
        vm.parseInputText("chai 40 cash")
        assertTrue(vm.isAiNoticeOpen.value)
        // Persist through a second settings object, without pumping Main: the old callback exists.
        AiSettings(app).setEnabled(false)
        assertTrue("Invoke the still-displayed callback before Main processes the preference", vm.isAiNoticeOpen.value)
        vm.acceptAiNotice()
        assertFalse(settings.aiNoticeAccepted.first())
        until { !vm.aiEnabled.value && !vm.isAiNoticeOpen.value }
        val replacement = newVm("replacement")
        assertFalse("New VM must read the persisted choice synchronously", replacement.aiEnabled.value)
        replacement.setAiEnabled(true)
        until { replacement.aiEnabled.value && choice.enabledNow }
        assertFalse(settings.aiNoticeAccepted.first())
        replacement.parseInputText("lunch 80 cash")
        assertTrue("Off to On must still show the AI disclosure", replacement.isAiNoticeOpen.value)
        replacement.declineAiNotice()
        assertEquals("80", replacement.amount.value)
        assertEquals(0, requests.get())
    }

    @After fun close(): Unit = runBlocking {
        release.countDown()
        try {
            val jobs = extraJobs.toList()
            store.clear()
            until { jobs.all { it.isCompleted } && client.dispatcher.runningCallsCount() == 0 }
        } finally {
            try {
                if (::db.isInitialized) db.close()
                ReceiptStore.forAccount(app, userId).dir.deleteRecursively()
                previousConsent?.let { settings.setAiNoticeAccepted(it) }
                if (captured) {
                    val edit = prefs.edit()
                    if (hadEnabled) edit.putBoolean("enabled", previousEnabled) else edit.remove("enabled")
                    assertTrue(edit.commit())
                }
            } finally { if (installed) Dispatchers.resetMain() }
        }
    }

    private val extraJobs = mutableListOf<Job>()
}
