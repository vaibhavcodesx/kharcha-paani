package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * An equal split with friends (not in a shared group) adds up to the amount to the paisa, with
 * the leftover paisa on the payer, as shared groups already do (decision 6). ₹500 over three used
 * to show ₹166.67 × 3 = ₹500.01.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EqualSplitPaisaTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val screen = CoroutineScope(Dispatchers.Unconfined + Job())

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
        viewModel = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555")
        // The app shell collects these while it's open; the sheet reads them.
        screen.launch { viewModel.people.collect {} }
        screen.launch { viewModel.groups.collect {} }
        screen.launch { viewModel.rawGroups.collect {} }
        runBlocking { withTimeout(10_000) { while (viewModel.people.value.size < 2) delay(20) } }
    }

    @After
    fun tearDown() {
        screen.cancel()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `an equal split over three adds up to the amount, the leftover paisa on you`() = runBlocking {
        viewModel.parseInputText("dinner 500 with rohan and neha")
        // No AI in this build: the line is read on the phone, off the main thread, so wait for the sheet.
        if (viewModel.isAiNoticeOpen.value) viewModel.declineAiNotice()
        withTimeout(10_000) { while (!viewModel.isConfirmSheetOpen.value || viewModel.isParsing.value) delay(20) }

        val shares = viewModel.shares.value.filter { it.isSelected }.associate { it.personId to it.amount }
        assertEquals(setOf(-1L, 1L, 2L), shares.keys)
        assertEquals(500.0, shares.values.sum(), 0.0001)
        assertEquals(166.66, shares.getValue(1L), 0.0001)
        assertEquals(166.66, shares.getValue(2L), 0.0001)
        assertEquals(166.68, shares.getValue(-1L), 0.0001)
    }
}
