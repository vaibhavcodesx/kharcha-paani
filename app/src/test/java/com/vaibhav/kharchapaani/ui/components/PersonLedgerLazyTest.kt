package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PersonLedgerLazyTest {

    @get:Rule val compose = createComposeRule()

    private val testPerson = PersonEntity(
        id = 2L,
        name = "Rohan",
        avatarColor = "#70C1FF"
    )

    @Test
    fun `empty ledger renders notice inside lazy column`() {
        compose.setContent {
            SplitFlowTheme {
                PersonLedgerContent(
                    person = testPerson,
                    ledgerItems = emptyList(),
                    onSettleUp = {},
                )
            }
        }
        compose.onNodeWithText("Rohan").assertExists()
        compose.onNodeWithText("History · 0").assertExists()
        compose.onNodeWithText("No splits or settlements with Rohan yet.").assertExists()
    }

    @Test
    fun `long ledger list renders with lazy column and scrolls to bottom`() {
        var settled = false
        val items = (1..60).map { i ->
            PersonLedgerItem(
                id = "item_$i",
                date = 1700000000000L + (i * 86400000L),
                title = "Dinner $i",
                subtitle = "Paid by you, split equally",
                amount = 100.0 + i,
                netChange = 50.0 + i,
                isSettlement = false,
            )
        }

        compose.setContent {
            SplitFlowTheme {
                PersonLedgerContent(
                    person = testPerson,
                    ledgerItems = items,
                    onSettleUp = { settled = true },
                )
            }
        }

        // Top item exists
        compose.onNodeWithText("Dinner 1").assertExists()
        compose.onNodeWithText("History · 60").assertExists()

        // Scroll to the 60th item using LazyList scrollToKey
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("item_60")
        compose.onNodeWithText("Dinner 60").assertExists()

        // Settle up action bar button remains clickable
        compose.onNodeWithTag("ledger_settle_up_button").performClick()
        assertTrue(settled)
    }
}
