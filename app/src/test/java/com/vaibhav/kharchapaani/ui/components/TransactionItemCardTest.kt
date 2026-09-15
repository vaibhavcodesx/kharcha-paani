package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransactionItemCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tx = FullTransaction(
        TransactionEntity(id = 9, type = "expense", amount = 34.5, category = "Food", merchant = "Idli", paymentMethod = "UPI", date = 0L),
    )

    @Test
    fun `amounts keep their paisa`() {
        composeTestRule.setContent { SplitFlowTheme { TransactionItemCard(tx, onEdit = {}, onDelete = {}) } }

        composeTestRule.onNodeWithText("₹34.50", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a read-only row can't be swiped away or opened from its menu`() {
        var deletes = 0
        composeTestRule.setContent {
            SplitFlowTheme { TransactionItemCard(tx, onEdit = {}, onDelete = { deletes++ }, isReadOnly = true) }
        }

        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertEquals(0, deletes)
        composeTestRule.onNodeWithTag("transaction_item_9").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Options").assertDoesNotExist()
    }

    @Test
    fun `swiping left deletes exactly once`() {
        var deletes = 0
        composeTestRule.setContent {
            SplitFlowTheme { TransactionItemCard(tx, onEdit = {}, onDelete = { deletes++ }) }
        }

        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertEquals(1, deletes)
    }

    @Test
    fun `a short flick while scrolling doesn't delete`() {
        var deletes = 0
        composeTestRule.setContent {
            SplitFlowTheme { TransactionItemCard(tx, onEdit = {}, onDelete = { deletes++ }) }
        }

        // A quarter of the row, fast: the kind of sideways twitch a vertical scroll picks up.
        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput {
            swipe(start = center, end = center.copy(x = center.x - width * 0.25f), durationMillis = 60)
        }
        composeTestRule.waitForIdle()

        assertEquals(0, deletes)
        composeTestRule.onNodeWithTag("transaction_item_9").assertIsDisplayed()
    }

    @Test
    fun `a row brought back by Undo is in place and isn't deleted again`() {
        var deletes = 0
        var shown by mutableStateOf(true)
        composeTestRule.setContent {
            SplitFlowTheme {
                LazyColumn {
                    if (shown) {
                        item(key = "tx_9") { TransactionItemCard(tx, onEdit = {}, onDelete = { deletes++; shown = false }) }
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { shown = true }
        composeTestRule.waitForIdle()

        assertEquals(1, deletes)
        composeTestRule.onNodeWithTag("transaction_item_9").assertIsDisplayed()
        composeTestRule.onNodeWithText("Idli").assertIsDisplayed()
    }

    @Test
    fun `swiping right edits exactly once and the row comes back`() {
        var edits = 0
        var deletes = 0
        composeTestRule.setContent {
            SplitFlowTheme { TransactionItemCard(tx, onEdit = { edits++ }, onDelete = { deletes++ }) }
        }

        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertEquals(1, edits)
        assertEquals(0, deletes)
        composeTestRule.onNodeWithTag("transaction_item_9").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `TalkBack gets edit and delete on an editable row`() {
        var deletes = 0
        composeTestRule.setContent {
            SplitFlowTheme { TransactionItemCard(tx, onEdit = {}, onDelete = { deletes++ }) }
        }

        composeTestRule.onNodeWithTag("transaction_item_9").performCustomAccessibilityActionWithLabel("Delete")

        assertEquals(1, deletes)
    }

    @Test
    fun `a read-only row offers TalkBack no actions`() {
        composeTestRule.setContent {
            SplitFlowTheme { TransactionItemCard(tx, onEdit = {}, onDelete = {}, isReadOnly = true) }
        }

        composeTestRule.onNodeWithTag("transaction_item_9").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    }
}
