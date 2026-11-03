package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Sheet-first quick add (docs/designs/sheet-first-quick-add.md, REL M5): a typed line opens the
 * sheet at once on the phone's guess, and Gemini's reading, held back here until the test lets it
 * go, fills in only what the person hasn't touched. A reading that arrives after they've moved on
 * changes nothing. Real Room database and real MainViewModel throughout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SheetFirstQuickAddTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val screen = CoroutineScope(Dispatchers.Unconfined + Job())

    /** What the fake Gemini answers with, as the model's JSON, once [aiMayAnswer] opens. */
    @Volatile private var answer = ""
    private val aiMayAnswer = CountDownLatch(1)
    private val asked = AtomicInteger()
    private val answered = AtomicInteger()

    private val gemini = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            asked.incrementAndGet()
            // A slow network: the reading only comes back when the test says so.
            aiMayAnswer.await(10, TimeUnit.SECONDS)
            val text = org.json.JSONObject.quote(answer)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":$text}]}}]}""".toResponseBody("application/json".toMediaType()))
                .build()
                .also { answered.incrementAndGet() }
        }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "test-token" },
        configured = true,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        runBlocking { AppSettings(application).setAiNoticeAccepted(false) }
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking {
            db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF"))
            db.personDao().insertPerson(PersonEntity(id = 2, name = "Neha", avatarColor = "#70C1FF"))
        }
        viewModel = MainViewModel(application, db, userId, geminiRepo = gemini)
        // The app shell collects these while it's open; quick add reads them.
        screen.launch { viewModel.people.collect {} }
        screen.launch { viewModel.groups.collect {} }
        screen.launch { viewModel.rawGroups.collect {} }
        runBlocking { until { viewModel.people.value.size == 2 } }
    }

    @After
    fun tearDown() {
        // Nothing left blocked on the fake network.
        aiMayAnswer.countDown()
        screen.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(20) }

    /**
     * Waits until the request is on the (held-back) network. Cancelled any earlier, it would never
     * be sent at all, which is right but isn't the late answer these tests are about.
     */
    private suspend fun aiIsReading() = until { asked.get() == 1 }

    /** Lets the held-back reading through, and waits until the fake network has sent it. */
    private suspend fun aiAnswers() {
        aiMayAnswer.countDown()
        until { answered.get() == 1 }
    }

    private fun ticked(): Set<Long> = viewModel.shares.value.filter { it.isSelected }.map { it.personId }.toSet()

    private val toit = """{"type":"expense","amount":2400,"merchant":"Toit","category":"Food","paymentMethod":"GPay","date":"","splitWith":["Rohan"],"note":"Dinner at Toit"}"""

    @Test
    fun `the sheet opens on the phone's guess at once, then takes the AI's reading`() = runBlocking {
        answer = toit

        viewModel.parseInputText("dinner at toit 2400 with rohan")
        viewModel.acceptAiNotice()

        // Before the AI has said anything: the sheet is up, with what the phone read.
        assertTrue(viewModel.isConfirmSheetOpen.value)
        assertTrue(viewModel.isImprovingWithAi.value)
        assertFalse("the old full-sheet spinner is gone for typed lines", viewModel.isParsing.value)
        assertEquals("2400", viewModel.amount.value)
        assertEquals("Dinner Toit Rohan", viewModel.merchant.value)
        assertEquals(setOf(-1L, 1L), ticked())

        aiAnswers()
        until { !viewModel.isImprovingWithAi.value }

        assertEquals("Toit", viewModel.merchant.value)
        assertEquals("GPay", viewModel.paymentMethod.value)
        assertEquals("Dinner at Toit", viewModel.note.value)
        assertEquals(setOf(-1L, 1L), ticked())
        assertEquals(2400.0, viewModel.shares.value.filter { it.isSelected }.sumOf { it.amount }, 0.001)
        assertNull(viewModel.parseNotice.value)
    }

    @Test
    fun `what they changed while it was reading stays, and the rest is filled in`() = runBlocking {
        answer = toit

        viewModel.parseInputText("dinner at toit 2400 with rohan")
        viewModel.acceptAiNotice()
        assertTrue(viewModel.isImprovingWithAi.value)

        // They fix the amount, name the place their way, and bring Neha in, before the AI is back.
        viewModel.updateAmount("2550")
        viewModel.updateMerchant("Toit, Indiranagar")
        viewModel.togglePersonShare(2L)

        aiAnswers()
        until { !viewModel.isImprovingWithAi.value }

        assertEquals("2550", viewModel.amount.value)
        assertEquals("Toit, Indiranagar", viewModel.merchant.value)
        assertEquals("their split, not the AI's Rohan-only one", setOf(-1L, 1L, 2L), ticked())
        assertEquals("the shares still add up to their amount", 2550.0, viewModel.shares.value.filter { it.isSelected }.sumOf { it.amount }, 0.001)
        // Untouched, so the AI's reading fills it.
        assertEquals("GPay", viewModel.paymentMethod.value)
        assertEquals("Food", viewModel.category.value)
    }

    @Test
    fun `closing the sheet while it reads leaves the closed sheet alone`() = runBlocking {
        answer = toit

        viewModel.parseInputText("dinner at toit 2400 with rohan")
        viewModel.acceptAiNotice()
        aiIsReading()
        viewModel.dismissConfirmSheet()
        assertFalse(viewModel.isImprovingWithAi.value)

        aiAnswers()
        // The reading has reached the phone; give it every chance to land.
        delay(300)

        assertFalse("a late reading reopened the sheet", viewModel.isConfirmSheetOpen.value)
        assertEquals("Dinner Toit Rohan", viewModel.merchant.value)
    }

    @Test
    fun `a late reading never turns an edit into a new entry`() = runBlocking {
        answer = toit
        val chai = TransactionEntity(type = "expense", amount = 40.0, category = "Food", merchant = "Chai", paymentMethod = "Cash", date = System.currentTimeMillis())
        val chaiId = db.transactionDao().insertTransaction(chai)

        viewModel.parseInputText("dinner at toit 2400 with rohan")
        viewModel.acceptAiNotice()
        aiIsReading()
        // From the activity list, before the reading is back (REL M5: this used to become a new entry).
        viewModel.openEditTransaction(FullTransaction(chai.copy(id = chaiId)))

        aiAnswers()
        delay(300)

        assertEquals(chaiId, viewModel.editingTransactionId.value)
        assertEquals("40", viewModel.amount.value)
        assertEquals("Chai", viewModel.merchant.value)
        assertFalse(viewModel.isImprovingWithAi.value)
    }

    @Test
    fun `a save before the AI is back is saved as it was on the sheet`() = runBlocking {
        answer = """{"type":"expense","amount":45,"merchant":"Masala chai","category":"Beverages","paymentMethod":"UPI","date":"","splitWith":[],"note":"chai"}"""

        viewModel.parseInputText("chai 40 cash")
        viewModel.acceptAiNotice()
        assertTrue(viewModel.isImprovingWithAi.value)
        aiIsReading()
        viewModel.saveCurrentTransaction()
        until { !viewModel.isConfirmSheetOpen.value }

        aiAnswers()
        delay(300)

        val saved = db.transactionDao().getAllTransactionsDirect().single()
        assertEquals(40.0, saved.amount, 0.001)
        assertEquals("Chai", saved.merchant)
        assertEquals("Cash", saved.paymentMethod)
        assertFalse("a late reading reopened the sheet", viewModel.isConfirmSheetOpen.value)
        assertEquals("the sheet's state wasn't rewritten after the save", "40", viewModel.amount.value)
    }
}
