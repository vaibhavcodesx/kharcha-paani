package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-01: "Delete this phone's ledger" in the guest Settings asks first, and only the second tap deletes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DeleteLocalLedgerDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var deleted = 0

    @Test
    fun `one tap only asks, Cancel keeps the ledger, Delete deletes`() {
        composeTestRule.setContent {
            SplitFlowTheme {
                AccountSheetContent(
                    state = AuthUiState(authState = AuthState.SignedOut),
                    syncStatus = SyncStatus(),
                    lastSyncError = null,
                    askBeforeDiscarding = true,
                    onSaveProfile = { _, _ -> },
                    onAskBeforeDiscardingChange = {},
                    onOpenExport = {},
                    onRetryFailedSync = {},
                    onRetryProfile = {},
                    onSignOut = {},
                    isLocal = true,
                    onDeleteLocalLedger = { deleted++ },
                )
            }
        }
        composeTestRule.onNodeWithTag("account_delete_local").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("delete_local_dialog").assertIsDisplayed()
        assertEquals("nothing deleted yet", 0, deleted)

        composeTestRule.onNodeWithTag("delete_local_cancel").performClick()
        composeTestRule.onNodeWithTag("delete_local_dialog").assertDoesNotExist()
        assertEquals(0, deleted)

        composeTestRule.onNodeWithTag("account_delete_local").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("delete_local_confirm").performClick()
        assertEquals(1, deleted)
    }
}
