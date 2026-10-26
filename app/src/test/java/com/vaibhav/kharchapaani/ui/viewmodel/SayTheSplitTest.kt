package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Say the split (docs/designs/say-the-split.md): a quick-add line that names friends or a group
 * opens the sheet with that split already filled in, from the real database, both when Gemini reads
 * the line and when the phone does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SayTheSplitTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val screen = CoroutineScope(Dispatchers.Unconfined + Job())

    /** What the fake Gemini answers with, as the model's JSON. */
    @Volatile private var answer = ""

    private val gemini = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { chain ->
            val text = org.json.JSONObject.quote(answer)
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("""{"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":$text}]}}]}""".toResponseBody("application/json".toMediaType()))
                .build()
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
            db.personDao().insertPerson(PersonEntity(id = 2, name = "Ankit Shah", avatarColor = "#70C1FF"))
            db.personDao().insertPerson(PersonEntity(id = 3, name = "Neha", avatarColor = "#70C1FF"))
            db.groupDao().insertGroup(GroupEntity(id = 10, name = "Goa trip"))
            db.groupDao().insertGroupMembers(listOf(GroupMemberEntity(groupId = 10, personId = 1), GroupMemberEntity(groupId = 10, personId = 2)))
        }
        viewModel = MainViewModel(application, db, userId, geminiRepo = gemini)
        // The app shell collects these while it's open; quick add reads them.
        screen.launch { viewModel.people.collect {} }
        screen.launch { viewModel.groups.collect {} }
        screen.launch { viewModel.rawGroups.collect {} }
        runBlocking { until { viewModel.people.value.size == 3 && viewModel.groups.value.size == 1 && viewModel.rawGroups.value.size == 1 } }
    }

    @After
    fun tearDown() {
        screen.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10_000) { while (!condition()) delay(20) }

    private fun ticked(): Set<Long> = viewModel.shares.value.filter { it.isSelected }.map { it.personId }.toSet()

    private fun shareTotal(): Double = viewModel.shares.value.filter { it.isSelected }.sumOf { it.amount }

    @Test
    fun `a group named in the line puts the expense in that group, with its members`() = runBlocking {
        // ₹600 over three: a local group's equal split still rounds each share on its own (₹500
        // would show ₹500.01; TODOS "Equal splits outside shared groups drift by a paisa").
        viewModel.parseInputText("petrol 600 goa trip")
        viewModel.declineAiNotice()

        assertTrue(viewModel.isConfirmSheetOpen.value)
        assertEquals(10L, viewModel.groupId.value)
        assertTrue(viewModel.isSplit.value)
        assertEquals(setOf(-1L, 1L, 2L), ticked())
        assertEquals(600.0, shareTotal(), 0.001)
    }

    @Test
    fun `the phone finds a friend by first name`() = runBlocking {
        viewModel.parseInputText("auto 120 with ankit")
        viewModel.declineAiNotice()

        assertNull(viewModel.groupId.value)
        assertEquals(setOf(-1L, 2L), ticked())
        assertEquals(120.0, shareTotal(), 0.001)
    }

    @Test
    fun `Gemini's names find friends, and one it couldn't place is said on the sheet`() = runBlocking {
        answer = """{"type":"expense","amount":1800,"merchant":"Dinner","category":"Food","paymentMethod":"Cash","date":"","splitWith":["Rohan bhai","Priya"],"note":"dinner 1800 with rohan bhai and priya"}"""

        viewModel.parseInputText("dinner 1800 with rohan bhai and priya")
        viewModel.acceptAiNotice()
        until { viewModel.isConfirmSheetOpen.value && !viewModel.isParsing.value }

        assertNull(viewModel.groupId.value)
        assertEquals(setOf(-1L, 1L), ticked())
        assertEquals(1800.0, shareTotal(), 0.001)
        assertEquals("Couldn't find Priya in your friends, so they're not in the split.", viewModel.parseNotice.value)
    }

    @Test
    fun `naming a group and a friend in it splits with just that friend`() = runBlocking {
        answer = """{"type":"expense","amount":900,"merchant":"Lunch","category":"Food","paymentMethod":"Cash","date":"","splitWith":["Rohan"],"note":"lunch 900 goa trip with rohan"}"""

        viewModel.parseInputText("lunch 900 goa trip with rohan")
        viewModel.acceptAiNotice()
        until { viewModel.isConfirmSheetOpen.value && !viewModel.isParsing.value }

        assertEquals(10L, viewModel.groupId.value)
        assertEquals(setOf(-1L, 1L), ticked())
        assertNull(viewModel.parseNotice.value)
    }
}
