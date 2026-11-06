package com.vaibhav.kharchapaani.ui.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.Profile
import com.vaibhav.kharchapaani.ui.auth.AccountSheetContent
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.auth.ProfileState
import com.vaibhav.kharchapaani.ui.auth.SignInScreen
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class GuestUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `sign-in screen offers local ledger without starting the Google picker`() {
        var local = 0
        var google = 0
        compose.setContent {
            SplitFlowTheme {
                SignInScreen(isSigningIn = false, failure = null,
                    onSignInWithGoogle = { google++ }, onDismissFailure = {}, onUseLocal = { local++ })
            }
        }
        compose.onNodeWithTag("sign_in_use_local").performScrollTo().performClick()
        assertEquals(1, local)
        assertEquals(0, google)
    }

    @Test fun `local settings hide cloud controls retain backup and explain sign-in requirement for AI`() {
        var signIn = 0
        var savedProfile = 0
        compose.setContent {
            SplitFlowTheme {
                AccountSheetContent(
                    // Even a stale cloud profile must not reveal cloud controls in local mode.
                    state = AuthUiState(authState = AuthState.SignedIn("cloud", "cloud@example.com", false),
                        profile = ProfileState.Loaded(Profile(id = "cloud", displayName = "Cloud name"))),
                    syncStatus = SyncStatus(unsyncedChanges = 3), askBeforeDiscarding = true,
                    onSaveProfile = { _, _ -> savedProfile++ }, onAskBeforeDiscardingChange = {},
                    onOpenExport = {}, onRetryFailedSync = {}, onRetryProfile = {}, onSignOut = {},
                    isLocal = true, onSignInFromLocal = { signIn++ }, aiEnabled = true,
                )
            }
        }
        compose.onNodeWithTag("profile_name_input").assertDoesNotExist()
        compose.onNodeWithTag("account_sync_status").assertDoesNotExist()
        compose.onNodeWithTag("account_delete").assertDoesNotExist()
        compose.onNodeWithTag("account_sign_out").assertDoesNotExist()
        compose.onNodeWithText("cloud@example.com").assertDoesNotExist()
        compose.onNodeWithTag("account_local_explanation").assertExists()
        compose.onNodeWithTag("account_local_sign_in").performScrollTo().performClick()
        assertEquals(1, signIn)
        assertEquals(0, savedProfile)
        compose.onNodeWithTag("setting_ai_enabled").performScrollTo()
        compose.onNodeWithText("AI needs you signed in. This keeps your choice for when you are.").assertExists()
        compose.onNodeWithTag("setting_backup").performScrollTo().assertExists()
        compose.onNodeWithTag("setting_restore").performScrollTo().assertExists()
    }

    @Test fun `cloud settings offer a separate local ledger without calling sign-out`() {
        var local = 0
        var signOut = 0
        compose.setContent {
            SplitFlowTheme {
                AccountSheetContent(state = AuthUiState(), syncStatus = SyncStatus(), askBeforeDiscarding = true,
                    onSaveProfile = { _, _ -> }, onAskBeforeDiscardingChange = {}, onOpenExport = {},
                    onRetryFailedSync = {}, onRetryProfile = {}, onSignOut = { signOut++ },
                    onUseLocal = { local++ })
            }
        }
        compose.onNodeWithTag("account_use_local").performScrollTo().performClick()
        assertEquals(1, local)
        assertEquals(0, signOut)
        compose.onNodeWithTag("account_local_sign_in").assertDoesNotExist()
    }
}
