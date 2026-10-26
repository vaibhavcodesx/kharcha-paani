package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A second tap while the first save is still writing does nothing (REL H1). It logged the dinner
 * twice, and on Create group made two shared groups on the server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DoubleSubmitTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    /**
     * Main runs only when the test says so, so the first save is certainly still under way when the
     * second tap comes. Unconfined, the first one could finish in between, and the test then saw two.
     */
    private val main = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10_000) {
        while (true) {
            main.scheduler.advanceUntilIdle()
            if (condition()) break
            delay(20)
        }
    }

    @Test
    fun `two taps on Save save the expense once`() = runBlocking {
        viewModel.openManualAdd()
        viewModel.updateAmount("450")
        viewModel.updateMerchant("Dinner")

        viewModel.saveCurrentTransaction()
        viewModel.saveCurrentTransaction()
        until { !viewModel.isConfirmSheetOpen.value }
        // Long enough for a second write that was let through to land.
        until { true }
        delay(300)
        main.scheduler.advanceUntilIdle()

        assertEquals(1, db.transactionDao().getAllTransactionsDirect().size)
    }

    @Test
    fun `two taps on Create group make one group`() = runBlocking {
        viewModel.createNewGroup("Goa trip", "", "🏖️", "#FF7A3D", emptyList(), shared = true)
        viewModel.createNewGroup("Goa trip", "", "🏖️", "#FF7A3D", emptyList(), shared = true)
        until { db.groupDao().getAllGroupsDirect().isNotEmpty() }
        delay(300)
        main.scheduler.advanceUntilIdle()

        assertEquals(1, db.groupDao().getAllGroupsDirect().size)
    }
}
