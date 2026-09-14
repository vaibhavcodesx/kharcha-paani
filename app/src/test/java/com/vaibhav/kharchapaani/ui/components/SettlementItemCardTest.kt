package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettlementItemCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun item(from: Long, to: Long, isIou: Boolean = false) = ActivityItem.Settlement(
        settlement = SettlementEntity(id = 5, fromPersonId = from, toPersonId = to, amount = 250.0, isIou = isIou, groupId = 7),
        fromName = if (from == -1L) "You" else "Bob",
        toName = if (to == -1L) "You" else if (to == 3L) "Carol" else "Bob",
        groupName = "Goa",
    )

    @Test
    fun `a settlement I received shows who paid, the group, and can be retracted`() {
        var retracted = 0
        composeTestRule.setContent {
            SplitFlowTheme { SettlementItemCard(item(from = 2, to = -1), onRetract = { retracted++ }) }
        }

        composeTestRule.onNodeWithText("Bob paid you").assertIsDisplayed()
        composeTestRule.onNodeWithText("+₹250").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("More options for Bob paid you").performClick()
        composeTestRule.onNodeWithText("Retract settlement").performClick()
        assertEquals(1, retracted)
    }

    @Test
    fun `an IOU I gave reads as lending`() {
        composeTestRule.setContent {
            SplitFlowTheme { SettlementItemCard(item(from = -1, to = 2, isIou = true), onRetract = {}) }
        }

        composeTestRule.onNodeWithText("You lent Bob").assertIsDisplayed()
        composeTestRule.onNodeWithText("-₹250").assertIsDisplayed()
    }

    @Test
    fun `a settlement between two groupmates has no retract option`() {
        composeTestRule.setContent {
            SplitFlowTheme { SettlementItemCard(item(from = 2, to = 3), onRetract = {}) }
        }

        composeTestRule.onNodeWithText("Bob paid Carol").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("More options for Bob paid Carol").assertDoesNotExist()
    }
}
