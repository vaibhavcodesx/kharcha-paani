package com.vaibhav.kharchapaani.ui.components

import androidx.compose.material3.Text
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class KpModalSheetDiscardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var dismissals = 0

    private fun open(unsaved: Boolean) {
        composeTestRule.setContent {
            SplitFlowTheme {
                KpModalSheet(
                    onDismiss = { dismissals++ },
                    discardGuard = DiscardGuard(
                        title = "Discard this expense?",
                        message = "What you've entered won't be saved.",
                        hasUnsavedInput = { unsaved },
                    ),
                ) {
                    Text("Sheet body")
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    // The handle's dismiss action takes the same path as a tap on the scrim.
    private fun dismissFromHandle() {
        composeTestRule.onNodeWithContentDescription("Drag handle").performSemanticsAction(SemanticsActions.Dismiss)
        composeTestRule.waitForIdle()
    }

    @Test
    fun `a sheet with nothing to lose closes without asking`() {
        open(unsaved = false)

        dismissFromHandle()

        assertEquals(1, dismissals)
        composeTestRule.onNodeWithText("Discard this expense?").assertDoesNotExist()
    }

    @Test
    fun `a sheet with unsaved input asks first and stays open`() {
        open(unsaved = true)

        dismissFromHandle()

        assertEquals(0, dismissals)
        composeTestRule.onNodeWithText("Discard this expense?").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sheet body").assertIsDisplayed()
    }

    @Test
    fun `keep editing leaves the sheet as it was`() {
        open(unsaved = true)
        dismissFromHandle()

        composeTestRule.onNodeWithText("Keep editing").performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, dismissals)
        composeTestRule.onNodeWithText("Discard this expense?").assertDoesNotExist()
        composeTestRule.onNodeWithText("Sheet body").assertIsDisplayed()
    }

    @Test
    fun `discard closes the sheet`() {
        open(unsaved = true)
        dismissFromHandle()

        composeTestRule.onNodeWithText("Discard").performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, dismissals)
    }
}
