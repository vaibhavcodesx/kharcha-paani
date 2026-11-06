package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Decision D1 on the screen: Former member unless chosen otherwise, and two steps before anything goes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DeleteAccountContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val deletes = mutableListOf<Boolean>()

    private fun show(unsynced: Int = 0) = compose.setContent {
        SplitFlowTheme {
            DeleteAccountContent(unsyncedChanges = unsynced, isDeleting = false, error = null, onSyncNow = {}, onDelete = { deletes += it }, onKeepAccount = {})
        }
    }

    @Test
    fun `left alone, it deletes as Former member, and only after the second confirm`() {
        show()

        compose.onNodeWithTag("delete_account_continue").performClick()
        assertEquals("the first tap only asks again", emptyList<Boolean>(), deletes)
        compose.onNodeWithTag("delete_account_confirm").performClick()

        assertEquals(listOf(false), deletes)
    }

    @Test
    fun `keeping the name is a choice that's passed on`() {
        show()

        compose.onNodeWithTag("delete_account_keep_name").performScrollTo().performClick()
        compose.onNodeWithTag("delete_account_continue").performClick()
        compose.onNodeWithTag("delete_account_confirm").performClick()

        assertEquals(listOf(true), deletes)
    }

    @Test
    fun `with changes not synced, it says so, and continuing first means accepting their loss`() {
        show(unsynced = 3)

        compose.onNodeWithTag("delete_account_unsynced").performScrollTo().assertExists()
        compose.onNodeWithTag("delete_account_continue").performClick()
        compose.onNodeWithTag("delete_account_confirm").assertDoesNotExist()
        compose.onNodeWithTag("delete_account_continue").performClick()
        compose.onNodeWithTag("delete_account_confirm").performClick()

        assertEquals(listOf(false), deletes)
    }

    @Test
    fun `shows localized copy from string resources`() {
        show(unsynced = 1)

        compose.onNodeWithText("Delete your account?").assertExists()
        compose.onNodeWithText("Here's what happens.").assertExists()
        compose.onNodeWithText("GOES").assertExists()
        compose.onNodeWithText("STAYS").assertExists()
        compose.onNodeWithText("ON PAST GROUP EXPENSES, SHOW ME AS").performScrollTo().assertExists()
        compose.onNodeWithText("Former member").performScrollTo().assertExists()
        compose.onNodeWithText("My name, as it is now").performScrollTo().assertExists()
        compose.onNodeWithText("1 change hasn't synced yet. Sync first, or it's lost with the account.").performScrollTo().assertExists()
        compose.onNodeWithText("Keep my account").performScrollTo().assertExists()
    }
}
