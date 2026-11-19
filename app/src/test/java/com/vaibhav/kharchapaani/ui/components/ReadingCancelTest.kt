package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** D-21: while a bill or line is being read, the sheet offers a visible Cancel that stops the read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReadingCancelTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var cancelled = 0

    private fun show(isParsing: Boolean, withCancel: Boolean) {
        composeTestRule.setContent {
            SplitFlowTheme {
                ConfirmSheetContent(
                    isParsing = isParsing, isEditing = false, type = "expense", amount = "", merchant = "",
                    category = "Food", paymentMethod = "GPay", note = "", date = 0L, groupId = null,
                    availableGroups = emptyList(), isSplit = false, splitMode = SplitMode.EQUAL, paidByPersonId = -1L,
                    shares = emptyList(), onTypeChange = {}, onAmountChange = {}, onMerchantChange = {},
                    onCategoryChange = {}, onPaymentMethodChange = {}, onNoteChange = {}, onDateChange = {},
                    onGroupIdChange = {}, onIsSplitChange = {}, onSplitModeChange = {}, onPaidByPersonIdChange = {},
                    onTogglePersonShare = {}, onExactAmountChange = { _, _ -> }, onPercentageChange = { _, _ -> },
                    onOpenAddFriend = {}, onSave = {},
                    onCancelReading = if (withCancel) ({ cancelled++ }) else null,
                )
            }
        }
    }

    @Test
    fun `a read in progress shows Cancel, and tapping it cancels`() {
        show(isParsing = true, withCancel = true)
        composeTestRule.onNodeWithTag("confirm_reading_cancel").assertIsDisplayed().performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `no Cancel once the read is done`() {
        show(isParsing = false, withCancel = true)
        composeTestRule.onNodeWithTag("confirm_reading_cancel").assertDoesNotExist()
    }
}
