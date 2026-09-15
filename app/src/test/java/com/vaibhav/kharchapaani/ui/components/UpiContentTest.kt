package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class UpiContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var saved: UpiPayee? = null
    private var removed = false

    private fun show(existing: UpiPayee? = null) {
        composeTestRule.setContent {
            SplitFlowTheme { UpiContent(saved = existing, defaultName = "Priya Sharma", onSave = { saved = it }, onRemove = { removed = true }) }
        }
    }

    @Test
    fun `a UPI ID that isn't one says so and can't be saved`() {
        show()

        composeTestRule.onNodeWithTag("upi_id_input").performTextReplacement("priya.okaxis")

        composeTestRule.onNodeWithTag("upi_id_hint").assertTextContains("doesn't look like a UPI ID", substring = true)
        composeTestRule.onNodeWithTag("upi_save_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("upi_payment_card").assertDoesNotExist()
    }

    @Test
    fun `a real UPI ID shows its code and saves tidied, with the profile name`() {
        show()

        composeTestRule.onNodeWithTag("upi_id_input").performTextReplacement(" Priya@OKAXIS ")
        composeTestRule.onNodeWithTag("upi_payment_card").performScrollTo()
        composeTestRule.onNodeWithTag("upi_save_button").assertIsEnabled().performClick()

        assertEquals(UpiPayee("priya@okaxis", "Priya Sharma"), saved)
    }

    @Test
    fun `a saved ID can be removed, and saving the same again isn't offered`() {
        show(UpiPayee("priya@okaxis", "Priya"))

        composeTestRule.onNodeWithTag("upi_save_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("upi_remove_button").performScrollTo().performClick()

        assertTrue(removed)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettleUpUpiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rohan = PersonEntity(id = 2, name = "Rohan Mehta", avatarColor = "#70C1FF")

    private fun show(payee: UpiPayee?, owed: Double) {
        composeTestRule.setContent {
            SplitFlowTheme {
                SettleUpContent(
                    person = rohan, suggestedAmount = owed, groupOptions = com.vaibhav.kharchapaani.domain.SharedRecordGroups.Options(emptyList(), emptyList(), false),
                    onCreateGroup = {}, onConfirm = { _, _, _, _, _ -> }, upiPayee = payee,
                )
            }
        }
    }

    @Test
    fun `when a friend owes you, your code asks for that amount`() {
        show(UpiPayee("priya@okaxis", "Priya"), owed = 450.0)

        composeTestRule.onNodeWithTag("upi_payment_card").performScrollTo()
        composeTestRule.onNodeWithText("Scan to pay ₹450").assertExists()
    }

    @Test
    fun `no code when you're the one paying`() {
        show(UpiPayee("priya@okaxis", "Priya"), owed = -450.0)

        composeTestRule.onNodeWithTag("upi_payment_card").assertDoesNotExist()
    }

    @Test
    fun `no code without a UPI ID`() {
        show(null, owed = 450.0)

        composeTestRule.onNodeWithTag("upi_payment_card").assertDoesNotExist()
    }
}
