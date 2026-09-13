package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.auth.GoogleSignInFailure
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignInScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping continue with Google starts sign-in`() {
        var signInRequests = 0
        setScreen(onSignInWithGoogle = { signInRequests++ })

        composeTestRule.onNodeWithText("Continue with Google").performClick()

        assertEquals(1, signInRequests)
    }

    @Test
    fun `button is disabled while a sign-in is in progress`() {
        setScreen(isSigningIn = true)

        composeTestRule.onNode(hasText("Signing in…") and hasClickAction(), useUnmergedTree = false)
            .assertIsNotEnabled()
    }

    @Test
    fun `offline failure explains what to do and can be dismissed`() {
        var dismissed = false
        setScreen(failure = GoogleSignInFailure.Network, onDismissFailure = { dismissed = true })

        composeTestRule.onNodeWithText("You're offline. Connect once to sign in.").assertExists()
        composeTestRule.onNodeWithContentDescription("Dismiss").performClick()

        assertEquals(true, dismissed)
    }

    @Test
    fun `no failure notice without a failure`() {
        setScreen()

        composeTestRule.onNodeWithContentDescription("Dismiss").assertDoesNotExist()
    }

    private fun setScreen(
        isSigningIn: Boolean = false,
        failure: GoogleSignInFailure? = null,
        onSignInWithGoogle: () -> Unit = {},
        onDismissFailure: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            SplitFlowTheme {
                SignInScreen(
                    isSigningIn = isSigningIn,
                    failure = failure,
                    onSignInWithGoogle = onSignInWithGoogle,
                    onDismissFailure = onDismissFailure,
                )
            }
        }
    }
}
