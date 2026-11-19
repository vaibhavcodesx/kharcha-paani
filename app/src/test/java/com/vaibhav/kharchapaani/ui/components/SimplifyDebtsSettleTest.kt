package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-10: a "who pays whom" row that involves you has a Settle button; other people's rows have none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SimplifyDebtsSettleTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val debts = listOf(
        SimplifiedDebt(fromPersonId = -1L, fromPersonName = "You", toPersonId = 2L, toPersonName = "Asha", amount = 300.0),
        SimplifiedDebt(fromPersonId = 3L, fromPersonName = "Bilal", toPersonId = -1L, toPersonName = "You", amount = 120.0),
        SimplifiedDebt(fromPersonId = 3L, fromPersonName = "Bilal", toPersonId = 2L, toPersonName = "Asha", amount = 50.0),
    )
    private val settled = mutableListOf<Long>()

    @Test
    fun `rows that involve you settle with the other person, rows between others have no button`() {
        composeTestRule.setContent {
            SplitFlowTheme { SimplifyDebtsContent(debts, onSettle = { settled += it }) }
        }
        composeTestRule.onNodeWithTag("simplify_settle_2").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("simplify_settle_3").assertIsDisplayed().performClick()

        assertEquals(listOf(2L, 3L), settled)
    }

    @Test
    fun `without a settle action there are no buttons`() {
        composeTestRule.setContent { SplitFlowTheme { SimplifyDebtsContent(debts) } }

        composeTestRule.onNodeWithTag("simplify_settle_2").assertDoesNotExist()
        composeTestRule.onNodeWithTag("simplify_settle_3").assertDoesNotExist()
    }
}
