package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.receipts.BillPhoto
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.domain.AiPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Persisted off must override prior consent at every existing AI entry point. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AiDisabledTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val settings = AppSettings(app)
    private val aiPreferences = app.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val screen = CoroutineScope(SupervisorJob() + main)
    private val requests = AtomicInteger()
    private val ownedFiles = mutableListOf<File>()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private var mainInstalled = false
    private var preferencesCaptured = false
    private var hadEnabled = false
    private var previousEnabled = true
    private var previousConsent: Boolean? = null

    // Only the external HTTP boundary is replaced. A refusal avoids spending local answer quota.
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        requests.incrementAndGet()
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
            .code(403).message("Forbidden")
            .body("""{"error":{"status":"PERMISSION_DENIED"}}""".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(main)
        mainInstalled = true
        hadEnabled = aiPreferences.contains("enabled")
        previousEnabled = aiPreferences.getBoolean("enabled", true)
        preferencesCaptured = true
        previousConsent = settings.aiNoticeAccepted.first()
        assertTrue(aiPreferences.edit().putBoolean("enabled", false).commit())
        settings.setAiNoticeAccepted(true)
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        val repository = GeminiRepository(
            client = client,
            endpoint = { model -> "https://example.invalid/models/$model:generateContent" },
            accessToken = { "test-token" },
            configured = true,
        )
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555", geminiRepo = repository)
        store.put("main", vm)
        main.scheduler.runCurrent()
    }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            main.scheduler.runCurrent()
            if (condition()) break
            yield()
        }
    }

    /**
     * Await the jobs actually started by an action, including sibling receipt jobs launched
     * after shared-image decoding. A no-op disabled path can finish immediately. Prior consent
     * is persisted, but accepting a stale displayed notice prevents a false green on old code.
     */
    private suspend fun finishAction(ready: () -> Boolean = { true }, action: () -> Unit) {
        main.scheduler.runCurrent()
        val scope = vm.viewModelScope.coroutineContext[Job]!!
        val existing = scope.children.toSet()
        val started = mutableSetOf<Job>()
        action()
        if (vm.isAiNoticeOpen.value) vm.acceptAiNotice()
        until {
            // Decoding a shared image may only now have reached the consent gate.
            if (vm.isAiNoticeOpen.value) vm.acceptAiNotice()
            started += scope.children.filter { it !in existing }
            started.all { it.isCompleted } && ready()
        }
    }

    @After fun tearDown(): Unit = runBlocking {
        try {
            val jobs = mutableListOf<Job>()
            screen.coroutineContext[Job]?.let(jobs::add)
            screen.cancel()
            if (::vm.isInitialized) jobs += vm.viewModelScope.coroutineContext[Job]!!
            store.clear()
            until { jobs.all { it.isCompleted } }
        } finally {
            try {
                if (::db.isInitialized) db.close()
                ownedFiles.forEach { it.delete() }
                previousConsent?.let { settings.setAiNoticeAccepted(it) }
                if (preferencesCaptured) {
                    val edit = aiPreferences.edit()
                    if (hadEnabled) edit.putBoolean("enabled", previousEnabled) else edit.remove("enabled")
                    assertTrue(edit.commit())
                }
            } finally {
                if (mainInstalled) Dispatchers.resetMain()
            }
        }
    }

    @Test fun `AI off keeps typed quick add local and saves its real amount`(): Unit = runBlocking {
        finishAction(ready = { vm.isConfirmSheetOpen.value && !vm.isImprovingWithAi.value }) {
            vm.parseInputText("chai 40 cash")
        }
        finishAction { vm.saveCurrentTransaction(false) }
        val row = db.transactionDao().getAllTransactions().first().single()
        assertEquals(40.0, row.amount, 0.0)
        assertEquals("Food", row.category)
        assertEquals("Cash", row.paymentMethod)
        assertEquals("chai 40 cash", row.note)
        assertEquals("AI off must prevent text requests even after prior consent", 0, requests.get())
    }

    @Test fun `AI off keeps a bill photo available for a manual expense`(): Unit = runBlocking {
        finishAction(ready = { vm.isConfirmSheetOpen.value && vm.billPhoto.value is BillPhoto.Kept && !vm.isParsing.value }) {
            vm.parseReceiptImage(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        }
        assertNotNull((vm.billPhoto.value as BillPhoto.Kept).staged)
        vm.updateAmount("125.50")
        vm.updateMerchant("Manual receipt")
        finishAction { vm.saveCurrentTransaction(false) }
        val row = db.transactionDao().getAllTransactions().first().single()
        assertEquals(125.50, row.amount, 0.0)
        assertEquals("Manual receipt", row.merchant)
        assertEquals(1, db.receiptDao().forTransaction(row.id).size)
        assertEquals("AI off must prevent receipt requests while retaining the photo", 0, requests.get())
    }

    @Test fun `AI off does not upload statement bytes`(): Unit = runBlocking {
        val pdf = "%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%EOF".toByteArray()
        finishAction(ready = { !vm.isParsingStatement.value }) {
            vm.parseBankOrUpiStatement(pdf, "invented.pdf")
        }
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        assertEquals("AI off must prevent statement upload after prior consent", 0, requests.get())
    }

    @Test fun `AI off does not request an Insights answer`(): Unit = runBlocking {
        assertTrue("Fixture needs available AI quota", settings.aiUsage.first().canAsk(java.time.LocalDate.now()))
        finishAction(ready = { !vm.askBoard.value.isAsking }) {
            vm.askInsights(AiPrompt.MonthInReview)
        }
        assertEquals("AI off must prevent Insights requests after prior consent", 0, requests.get())
    }

    @Test fun `AI off does not request a real groups trip review`(): Unit = runBlocking {
        assertTrue("Fixture needs available AI quota", settings.aiUsage.first().canAsk(java.time.LocalDate.now()))
        db.groupDao().insertGroup(GroupEntity(id = 7, name = "Invented trip"))
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 50.0,
            category = "Food", merchant = "Test lunch", paymentMethod = "Cash", groupId = 7))
        screen.launch { vm.groups.collect {} }
        until { vm.groups.value.any { it.group.id == 7L && it.transactionsCount == 1 } }
        finishAction { vm.askTripReview(7L) }
        assertEquals(50.0, db.transactionDao().getAllTransactions().first().single().amount, 0.0)
        assertEquals("AI off must prevent trip review requests after prior consent", 0, requests.get())
    }

    @Test fun `AI off keeps a shared image local`(): Unit = runBlocking {
        val file = File.createTempFile("ai-off-shared-", ".png", app.cacheDir).also(ownedFiles::add)
        file.outputStream().use {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        finishAction(ready = { vm.isConfirmSheetOpen.value && vm.billPhoto.value is BillPhoto.Kept && !vm.isParsing.value }) {
            vm.handleSharedImageUri(app, Uri.fromFile(file))
        }
        assertNotNull((vm.billPhoto.value as BillPhoto.Kept).staged)
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        assertEquals("AI off must cover shared images too", 0, requests.get())
    }
}
