package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.AiFeature
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiOffNudgeContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `a bill nudge names the feature and offers to keep the photo without AI`() {
        var on = 0
        var off = 0
        compose.setContent { SplitFlowTheme { AiOffNudgeContent(AiFeature.Bill, onTurnOn = { on++ }, onKeepOff = { off++ }) } }
        compose.onNodeWithText("Reading a bill needs AI").assertExists()
        compose.onNodeWithText("Type it myself, keep the photo").assertExists()
        compose.onNodeWithTag("ai_off_nudge_turn_on").performClick()
        compose.onNodeWithTag("ai_off_nudge_keep_off").performClick()
        compose.runOnIdle { assertEquals(1, on); assertEquals(1, off) }
    }

    @Test fun `every feature has its own words`() {
        val copies = AiFeature.entries.filter { it != AiFeature.QuickAdd }.map { nudgeCopy(it) }
        assertEquals("each feature says something different", copies.size, copies.map { it.title }.toSet().size)
    }
}
