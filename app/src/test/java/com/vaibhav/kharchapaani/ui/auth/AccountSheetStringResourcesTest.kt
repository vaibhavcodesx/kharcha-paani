package com.vaibhav.kharchapaani.ui.auth

import android.app.Application
import android.content.res.Resources
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.Profile
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AccountSheetStringResourcesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val resources: Resources = application.resources

    @Test
    fun `account profile and avatar string resources match exact copy`() {
        assertEquals("Your avatar", resources.getString(R.string.account_avatar_content_description))
        assertEquals("Your account", resources.getString(R.string.account_title_fallback))
        assertEquals("Profile", resources.getString(R.string.account_eyebrow_profile))
        assertEquals("Your name and avatar can be changed once your cloud profile is reachable.", resources.getString(R.string.account_profile_unreachable))
        assertEquals("Name", resources.getString(R.string.account_name_label))
        assertEquals("Your name", resources.getString(R.string.account_name_placeholder))
        assertEquals("Avatar", resources.getString(R.string.account_avatar_label))
        assertEquals("Initials", resources.getString(R.string.account_avatar_initials))
        assertEquals("Couldn't save your profile. Check your connection and try again.", resources.getString(R.string.account_profile_save_failed))
        assertEquals("Keep it to 30 characters.", resources.getString(R.string.account_name_too_long, 30))
        assertEquals("Groupmates see your name and avatar.", resources.getString(R.string.account_name_helper))
        assertEquals("Account", resources.getString(R.string.account_eyebrow_account))
    }

    @Test
    fun `account cloud status string resources match exact copy`() {
        assertEquals("Offline. Your account reconnects on its own.", resources.getString(R.string.account_cloud_offline))
        assertEquals("Can't reach your cloud account right now", resources.getString(R.string.account_cloud_unavailable))
        assertEquals("Cloud profile ready", resources.getString(R.string.account_cloud_ready))
        assertEquals("Cloud profile not found", resources.getString(R.string.account_cloud_missing))
        assertEquals("Checking your cloud profile…", resources.getString(R.string.account_cloud_checking))
        assertEquals("Retry", resources.getString(R.string.account_retry))
    }

    @Test
    fun `account sync status string resources and plurals match exact copy`() {
        assertEquals("1 change couldn't sync", resources.getQuantityString(R.plurals.account_sync_failed, 1, 1))
        assertEquals("3 changes couldn't sync", resources.getQuantityString(R.plurals.account_sync_failed, 3, 3))
        assertEquals("Syncing…", resources.getString(R.string.account_sync_syncing))
        assertEquals("1 change waiting to sync", resources.getQuantityString(R.plurals.account_sync_unsynced, 1, 1))
        assertEquals("4 changes waiting to sync", resources.getQuantityString(R.plurals.account_sync_unsynced, 4, 4))
        assertEquals("All changes synced", resources.getString(R.string.account_sync_synced_just_now))
        assertEquals("All changes synced · 2 min. ago", resources.getString(R.string.account_sync_synced_relative, "2 min. ago"))
        assertEquals("Not synced yet", resources.getString(R.string.account_sync_not_synced))
    }

    @Test
    fun `account sheet content renders extracted string resources on screen`() {
        composeTestRule.setContent {
            SplitFlowTheme {
                AccountSheetContent(
                    state = AuthUiState(
                        authState = AuthState.SignedIn(userId = "u1", email = "test@example.com", isRefreshPending = false),
                        profile = ProfileState.Loaded(Profile(id = "u1", displayName = "Test User", avatarEmoji = null)),
                    ),
                    syncStatus = SyncStatus(lastSyncedAt = 10_000_000L),
                    askBeforeDiscarding = true,
                    onSaveProfile = { _, _ -> },
                    onAskBeforeDiscardingChange = {},
                    onOpenExport = {},
                    onRetryFailedSync = {},
                    onRetryProfile = {},
                    onSignOut = {},
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Your avatar").assertExists()
        composeTestRule.onNodeWithText("PROFILE").assertExists()
        composeTestRule.onNodeWithText("Name").assertExists()
        composeTestRule.onNodeWithText("Avatar").assertExists()
        composeTestRule.onNodeWithText("Initials").assertExists()
        composeTestRule.onNodeWithText("Groupmates see your name and avatar.").assertExists()
        composeTestRule.onNodeWithText("ACCOUNT").assertExists()
        composeTestRule.onNodeWithText("Cloud profile ready").assertExists()
    }
}
