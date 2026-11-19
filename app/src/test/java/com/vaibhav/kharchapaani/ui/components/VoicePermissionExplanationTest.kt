package com.vaibhav.kharchapaani.ui.components

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import com.vaibhav.kharchapaani.ui.home.CaptureCard
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class VoicePermissionExplanationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val requests = mutableListOf<Pair<Int, Any?>>()
    private val messages = mutableListOf<String>()
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            requests += requestCode to input
        }
    }

    private fun tapMicrophone() {
        shadowOf(compose.activity.application).denyPermissions(Manifest.permission.RECORD_AUDIO)
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                SplitFlowTheme {
                    CaptureCard(
                        isParsing = false,
                        onParseText = {},
                        onParseImage = {},
                        onParsePdf = { _, _ -> },
                        onOpenIou = {},
                        onCaptureFailed = { messages += it },
                    )
                }
            }
        }
        compose.onNodeWithTag("voice_mic_button").performClick()
        compose.onNodeWithTag("permission_reason").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Pair<Int, Any?>>(), requests) }
    }

    @Test
    fun `Not now keeps typing available without requesting the microphone`() {
        tapMicrophone()
        compose.onNodeWithTag("permission_not_now").performClick()
        compose.onNodeWithTag("permission_reason").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(emptyList<Pair<Int, Any?>>(), requests)
            assertEquals(1, messages.size)
        }
        typeAnEntry()
    }

    @Test
    fun `Continue requests the microphone once and denying it leaves typing available`() {
        tapMicrophone()
        compose.onNodeWithTag("permission_continue").performClick()
        compose.runOnIdle {
            val (requestCode, input) = requests.single()
            assertEquals(Manifest.permission.RECORD_AUDIO, input)
            registry.dispatchResult(requestCode, false)
        }
        compose.onNodeWithTag("permission_reason").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, requests.size)
            assertEquals(1, messages.size)
        }
        typeAnEntry()
    }

    private fun typeAnEntry() {
        val input = compose.onNodeWithTag("quick_capture_input")
        input.performTextInput("Lunch 200")
        input.assertTextEquals("Lunch 200")
    }
}
