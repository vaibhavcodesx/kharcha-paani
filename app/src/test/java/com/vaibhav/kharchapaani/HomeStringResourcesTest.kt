package com.vaibhav.kharchapaani

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.shell.KpDock
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import java.time.LocalDate
import java.time.ZoneId
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w411dp-h2000dp")
class HomeStringResourcesTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private lateinit var root: View
    private lateinit var shared: SharedGroupsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
    }
    @After fun tearDown() = runBlocking {
        val jobs = listOfNotNull(vm.viewModelScope.coroutineContext[Job], if (::shared.isInitialized) shared.viewModelScope.coroutineContext[Job] else null)
        store.clear()
        withTimeout(10000) { while (jobs.any { !it.isCompleted }) { dispatcher.scheduler.runCurrent(); delay(10) } }
        db.close(); Dispatchers.resetMain()
    }
    @Test fun `Home keeps its English labels and formats real day totals and the streak`() = runBlocking {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        for ((day, amount) in listOf(today to 20.0, today.minusDays(1) to 10.0)) {
            db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = amount,
                category = "Food", merchant = "Tea", paymentMethod = "Cash", date = day.atStartOfDay(zone).toInstant().toEpochMilli()))
        }
        val uid = "11111111-2222-3333-4444-555555555555"
        shared = SharedGroupsViewModel(app, db, uid)
        store.put("shared", shared)
        compose.setContent { SplitFlowTheme {
            SplitFlowApp(vm, shared.controller, uid,
                AuthUiState(authState = AuthState.SignedIn(uid, "test@example.com", false)),
                onRetryProfile = {}, onSaveProfile = { _, _ -> }, onSignOut = {})
        } }
        compose.waitUntil(10000) {
            dispatcher.scheduler.runCurrent()
            compose.onAllNodesWithText("Logged 2 days in a row").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("scan_receipt_button").assertTextEquals("Bill photo")
        compose.onNodeWithContentDescription("Say it").assertHasClickAction()
        compose.onNodeWithText("SPENT THIS MONTH").assertExists()
        compose.onNodeWithText("Today").assertExists()
        compose.onNodeWithText("₹20 spent").assertExists()
    }
}
