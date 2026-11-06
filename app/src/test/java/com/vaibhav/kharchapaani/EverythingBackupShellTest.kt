package com.vaibhav.kharchapaani

import android.app.Application
import android.content.Context
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real guest VM factories and Room around the actual shell settings/export callback wiring. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class EverythingBackupShellTest {
    @get:Rule val compose = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var userId: String
    private var previousMode: Map<String, *>? = null
    private val store = ViewModelStore()
    private val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private lateinit var groups: SharedGroupsViewModel
    private var previousReminder: Boolean? = null

    @Before fun setUp() = runBlocking {
        val modePrefs = application.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE)
        previousMode = modePrefs.all.toMap()
        check(modePrefs.edit().clear().commit())
        userId = LedgerMode(application).useLocal().id
        previousReminder = AppSettings(application).reminder.first().enabled
        AppSettings(application).setReminderEnabled(false)
        WorkManagerTestInitHelper.initializeTestWorkManager(application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 40.0,
            category = "Food", merchant = "Keep this row", paymentMethod = "Cash"))
        viewModel = ViewModelProvider(owner, MainViewModel.factory(application, db, userId, isLocal = true))[MainViewModel::class.java]
        groups = ViewModelProvider(owner, SharedGroupsViewModel.factory(application, db, userId, isLocal = true))[SharedGroupsViewModel::class.java]
    }

    @After fun close() = runBlocking {
        try {
            val jobs = buildList {
                if (::viewModel.isInitialized) viewModel.viewModelScope.coroutineContext[Job]?.let(::add)
                if (::groups.isInitialized) groups.viewModelScope.coroutineContext[Job]?.let(::add)
            }
            compose.runOnUiThread { store.clear() }
            val closing = async(Dispatchers.Main.immediate) {
                if (::viewModel.isInitialized) viewModel.awaitSessionClosingWork()
            }
            withTimeout(10_000) {
                while (jobs.any { !it.isCompleted } || !closing.isCompleted) {
                    shadowOf(Looper.getMainLooper()).idle()
                    yield()
                }
            }
            closing.await()
            if (::db.isInitialized) db.close()
        } finally {
            previousReminder?.let { AppSettings(application).setReminderEnabled(it) }
            previousMode?.let { saved ->
                val edit = application.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE).edit().clear()
                saved.forEach { (key, value) -> if (value is String) edit.putString(key, value) }
                check(edit.commit())
            }
        }
    }

    @Test fun `settings opens full export warning and cancelling leaves real ledger untouched`() {
        compose.setContent { SplitFlowTheme {
            SplitFlowApp(viewModel, groups.controller, userId,
                AuthUiState(authState = AuthState.SignedOut), onRetryProfile = {},
                onSaveProfile = { _, _ -> }, onSignOut = {}, isLocal = true)
        } }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("home_account_button").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home_account_button").performClick()
        compose.onNodeWithTag("setting_backup").performScrollTo().performClick()
        compose.onNodeWithTag("everything_export_dialog").assertExists()
        compose.onNodeWithText("The file is not encrypted and contains your full financial history and bill photos. Keep it somewhere private and share it only with someone you trust.").assertExists()
        assertNull(shadowOf(application).nextStartedActivity)
        assertTrue(viewModel.fullBackupExportOpen.value)
        assertFalse(viewModel.fullBackupBusy.value)
        compose.onNodeWithTag("everything_export_cancel").performClick()
        compose.onNodeWithTag("everything_export_dialog").assertDoesNotExist()
        assertNull(shadowOf(application).nextStartedActivity)
        assertFalse(viewModel.fullBackupExportOpen.value)
        assertFalse(viewModel.fullBackupBusy.value)
        val rows = runBlocking { db.transactionDao().getAllTransactionsDirect() }
        assertEquals(1, rows.size)
        assertEquals("Keep this row", rows.single().merchant)
        assertEquals(40.0, rows.single().amount, 0.0)
        assertTrue(runBlocking { db.receiptDao().allFileNames().isEmpty() })
    }
}
