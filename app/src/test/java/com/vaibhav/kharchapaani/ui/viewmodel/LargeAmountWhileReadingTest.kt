package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.ui.components.ConfirmSheetContent
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * #195 review P1: Save on a large amount first asks "That's ₹15,00,000. Right?". An AI reading that
 * landed behind that dialog could change the draft, so "Yes, save" wrote a figure the person never
 * confirmed. The real sheet, wired to the real ViewModel as MainActivity wires it, a real Room
 * database, and an AI held back until the dialog is up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class LargeAmountWhileReadingTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    // The shell keeps the friends list subscribed for the whole session. Hold it here too, off the
    // compose clock, so quick add finds Rohan however the test machine schedules the composition.
    private val shell = CoroutineScope(Dispatchers.Unconfined + Job())

    private val aiMayAnswer = CountDownLatch(1)
    private val asked = AtomicInteger()
    private val answered = AtomicInteger()
    // A different amount and a split: neither may reach the saved row.
    private val reading = """{"type":"expense","amount":2000000,"merchant":"Laptop","category":"Shopping","paymentMethod":"UPI","date":"","splitWith":["Rohan"],"note":"laptop"}"""

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        asked.incrementAndGet()
        aiMayAnswer.await(10, TimeUnit.SECONDS)
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":${org.json.JSONObject.quote(reading)}}]}}]}""".toResponseBody("application/json".toMediaType()))
            .build()
            .also { answered.incrementAndGet() }
    }.build()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        runBlocking { AppSettings(application).setAiNoticeAccepted(false) }
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF")) }
        val gemini = GeminiRepository(
            client = client,
            endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
            accessToken = { "test-token" },
            configured = true,
        )
        viewModel = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555", geminiRepo = gemini)
        store.put("main", viewModel)
        shell.launch { viewModel.people.collect {} }
    }

    @After
    fun tearDown() {
        aiMayAnswer.countDown()
        shell.cancel()
        if (::viewModel.isInitialized) {
            val job = viewModel.viewModelScope.coroutineContext[Job]!!
            store.clear()
            // Its jobs finish on the real main looper here, so keep it turning rather than block it.
            val deadline = System.currentTimeMillis() + 10_000
            while (!job.isCompleted && System.currentTimeMillis() < deadline) ShadowLooper.idleMainLooper()
            check(job.isCompleted) { "the ViewModel's jobs didn't finish" }
        }
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `the amount confirmed in the large-amount check is the amount saved`() {
        compose.setContent {
            val people by viewModel.people.collectAsState()
            val amount by viewModel.amount.collectAsState()
            val merchant by viewModel.merchant.collectAsState()
            val category by viewModel.category.collectAsState()
            val paymentMethod by viewModel.paymentMethod.collectAsState()
            val type by viewModel.type.collectAsState()
            val isSplit by viewModel.isSplit.collectAsState()
            val shares by viewModel.shares.collectAsState()
            val open by viewModel.isConfirmSheetOpen.collectAsState()
            people.size // the shell keeps the friends list live; quick add reads it
            if (open) SplitFlowTheme {
                ConfirmSheetContent(
                    isParsing = false, isEditing = false, type = type, amount = amount, merchant = merchant, category = category,
                    paymentMethod = paymentMethod, note = "", date = 0L, groupId = null, availableGroups = emptyList(),
                    isSplit = isSplit, splitMode = viewModel.splitMode.value, paidByPersonId = -1L, shares = shares,
                    onTypeChange = {}, onAmountChange = {}, onMerchantChange = {}, onCategoryChange = {}, onPaymentMethodChange = {},
                    onNoteChange = {}, onDateChange = {}, onGroupIdChange = {}, onIsSplitChange = {}, onSplitModeChange = {},
                    onPaidByPersonIdChange = {}, onTogglePersonShare = {}, onExactAmountChange = { _, _ -> },
                    onPercentageChange = { _, _ -> }, onOpenAddFriend = {},
                    onSaveIntent = viewModel::holdDraftForSave,
                    onSave = { viewModel.saveCurrentTransaction() },
                )
            }
        }
        waitFor("Rohan in the friends list quick add reads") { viewModel.people.value.size == 1 }

        compose.runOnIdle {
            viewModel.parseInputText("laptop 1500000 upi")
            viewModel.acceptAiNotice()
        }
        compose.waitUntil(10_000) { asked.get() == 1 && viewModel.amount.value == "1500000" }

        // Save on ₹15,00,000: the check comes up first.
        compose.onNodeWithTag("confirm_save_button").performClick()
        compose.onNodeWithTag("large_amount_confirm").assertExists()

        // The AI's reading (₹20,00,000, split with Rohan) lands behind the dialog.
        aiMayAnswer.countDown()
        compose.waitUntil(10_000) { answered.get() == 1 && client.dispatcher.runningCallsCount() == 0 }
        compose.waitForIdle()

        compose.onNodeWithTag("large_amount_confirm").performClick()
        compose.waitUntil(10_000) { runBlocking { db.transactionDao().getAllTransactionsDirect().isNotEmpty() } }

        val saved = runBlocking { db.transactionDao().getAllTransactionsDirect() }.single()
        assertEquals("saved what the dialog asked about", 1_500_000.0, saved.amount, 0.001)
        assertFalse("the AI's split didn't arrive behind the dialog", saved.isSplit)
    }

    /** [compose.waitUntil], but a timeout says what it was waiting for and what the database held. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        try {
            compose.waitUntil(10_000) {
                // Room sharing resumes on native Main, independently of Compose's frame clock.
                ShadowLooper.idleMainLooper()
                condition()
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            val inDb = runBlocking { db.personDao().getAllPeopleDirect().map { it.name } }
            throw AssertionError("timed out waiting for $what; people flow=${viewModel.people.value.map { it.name }}, database=$inDb", e)
        }
    }
}
