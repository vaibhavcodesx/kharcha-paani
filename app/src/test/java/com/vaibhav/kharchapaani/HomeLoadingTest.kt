package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeLoadingTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val blocked = AtomicBoolean(false)
    private val release = CountDownLatch(1)
    private val executor = Executors.newFixedThreadPool(4)
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private lateinit var shared: SharedGroupsViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries()
            .setQueryExecutor { command -> executor.execute { if (blocked.get()) release.await(); command.run() } }.build()
    }

    @After fun tearDown() = runBlocking {
        release.countDown()
        if (::vm.isInitialized) {
            val jobs = listOf(vm.viewModelScope.coroutineContext[Job]!!, shared.viewModelScope.coroutineContext[Job]!!)
            store.clear()
            withTimeout(10000) { while (jobs.any { !it.isCompleted }) { dispatcher.scheduler.runCurrent(); delay(10) } }
        }
        db.close()
        executor.shutdownNow()
        Dispatchers.resetMain()
    }

    private fun open() {
        blocked.set(true)
        val uid = "11111111-2222-3333-4444-555555555555"
        vm = MainViewModel(app, db, uid)
        shared = SharedGroupsViewModel(app, db, uid)
        store.put("main", vm); store.put("shared", shared)
        compose.setContent { SplitFlowTheme {
            SplitFlowApp(vm, shared.controller, uid,
                AuthUiState(authState = AuthState.SignedIn(uid, "test@example.com", false)),
                onRetryProfile = {}, onSaveProfile = { _, _ -> }, onSignOut = {})
        } }
        dispatcher.scheduler.runCurrent()
        compose.waitForIdle()
    }

    @Test fun `returning ledger waits for Room instead of showing a false empty state`() = runBlocking {
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 432.0,
            category = "Food", merchant = "Breakfast saved yesterday", paymentMethod = "Cash"))
        open()
        compose.onNodeWithText("Your first entry goes here").assertDoesNotExist()
        release.countDown()
        compose.waitUntil(10000) {
            dispatcher.scheduler.runCurrent()
            compose.onAllNodesWithText("Breakfast saved yesterday").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Your first entry goes here").assertDoesNotExist()
    }

    @Test fun `a genuinely empty ledger shows its first entry invitation only after Room answers`() {
        open()
        compose.onNodeWithText("Your first entry goes here").assertDoesNotExist()
        release.countDown()
        compose.waitUntil(10000) {
            dispatcher.scheduler.runCurrent()
            compose.onAllNodesWithText("Your first entry goes here").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Your first entry goes here").assertExists()
    }
}
