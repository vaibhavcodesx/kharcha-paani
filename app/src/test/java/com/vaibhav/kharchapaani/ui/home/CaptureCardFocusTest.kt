package com.vaibhav.kharchapaani.ui.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The widget's quick-add bar lands in the capture line with the cursor in it (and so the keyboard
 * up), instead of opening the New entry form.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureCardFocusTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(request: androidx.compose.runtime.MutableState<Boolean>, onFocused: () -> Unit) {
        compose.setContent {
            SplitFlowTheme {
                CaptureCard(
                    isParsing = false,
                    onParseText = {}, onParseImage = {}, onParsePdf = { _, _ -> }, onOpenIou = {},
                    focusCapture = request.value,
                    onCaptureFocused = onFocused,
                )
            }
        }
    }

    @Test
    fun `a focus request puts the cursor in the capture line, once`() {
        val request = mutableStateOf(true)
        var focused = 0
        show(request) { focused++; request.value = false }
        compose.waitForIdle()

        compose.onNodeWithTag("quick_capture_input").assertIsFocused()
        assertEquals(1, focused)
    }

    @Test
    fun `without a request the capture line is left alone`() {
        val request = mutableStateOf(false)
        var focused = 0
        show(request) { focused++ }
        compose.waitForIdle()

        assertEquals(0, focused)
    }
}
