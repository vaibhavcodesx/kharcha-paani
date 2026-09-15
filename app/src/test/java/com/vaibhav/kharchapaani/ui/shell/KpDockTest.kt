package com.vaibhav.kharchapaani.ui.shell

import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KpDockTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tapping a tab selects it and only it`() {
        composeTestRule.setContent {
            var tab by remember { mutableStateOf(AppTab.Activity) }
            SplitFlowTheme { KpDock(currentTab = tab, onSelectTab = { tab = it }, onAdd = {}) }
        }

        composeTestRule.onNodeWithTag("tab_activity").assertIsSelected()
        composeTestRule.onNodeWithText("Splits").performClick()

        composeTestRule.onNodeWithTag("tab_splits").assertIsSelected()
        composeTestRule.onNodeWithTag("tab_activity").assertIsNotSelected()
        composeTestRule.onNodeWithTag("tab_insights").assertIsNotSelected()
    }

    @Test
    fun `the add button is one labelled action`() {
        var adds = 0
        composeTestRule.setContent {
            SplitFlowTheme { KpDock(currentTab = AppTab.Insights, onSelectTab = {}, onAdd = { adds++ }) }
        }

        composeTestRule.onNodeWithContentDescription("Add expense").performClick()

        assertEquals(1, adds)
    }
}
