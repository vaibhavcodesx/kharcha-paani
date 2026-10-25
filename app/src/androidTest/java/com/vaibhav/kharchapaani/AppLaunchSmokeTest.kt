package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
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

/**
 * AppShellLaunchTest (a unit test, run on every PR) on a real device: the signed-in app as
 * MainActivity puts it together, then every tab opened once, with the phone's own WorkManager,
 * Supabase client and rendering. CI has no emulator, so this one is run by hand before a release:
 *
 *     ./gradlew :app:connectedDebugAndroidTest
 *
 * with one phone or emulator connected (docs/release-checklist.md). It uses an in-memory database,
 * so it never touches the ledger already on the phone.
 */
@RunWith(AndroidJUnit4::class)
class AppLaunchSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun signedInAppOpensOnHomeAndEveryTabOpens() {
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
