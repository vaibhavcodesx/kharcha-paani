package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The signed-in app as MainActivity puts it together: both view models, built the way their
 * factories build them, and [SplitFlowApp] composed around them, then every tab opened once.
 *
 * MainViewModelConstructionTest covers building the view model. This covers the step after, the
 * first frames: a crash in a screen's first composition, or in a flow it collects, takes the app
 * down on launch just the same, and no screen test sees it because each one gets hand-made state.
 * Runs on every PR; `app/src/androidTest/.../AppLaunchSmokeTest` is the same check on a real
 * device, run by hand before a release (docs/release-checklist.md).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppShellLaunchTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a signed-in account's app opens on Home, and every tab opens`() {
        val viewModel = MainViewModel(application, db, userId)
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

        compose.onNodeWithTag("dock_add").assertExists()
        for (tab in listOf("tab_splits", "tab_insights", "tab_activity")) {
            compose.onNodeWithTag(tab).performClick()
            compose.waitForIdle()
        }
        compose.onNodeWithTag("dock_add").assertExists()
    }
}
