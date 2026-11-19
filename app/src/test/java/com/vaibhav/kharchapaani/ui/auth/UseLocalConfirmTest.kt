package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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

/** UX-31: "Use a local ledger instead" asks before it switches ledgers; one tap no longer does it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UseLocalConfirmTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var switched = 0

    private fun show() {
        composeTestRule.setContent {
            SplitFlowTheme {
                AccountSheetContent(
                    state = AuthUiState(
                        authState = AuthState.SignedIn(userId = "u1", email = "priya@example.com", isRefreshPending = false),
                        profile = ProfileState.Loaded(Profile(id = "u1", displayName = "Priya")),
                    ),
                    syncStatus = SyncStatus(),
                    lastSyncError = null,
                    askBeforeDiscarding = true,
                    onSaveProfile = { _, _ -> },
                    onAskBeforeDiscardingChange = {},
                    onOpenExport = {},
                    onRetryFailedSync = {},
                    onRetryProfile = {},
                    onSignOut = {},
                    onUseLocal = { switched++ },
                )
            }
        }
    }

    @Test
    fun `one tap only asks, Cancel keeps the account ledger, Switch switches`() {
        show()
        composeTestRule.onNodeWithTag("account_use_local").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("use_local_dialog").assertIsDisplayed()
        assertEquals("nothing switched yet", 0, switched)

        composeTestRule.onNodeWithTag("use_local_cancel").performClick()
        composeTestRule.onNodeWithTag("use_local_dialog").assertDoesNotExist()
        assertEquals(0, switched)

        composeTestRule.onNodeWithTag("account_use_local").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("use_local_confirm").performClick()
        assertEquals(1, switched)
    }
}
