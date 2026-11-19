package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-06: the edit sheet of your own expense has a visible Delete; a new entry does not. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DeleteFromEditSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var deleted = 0

    private fun show(isEditing: Boolean, withDelete: Boolean) {
        composeTestRule.setContent {
            SplitFlowTheme {
                ConfirmSheetContent(
                    isParsing = false, isEditing = isEditing, type = "expense", amount = "120", merchant = "Chai",
                    category = "Food", paymentMethod = "GPay", note = "", date = 0L, groupId = null,
                    availableGroups = emptyList(), isSplit = false, splitMode = SplitMode.EQUAL, paidByPersonId = -1L,
                    shares = emptyList(), onTypeChange = {}, onAmountChange = {}, onMerchantChange = {},
                    onCategoryChange = {}, onPaymentMethodChange = {}, onNoteChange = {}, onDateChange = {},
                    onGroupIdChange = {}, onIsSplitChange = {}, onSplitModeChange = {}, onPaidByPersonIdChange = {},
                    onTogglePersonShare = {}, onExactAmountChange = { _, _ -> }, onPercentageChange = { _, _ -> },
                    onOpenAddFriend = {}, onSave = {},
                    onDelete = if (withDelete) ({ deleted++ }) else null,
                )
            }
        }
    }

    @Test
    fun `editing your own expense offers Delete, and tapping it deletes`() {
        show(isEditing = true, withDelete = true)
        composeTestRule.onNodeWithTag("confirm_delete_button").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun `a new entry, or an expense you cannot delete, shows no Delete`() {
        show(isEditing = false, withDelete = true)
        composeTestRule.onNodeWithTag("confirm_delete_button").assertDoesNotExist()
    }

    @Test
    fun `editing without a delete action shows no Delete`() {
        show(isEditing = true, withDelete = false)
        composeTestRule.onNodeWithTag("confirm_delete_button").assertDoesNotExist()
    }
}
