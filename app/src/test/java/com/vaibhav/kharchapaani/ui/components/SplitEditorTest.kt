package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SplitEditorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `typing a decimal point in an exact share keeps it`() {
        var bobsShare = 0.0
        composeTestRule.setContent {
            // The same round trip the ViewModel does: the field's text is parsed into the share, and the
            // share is what gets passed back down.
            var shares by remember { mutableStateOf(listOf(ShareInput(-1, "You"), ShareInput(2, "Bob"))) }
            SplitFlowTheme {
                SplitEditor(
                    amount = "100", splitMode = SplitMode.EXACT, paidByPersonId = -1L, shares = shares,
                    onSplitModeChange = {}, onPaidByPersonIdChange = {}, onTogglePersonShare = {},
                    onExactAmountChange = { id, amount ->
                        shares = shares.map { if (it.personId == id) it.copy(amount = amount) else it }
                        if (id == 2L) bobsShare = amount
                    },
                    onPercentageChange = { _, _ -> }, onOpenAddFriend = {},
                )
            }
        }

        val field = composeTestRule.onNodeWithContentDescription("Bob's amount")
        field.performTextInput("12.")
        field.performTextInput("5")

        field.assert(hasText("12.5"))
        assertEquals(12.5, bobsShare, 0.0)
    }

    @Test
    fun `search filters payer chips and split rows and clear restores them`() {
        composeTestRule.setContent {
            val shares = listOf(
                ShareInput(-1, "You", isSelected = true),
                ShareInput(2, "Rohan", isSelected = false),
                ShareInput(3, "Amit", isSelected = false),
                ShareInput(4, "Zara", isSelected = true),
            )
            SplitFlowTheme {
                SplitEditor(
                    amount = "100", splitMode = SplitMode.EQUAL, paidByPersonId = -1L, shares = shares,
                    onSplitModeChange = {}, onPaidByPersonIdChange = {}, onTogglePersonShare = {},
                    onExactAmountChange = { _, _ -> }, onPercentageChange = { _, _ -> }, onOpenAddFriend = {},
                )
            }
        }

        // Initially all candidates exist
        composeTestRule.onNodeWithText("Rohan").assertExists()
        composeTestRule.onNodeWithText("Amit").assertExists()
        composeTestRule.onNodeWithText("Zara").assertExists()

        // Filter by "Roh"
        composeTestRule.onNodeWithTag("split_search_input").performTextInput("Roh")

        composeTestRule.onNodeWithText("Rohan").assertExists()
        // Amit is not selected and doesn't match query, so hidden
        composeTestRule.onNodeWithText("Amit").assertDoesNotExist()
        // Zara is selected, so stays visible in split rows
        composeTestRule.onNodeWithText("Zara").assertExists()

        // Clear search
        composeTestRule.onNodeWithTag("split_search_clear").performClick()

        // Amit reappears
        composeTestRule.onNodeWithText("Amit").assertExists()
    }
}
