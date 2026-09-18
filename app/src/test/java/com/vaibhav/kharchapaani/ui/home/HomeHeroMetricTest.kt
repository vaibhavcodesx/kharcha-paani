package com.vaibhav.kharchapaani.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner decision, 2026-09-18: Home leads with what this month cost, not with the split balance.
 * This pins the swap, and that the debt keeps its one action after moving down a level.
 *
 * Amounts are asserted by content description: [com.vaibhav.kharchapaani.ui.components
 * .AnimatedRollingNumber] draws a digit per child and clears their semantics, so the whole figure
 * only exists as one description.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeHeroMetricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val analytics = MonthlyAnalytics(
        totalExpense = 5265.34,
        totalIncome = 1200.0,
        netCashflow = -4065.34,
        categorySummaries = emptyList(),
        topMerchant = "Rapido",
        dailyStreak = 4,
    )

    private var simplifyOpened = 0

    private fun showHeader(netBalance: Double) {
        composeTestRule.setContent {
            SplitFlowTheme {
                Column {
                    BalanceHeader(
                        monthlyExpense = analytics.totalExpense,
                        dailyStreak = analytics.dailyStreak,
                        accountAvatar = "V",
                        onOpenAccount = {},
                    )
                    MonthStrip(
                        analytics = analytics,
                        netBalance = netBalance,
                        onOpenSimplifyDebts = { simplifyOpened++ },
                    )
                }
            }
        }
    }

    @Test
    fun `the hero is what this month cost, not what is owed`() {
        showHeader(netBalance = -1953.34)

        composeTestRule.onNodeWithTag("hero_month_spend_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("SPENT THIS MONTH").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("₹5,265.34").assertIsDisplayed()
    }

    @Test
    fun `the streak stays with the hero`() {
        showHeader(netBalance = 0.0)

        composeTestRule.onNodeWithText("Logged 4 days in a row").assertIsDisplayed()
    }

    @Test
    fun `a debt shows in the second row, not the header`() {
        showHeader(netBalance = -1953.34)

        composeTestRule.onNodeWithText("YOU OWE").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("₹1,953.34").assertIsDisplayed()
    }

    @Test
    fun `being owed says so, in the same place`() {
        showHeader(netBalance = 949.0)

        composeTestRule.onNodeWithText("FRIENDS OWE YOU").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("₹949").assertIsDisplayed()
    }

    @Test
    fun `what came in keeps its place beside the debt`() {
        showHeader(netBalance = -1953.34)

        composeTestRule.onNodeWithText("CAME IN").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("₹1,200").assertIsDisplayed()
    }

    @Test
    fun `the debt figure still opens who pays whom`() {
        // The header link moved with the figure rather than being dropped in the swap.
        showHeader(netBalance = -1953.34)

        composeTestRule.onNodeWithTag("home_simplify_button").performClick()

        assertEquals(1, simplifyOpened)
    }

    @Test
    fun `with nobody owing anybody there is nothing to tap`() {
        showHeader(netBalance = 0.0)

        composeTestRule.onNodeWithText("ALL SQUARE").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodes(hasTestTag("home_simplify_button")).fetchSemanticsNodes().size,
        )
    }
}
