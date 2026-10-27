package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Column
import com.vaibhav.kharchapaani.ui.components.MonthSwitcher
import com.vaibhav.kharchapaani.ui.components.AccountAvatar
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import java.time.YearMonth
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
class SharedChromeStringResourcesTest {
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
    @Test fun `shared chrome keeps spoken labels while changing the actual insights month`(): Unit = runBlocking {
        val today = YearMonth.now()
        compose.setContent { SplitFlowTheme { Column {
            AccountAvatar("V", SyncStatus(failedChanges = 2), {})
            MonthSwitcher(vm.insightsMonth.collectAsState().value, today.minusMonths(1), today, vm::setInsightsMonth)
        } } }
        compose.onNodeWithContentDescription("Account").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "2 changes couldn't sync"))
        compose.onNodeWithContentDescription("Next month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous month").performClick()
        assertEquals(today.minusMonths(1), vm.insightsMonth.value)
        compose.onNodeWithContentDescription("Previous month").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next month").performClick()
        assertEquals(today, vm.insightsMonth.value)
    }
}
