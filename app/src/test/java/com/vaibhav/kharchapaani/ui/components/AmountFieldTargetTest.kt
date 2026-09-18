package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backlog item 11. The verification pass found the big "₹0" *is* the input, not a label above a
 * hidden keypad — but it was sized to its own content, so on an empty sheet the entire touch target
 * was the width of a single "0", on the most-used flow in the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AmountFieldTargetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var typed = ""

    private fun show(initial: String = "") {
        typed = initial
        composeTestRule.setContent {
            SplitFlowTheme {
                var amount by remember { mutableStateOf(initial) }
                Column {
                    BigAmountField(
                        amount = amount,
                        onAmountChange = {
                            amount = it
                            typed = it
                        },
                        accent = WarmAmberPrimary,
                        testTag = "amount",
                    )
                }
            }
        }
    }

    @Test
    fun `an empty amount is still a full-width target, not the width of one zero`() {
        show()

        composeTestRule.onNodeWithTag("amount_field_band")
            .assertHasClickAction()
            // The screen is 411dp wide. Anything near 20dp is the old bug.
            .assertWidthIsAtLeast(300.dp)
    }

    @Test
    fun `the band is tappable whether or not there is already an amount`() {
        show(initial = "370")

        composeTestRule.onNodeWithTag("amount_field_band").assertHasClickAction()
    }

    @Test
    fun `the field still takes what is typed`() {
        show()

        composeTestRule.onNodeWithTag("amount_field_band").performClick()
        composeTestRule.onNodeWithTag("amount").performTextInput("1250")

        assertEquals("1250", typed)
    }
}
