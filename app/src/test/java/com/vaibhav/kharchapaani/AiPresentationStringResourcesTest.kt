package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.ui.components.AskSection
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.domain.*
import com.vaibhav.kharchapaani.ui.format.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiPresentationStringResourcesTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val screen = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel

    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        screen.launch { vm.people.collect {} }
        until { vm.people.value.any { it.id == 2L } }
    }
    private suspend fun until(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }
    @After fun tearDown(): Unit = runBlocking {
        screen.cancel()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear(); until { job.isCompleted }
        db.close(); Dispatchers.resetMain()
    }
    @Test fun `AI display resources retain every existing prompt label and error sentence`() {
        assertEquals("Filled in on your phone. Check the amount and details.", app.getString(R.string.ai_display_local_filled))
        assertEquals("Read the bill, but couldn't find the total. Fill in the amount.", app.getString(R.string.ai_display_receipt_missing_total))
        assertEquals("Read from your bill. The photo is kept below.", app.getString(R.string.ai_display_receipt_photo_kept))
        AiPrompt.entries.forEach {
            assertEquals(it.label, app.getString(it.labelResource))
            assertEquals(it.eyebrow, app.getString(it.eyebrowResource))
        }
        AiFailure.entries.forEach {
            assertEquals(it.askMessage(), app.getString(it.answerMessageResource))
            assertEquals(it.quickAddNotice(), app.getString(it.quickAddNoticeResource))
            assertEquals(it.receiptNotice(), app.getString(it.receiptNoticeResource))
        }
    }
    @Test fun `actual empty insights board keeps its questions and empty guidance`(): Unit = runBlocking {
        val hasSpending = db.transactionDao().getAllTransactions().first().isNotEmpty()
        compose.setContent { SplitFlowTheme {
            AskSection(AiPrompt.forInsights, vm.askBoard.value, hasSpending, 20, "this month's", vm::askInsights)
        } }
        compose.onNodeWithText("Ask about this month's money").assertExists()
        compose.onNodeWithText("This month").assertExists()
        compose.onNodeWithText("What to change").assertExists()
        compose.onNodeWithText("Log a few expenses first; there's nothing to read yet.").assertExists()
    }
}
