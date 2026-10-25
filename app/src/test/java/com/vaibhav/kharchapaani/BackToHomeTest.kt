package com.vaibhav.kharchapaani

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Back on Splits or Insights goes Home; it used to close the app from any tab. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackToHomeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId)
        val sharedGroups = SharedGroupsViewModel(application, db, userId)
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
        db.close()
    }

    private fun pressBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
    }

    @Test
    fun `Back on Insights goes Home instead of leaving`() {
        compose.onNodeWithTag("tab_insights").performClick()
        compose.waitForIdle()
        assertEquals(AppTab.Insights.ordinal, viewModel.currentTab.value)

        pressBack()

        assertEquals(AppTab.Activity.ordinal, viewModel.currentTab.value)
        assertFalse("the app is still open", compose.activity.isFinishing)
    }

    @Test
    fun `Back on Home leaves, as it always did`() {
        pressBack()

        assertTrue(compose.activity.isFinishing)
    }
}
