package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
class AccountSheetContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val signedIn = AuthState.SignedIn(userId = "u1", email = "priya@example.com", isRefreshPending = false)
    private var saved: Pair<String, String?>? = null
    private var askChanges = mutableListOf<Boolean>()
    private var exportOpened = false

    private fun show(profile: ProfileState = ProfileState.Loaded(Profile(id = "u1", displayName = "Priya"))) {
        composeTestRule.setContent {
            SplitFlowTheme {
                AccountSheetContent(
                    state = AuthUiState(authState = signedIn, profile = profile),
                    syncStatus = SyncStatus(),
                    askBeforeDiscarding = true,
                    onSaveProfile = { name, emoji -> saved = name to emoji },
                    onAskBeforeDiscardingChange = { askChanges += it },
                    onOpenExport = { exportOpened = true },
                    onRetryFailedSync = {},
                    onRetryProfile = {},
                    onSignOut = {},
                )
            }
        }
    }

    @Test
    fun `nothing changed, nothing to save`() {
        show()

        composeTestRule.onNodeWithTag("profile_save_button").assertDoesNotExist()
    }

    @Test
    fun `a new name and emoji are saved together`() {
        show()

        composeTestRule.onNodeWithTag("profile_name_input").performTextReplacement("Priya Sharma")
        composeTestRule.onNodeWithTag("avatar_row").performClick()
        composeTestRule.onNodeWithTag("avatar_🦊").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("profile_save_button").performClick()

        assertEquals("Priya Sharma" to "🦊", saved)
    }

    @Test
    fun `going back to initials clears the emoji`() {
        show(ProfileState.Loaded(Profile(id = "u1", displayName = "Priya", avatarEmoji = "🐼")))

        composeTestRule.onNodeWithTag("avatar_row").performClick()
        composeTestRule.onNodeWithTag("avatar_initials").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("profile_save_button").performClick()

        assertEquals("Priya" to null, saved)
    }

    @Test
    fun `a blank name can't be saved`() {
        show()

        composeTestRule.onNodeWithTag("profile_name_input").performTextReplacement("   ")

        composeTestRule.onNodeWithTag("profile_save_button").assertIsNotEnabled()
    }

    @Test
    fun `without the cloud profile the name can't be edited`() {
        show(ProfileState.Unavailable)

        composeTestRule.onNodeWithTag("profile_name_input").assertDoesNotExist()
        composeTestRule.onNodeWithText("Your name and avatar can be changed once your cloud profile is reachable.").assertExists()
    }

    @Test
    fun `settings switch the discard question and open export`() {
        show()

        composeTestRule.onNodeWithTag("setting_ask_before_discarding").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("setting_export").performScrollTo().performClick()

        assertEquals(listOf(false), askChanges)
        assertEquals(true, exportOpened)
    }
}
