package com.vaibhav.kharchapaani

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.YearMonth
import java.util.concurrent.TimeUnit

/**
 * Verifies that InsightsScreen root flows collected via collectAsStateWithLifecycle
 * in SplitFlowApp bind cleanly to the Activity lifecycle and compose without error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InsightsTabLifecycleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private lateinit var sharedGroups: SharedGroupsViewModel
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId)
        store.put("main", viewModel)
        sharedGroups = SharedGroupsViewModel(application, db, userId)
        store.put("shared", sharedGroups)
        compose.setContent {
            SplitFlowTheme {
                SplitFlowApp(
                    viewModel = viewModel,
                    sharedGroups = sharedGroups.controller,
                    selfProfileId = userId,
                    authUiState = AuthUiState(authState = AuthState.SignedIn(userId = userId, email = "test@example.com", isRefreshPending = false)),
                    onRetryProfile = {},
                    onSaveProfile = { _, _ -> },
                    onSignOut = {},
                )
            }
        }
    }

    @After
    fun tearDown() {
        if (::viewModel.isInitialized || ::sharedGroups.isInitialized) {
            val jobs = listOfNotNull(
                if (::viewModel.isInitialized) viewModel.viewModelScope.coroutineContext[Job] else null,
                if (::sharedGroups.isInitialized) sharedGroups.viewModelScope.coroutineContext[Job] else null,
            )
            store.clear()
            val deadline = System.currentTimeMillis() + 10_000
            while (jobs.any { !it.isCompleted } && System.currentTimeMillis() < deadline) ShadowLooper.idleMainLooper()
            check(jobs.all { it.isCompleted }) { "the ViewModels' jobs didn't finish" }
        }
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `navigating to Insights tab renders analytics and export controls successfully`() {
        compose.onNodeWithTag("tab_insights").performClick()
        compose.waitForIdle()

        assertEquals(AppTab.Insights.ordinal, viewModel.currentTab.value)
        compose.onNodeWithTag("insights_spent_figure").assertExists()
        compose.onNodeWithTag("export_csv_button").assertExists()
    }

    @Test
    fun `backgrounding below STARTED halts collection and resuming receives mutated Room analytics`() {
        // 1. Seed nonzero Room data
        val now = System.currentTimeMillis()
        runBlocking {
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    id = 1,
                    type = "expense",
                    amount = 1200.0,
                    category = "Food",
                    merchant = "Dhaba",
                    paymentMethod = "UPI",
                    date = now,
                ),
            )
        }

        // 2. Open Insights tab and verify initial nonzero spent figure
        compose.onNodeWithTag("tab_insights").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("insights_spent_figure").assertExists()
        assertEquals(1200.0, viewModel.insightsAnalytics.value.totalExpense, 0.01)
        assertEquals(YearMonth.now(), viewModel.insightsMonth.value)

        // 3. Move Activity below STARTED (STOPPED via CREATED state)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        // 4. Advance past the 5000ms WhileSubscribed stop timeout without adding other subscribers
        ShadowLooper.idleMainLooper(5500, TimeUnit.MILLISECONDS)

        // 5. Mutate Room data while UI is stopped in background
        runBlocking {
            db.transactionDao().insertTransaction(
                TransactionEntity(
                    id = 2,
                    type = "expense",
                    amount = 800.0,
                    category = "Travel",
                    merchant = "Metro",
                    paymentMethod = "UPI",
                    date = now,
                ),
            )
        }

        // 6. Resume Activity to RESUMED (passing STARTED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()

        // 7. Verify updated analytics reflect the Room mutation after resuming
        compose.onNodeWithTag("insights_spent_figure").assertExists()
        assertEquals(2000.0, viewModel.insightsAnalytics.value.totalExpense, 0.01)
        assertEquals(YearMonth.now(), viewModel.insightsMonth.value)
        assertEquals(AppTab.Insights.ordinal, viewModel.currentTab.value)
    }
}
