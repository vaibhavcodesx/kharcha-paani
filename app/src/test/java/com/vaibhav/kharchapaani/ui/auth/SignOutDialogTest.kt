package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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

/** Signing out can leave nothing of the account on the phone, but never loses unsynced changes (T2.6). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SignOutDialogTest {
    @get:Rule val compose = createComposeRule()

    private val choices = mutableListOf<Boolean>()

    private fun show(unsynced: Int) = compose.setContent {
        SplitFlowTheme { SignOutDialog(isOpen = true, unsyncedChanges = unsynced, onSignOut = { choices += it }, onDismiss = {}) }
    }

    @Test fun `keeps the data by default`() {
        show(unsynced = 0)
        compose.onNodeWithTag("sign_out_remove_data").assertIsOff()
        compose.onNodeWithTag("confirm_sign_out_button").performClick()
        compose.runOnIdle { assertEquals(listOf(false), choices) }
    }

    @Test fun `with everything synced, removing the data can be chosen`() {
        show(unsynced = 0)
        compose.onNodeWithTag("sign_out_remove_data").performClick().assertIsOn()
        compose.onNodeWithTag("confirm_sign_out_button").performClick()
        compose.runOnIdle { assertEquals(listOf(true), choices) }
    }

    @Test fun `with changes waiting it can't remove them, and says why`() {
        show(unsynced = 2)
        compose.onNodeWithTag("sign_out_remove_data").assertIsNotEnabled()
        compose.onNodeWithText("2 changes haven't synced yet", substring = true).assertExists()
        compose.onNodeWithTag("confirm_sign_out_button").performClick()
        compose.runOnIdle { assertEquals(listOf(false), choices) }
    }
}
