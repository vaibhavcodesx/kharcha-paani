package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The redesigned sign-in screen on a tall phone: the preview appears and stays decorative. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SignInScreenTallLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a tall screen shows the app preview, hidden from screen readers, and the action still works`() {
        var signInRequests = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                SignInScreen(isSigningIn = false, failure = null, onSignInWithGoogle = { signInRequests++ }, onDismissFailure = {})
            }
        }

        composeTestRule.onNodeWithTag("sign_in_preview").assertExists()
        // Decorative: the sentence under the title already says it, so TalkBack doesn't read the
        // made-up rows out as if they were someone's ledger.
        composeTestRule.onNodeWithText("Friday dinner").assertDoesNotExist()
        composeTestRule.onNode(hasText("Kharcha Paani") and isHeading()).assertExists()

        composeTestRule.onNodeWithTag("sign_in_google_button").assertIsEnabled().performClick()
        assertEquals(1, signInRequests)
    }
}

/** On a short screen the preview is left out so the action stays within reach. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp")
class SignInScreenShortLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a short screen leaves the preview out`() {
        composeTestRule.setContent {
            SplitFlowTheme {
                SignInScreen(isSigningIn = false, failure = null, onSignInWithGoogle = {}, onDismissFailure = {})
            }
        }

        composeTestRule.onNodeWithTag("sign_in_preview").assertDoesNotExist()
        composeTestRule.onNodeWithText("Continue with Google").assertExists()
    }
}

class ReducedMotionTest {

    @Test
    fun `only a zero animator scale counts as animations off`() {
        assertTrue(isReducedMotion(0f))
        assertFalse(isReducedMotion(1f))
        assertFalse(isReducedMotion(0.5f))
    }
}
