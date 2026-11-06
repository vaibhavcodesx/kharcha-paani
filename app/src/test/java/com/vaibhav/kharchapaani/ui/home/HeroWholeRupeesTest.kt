package com.vaibhav.kharchapaani.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.wholeRupeesOf
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Home's hero shows the month in whole rupees (U27); everything else keeps paise. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HeroWholeRupeesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `rounds half up to the nearest rupee`() {
        assertEquals(1235.0, wholeRupeesOf(1234.50), 0.0)
        assertEquals(1234.0, wholeRupeesOf(1234.49), 0.0)
        assertEquals(0.0, wholeRupeesOf(0.0), 0.0)
        assertEquals(1234568.0, wholeRupeesOf(1234567.89), 0.0)
        assertEquals(Double.NaN, wholeRupeesOf(Double.NaN), 0.0)
    }

    @Test fun `the hero reads whole rupees, other figures keep paise`() {
        compose.setContent {
            SplitFlowTheme {
                androidx.compose.foundation.layout.Column {
                    BalanceHeader(monthlyExpense = 12345.50, dailyStreak = 0, accountAvatar = "V", onOpenAccount = {})
                    AnimatedRollingNumber(targetValue = 999.25)
                }
            }
        }
        compose.onNodeWithContentDescription("₹12,346").assertExists()
        compose.onNodeWithContentDescription("₹999.25").assertExists()
    }
}
