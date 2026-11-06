package com.vaibhav.kharchapaani.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.ui.auth.AccountSheetContent
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.home.CaptureCard
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.AiFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CaptureAiChoiceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun `AI off stops microphone and widget speech before launching another app, and nudges instead`() {
        val nudges = mutableListOf<AiFeature>()
        var consumed = 0
        compose.setContent {
            SplitFlowTheme {
                CaptureCard(isParsing = false, onParseText = {}, onParseImage = {}, onParsePdf = { _, _ -> },
                    onOpenIou = {}, aiEnabled = false, startVoiceCapture = true,
                    onVoiceCaptureStarted = { consumed++ }, onAiOff = { nudges += it })
            }
        }
        compose.waitForIdle()
        assertEquals(1, consumed)
        assertEquals(listOf(AiFeature.Voice), nudges)
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        compose.onNodeWithTag("voice_mic_button").performClick()
        compose.runOnIdle { assertEquals(listOf(AiFeature.Voice, AiFeature.Voice), nudges) }
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
        compose.onNodeWithTag("capture_ai_status").assertTextEquals("AI is off · quick add stays on this phone  See what AI adds")
        compose.onNodeWithTag("capture_ai_status").performClick()
        compose.runOnIdle { assertEquals(AiFeature.General, nudges.last()) }
        assertNull(shadowOf(compose.activity).nextStartedActivityForResult)
    }

    @Test fun `capture identifies Gemini while AI is on`() {
        compose.setContent {
            SplitFlowTheme {
                CaptureCard(isParsing = false, onParseText = {}, onParseImage = {}, onParsePdf = { _, _ -> },
                    onOpenIou = {}, aiEnabled = true)
            }
        }
        compose.onNodeWithTag("capture_ai_status").assertTextEquals("Typed lines use Groq · bills, statements and voice use Google Gemini")
    }

    @Test fun `settings exposes a labelled off switch and asks to enable it explicitly`() {
        val changes = mutableListOf<Boolean>()
        compose.setContent {
            SplitFlowTheme {
                AccountSheetContent(state = AuthUiState(authState = AuthState.SignedOut), syncStatus = SyncStatus(),
                    askBeforeDiscarding = true, onSaveProfile = { _, _ -> }, onAskBeforeDiscardingChange = {},
                    onOpenExport = {}, onRetryFailedSync = {}, onRetryProfile = {}, onSignOut = {},
                    aiEnabled = false, onAiEnabledChange = { changes += it })
            }
        }
        compose.onNodeWithTag("setting_ai_enabled").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(listOf(true), changes) }
    }
}
