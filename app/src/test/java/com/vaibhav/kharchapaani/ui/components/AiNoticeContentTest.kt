package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The notice says plainly what the free tier means (decision D2), and either answer works. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class AiNoticeContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var gotIt = 0
    private var typeMyself = 0

    private fun show() = compose.setContent {
        SplitFlowTheme { AiNoticeContent(onGotIt = { gotIt++ }, onTypeMyself = { typeMyself++ }) }
    }

    @Test
    fun `it says who reads it, that Google may use it, and that it's never sold`() {
        show()

        compose.onNodeWithText("Bills, statements and quick-add text are read by Google Gemini").assertExists()
        compose.onNodeWithText("Google may use what's sent to improve Gemini, and its reviewers may see it", substring = true).assertExists()
        compose.onNodeWithText("we never sell your data", substring = true).assertExists()
    }

    @Test
    fun `Got it and Type it myself each answer it`() {
        show()

        compose.onNodeWithTag("ai_notice_got_it").performClick()
        compose.onNodeWithTag("ai_notice_type_myself").performClick()

        assertEquals(1, gotIt)
        assertEquals(1, typeMyself)
    }
}
