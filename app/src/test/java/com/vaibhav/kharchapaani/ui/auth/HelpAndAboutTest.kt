package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Help & about (plan T1.2) and the sign-in screen's agreement line. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HelpAndAboutTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `help and about lists the pages, the version, and opens the licences`() {
        var licencesOpened = false
        compose.setContent {
            SplitFlowTheme {
                AccountSheetContent(
                    state = AuthUiState(authState = AuthState.SignedIn(userId = "u", email = "a@b.c", isRefreshPending = false)),
                    syncStatus = SyncStatus(),
                    askBeforeDiscarding = true,
                    onSaveProfile = { _, _ -> },
                    onAskBeforeDiscardingChange = {},
                    onOpenExport = {},
                    onRetryFailedSync = {},
                    onRetryProfile = {},
                    onSignOut = {},
                    onOpenLicences = { licencesOpened = true },
                )
            }
        }

        compose.onNodeWithTag("help_feedback").performScrollTo().assertExistsWithText("Send feedback")
        compose.onNodeWithTag("help_privacy").performScrollTo()
        compose.onNodeWithTag("help_terms").performScrollTo()
        compose.onNodeWithText("Kharcha Paani ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})").performScrollTo()
        compose.onNodeWithTag("help_licences").performScrollTo().performClick()

        assertTrue(licencesOpened)
    }

    @Test
    fun `sign-in says what continuing agrees to, and asks for 18 or older`() {
        compose.setContent {
            SplitFlowTheme { SignInScreen(isSigningIn = false, failure = null, onSignInWithGoogle = {}, onDismissFailure = {}) }
        }

        compose.onNodeWithText("By continuing you agree to the Terms and the Privacy policy, and confirm you're 18 or older.")
            .performScrollTo()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsWithText(text: String) {
        assertExists()
        compose.onNodeWithText(text).assertExists()
    }
}
