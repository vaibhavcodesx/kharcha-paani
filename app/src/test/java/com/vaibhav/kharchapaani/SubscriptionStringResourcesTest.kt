package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.ui.components.SubscriptionsContent
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import java.time.LocalDate
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
class SubscriptionStringResourcesTest {
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
    @Test fun `subscription labels save the same schedule and exact name`(): Unit = runBlocking {
        screen.launch { vm.subscriptions.collect {} }
        compose.setContent { SplitFlowTheme {
            SubscriptionsContent(emptyList(), CategoryPrefs(), vm::addSubscription, vm::cancelSubscription,
                vm::deleteSubscription, today = LocalDate.now())
        } }
        compose.onNodeWithText("Subscriptions").assertExists()
        compose.onNodeWithTag("add_subscription_button").assertIsNotEnabled()
        compose.onNodeWithTag("subscription_name_input").performScrollTo().performTextReplacement("Asha's 50% gym")
        compose.onNodeWithTag("subscription_amount_input").performScrollTo().performTextReplacement("649.50")
        compose.onNodeWithTag("add_subscription_button").assertTextEquals("Add subscription").performClick()
        until { vm.subscriptions.value.any { it.name == "Asha's 50% gym" } && vm.submitting.value.isEmpty() }
        val saved = vm.subscriptions.value.single()
        assertEquals(649.50, saved.amount, 0.0)
        assertEquals(SubscriptionCycle.MONTHLY.name, saved.cycle)
        compose.onNodeWithTag("backdate_dialog").assertDoesNotExist()
    }
}
