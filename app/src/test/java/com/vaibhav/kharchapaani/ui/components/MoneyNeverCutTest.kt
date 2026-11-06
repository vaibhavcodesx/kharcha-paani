package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI audit batch A (docs/production-readiness/audits/ui-audit-2026-11.md): money is never cut or misread. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class MoneyNeverCutTest {

    @get:Rule
    val compose = createComposeRule()

    private val rohan = PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF")

    @Test
    fun `a payment waiting for an answer shows what was paid, not ₹0`() {
        // U2: it doesn't move the balance until it's confirmed, so its change is nothing, and the
        // row read "They paid you ₹0".
        val waiting = PersonLedgerItem(
            id = "settlement_1", date = 0L, title = "They paid you", subtitle = "Settled up · waiting to be confirmed",
            amount = 300.0, netChange = 0.0, isSettlement = true,
        )
        val dinner = PersonLedgerItem(
            id = "split_1", date = 0L, title = "Dinner", subtitle = "You paid ₹2,400 · their share ₹800",
            amount = 800.0, netChange = 800.0, isSettlement = false,
        )
        compose.setContent { SplitFlowTheme { PersonLedgerContent(person = rohan, ledgerItems = listOf(waiting, dinner), onSettleUp = {}) } }

        compose.onNodeWithText("₹300").assertExists()
        assertEquals(0, compose.onAllNodesWithText("₹0").fetchSemanticsNodes().size)
        compose.onNodeWithText("+₹800").assertExists()
    }

    @Test
    fun `a share that rounds to nothing but isn't nothing reads under one percent`() {
        // U7: ₹580 of a month with a ₹1.25 lakh laptop read "0%".
        assertEquals("<1", shownPercent(0.45f))
        assertEquals("0", shownPercent(0f))
        assertEquals("1", shownPercent(0.5f))
        assertEquals("97", shownPercent(96.8f))
    }
}
