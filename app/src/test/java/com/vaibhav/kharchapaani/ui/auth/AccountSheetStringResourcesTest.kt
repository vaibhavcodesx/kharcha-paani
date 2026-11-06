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

    @Test
    fun `account settings and data string resources match exact copy`() {
        assertEquals("Settings", resources.getString(R.string.account_eyebrow_settings))
        assertEquals("UPI ID for getting paid", resources.getString(R.string.account_setting_upi_title))
        assertEquals("Add yours so friends can scan to pay you", resources.getString(R.string.account_setting_upi_empty))
        assertEquals("Set up UPI", resources.getString(R.string.account_setting_upi_click_label))
        assertEquals("Subscriptions", resources.getString(R.string.account_setting_subscriptions_title))
        assertEquals("Charges that repeat, added when they're due", resources.getString(R.string.account_setting_subscriptions_detail))
        assertEquals("Categories & payment methods", resources.getString(R.string.account_setting_categories_title))
        assertEquals("Hide defaults, add your own", resources.getString(R.string.account_setting_categories_detail))
        assertEquals("Ask before discarding an entry", resources.getString(R.string.account_setting_discard_prompt_title))
        assertEquals("When you close a half-filled expense", resources.getString(R.string.account_setting_discard_prompt_detail))
        assertEquals("Notifications", resources.getString(R.string.account_setting_notifications_title))
        assertEquals("Off for Kharcha Paani", resources.getString(R.string.account_setting_notifications_off))
        assertEquals("Payments to confirm, and a reminder at 9:00 PM", resources.getString(R.string.account_setting_notifications_reminder_and_payments, "9:00 PM"))
        assertEquals("Payments to confirm", resources.getString(R.string.account_setting_notifications_payments_only))
        assertEquals("Your data", resources.getString(R.string.account_eyebrow_data))
        assertEquals("Export to a spreadsheet", resources.getString(R.string.account_setting_export_title))
        assertEquals("A month, dates or everything, as CSV", resources.getString(R.string.account_setting_export_detail))
        assertEquals("Export everything", resources.getString(R.string.full_backup_ui_export_title))
        assertEquals("Restore from a backup", resources.getString(R.string.full_backup_ui_restore_title))
    }

    @Test
    fun `account help and reminder string resources match exact copy`() {
        assertEquals("Help & about", resources.getString(R.string.account_eyebrow_help))
        assertEquals("Send feedback", resources.getString(R.string.account_help_feedback_title))
        assertEquals("Privacy policy", resources.getString(R.string.account_help_privacy_title))
        assertEquals("Terms of use", resources.getString(R.string.account_help_terms_title))
        assertEquals("Send crash reports", resources.getString(R.string.account_help_crash_title))
        assertEquals("Open-source licences", resources.getString(R.string.account_help_licences_title))
        assertEquals("Kharcha Paani 1.6.0 (7)", resources.getString(R.string.account_help_version, "1.6.0", 7))
        assertEquals("Colour", resources.getString(R.string.account_colour_label))
        assertEquals("Choosing one", resources.getString(R.string.account_colour_choosing))
        assertEquals("Yours everywhere", resources.getString(R.string.account_colour_yours))
        assertEquals("You wear this colour everywhere: on your phone and on every groupmate's.", resources.getString(R.string.account_colour_wear_description))
        assertEquals("Save colour", resources.getString(R.string.account_colour_save))
        assertEquals("Your colour is close to Rohan's. Pick another so you're easy to tell apart.", resources.getString(R.string.account_colour_clash, "Rohan"))
        assertEquals("Daily reminder", resources.getString(R.string.account_setting_daily_reminder_title))
        assertEquals("Off", resources.getString(R.string.account_setting_daily_reminder_off))
        assertEquals("Notifications are off for Kharcha Paani, so it can't appear", resources.getString(R.string.account_setting_daily_reminder_blocked))
        assertEquals("At 9:00 PM if nothing's logged that day", resources.getString(R.string.account_setting_daily_reminder_scheduled, "9:00 PM"))
        assertEquals("Allow", resources.getString(R.string.account_setting_daily_reminder_allow))
        assertEquals("Reminder time", resources.getString(R.string.account_setting_reminder_time_title))
    }
}
