package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ConfirmSheetContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Composable
    private fun Sheet(
        notice: String? = null,
        amount: String = "370",
        category: String = "Food",
        onCategoryChange: (String) -> Unit = {},
        onIsSplitChange: (Boolean) -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        SplitFlowTheme {
            ConfirmSheetContent(
                isParsing = false, isEditing = false, notice = notice, type = "expense", amount = amount, merchant = "McDonald's",
                category = category, paymentMethod = "GPay", note = "", date = 0L, groupId = null,
                availableGroups = emptyList(), isSplit = false, splitMode = SplitMode.EQUAL, paidByPersonId = -1L,
                shares = emptyList(), onTypeChange = {}, onAmountChange = {}, onMerchantChange = {},
                onCategoryChange = onCategoryChange, onPaymentMethodChange = {}, onNoteChange = {}, onDateChange = {},
                onGroupIdChange = {}, onIsSplitChange = onIsSplitChange, onSplitModeChange = {}, onPaidByPersonIdChange = {},
                onTogglePersonShare = {}, onExactAmountChange = { _, _ -> }, onPercentageChange = { _, _ -> },
                onOpenAddFriend = {}, onSave = onSave,
            )
        }
    }

    @Test
    fun `save says what's missing until there's an amount`() {
        composeTestRule.setContent { Sheet(amount = "") }

        composeTestRule.onNodeWithTag("confirm_save_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Add an amount").assertExists()
    }

    @Test
    fun `save shows the amount and saves once`() {
        var saves = 0
        composeTestRule.setContent { Sheet(amount = "370", onSave = { saves++ }) }

        composeTestRule.onNodeWithText("₹370").assertExists()
        composeTestRule.onNodeWithTag("confirm_save_button").assertIsEnabled().performClick()

        assertEquals(1, saves)
    }

    @Test
    fun `a detail row opens its choices in place and closes after one is picked`() {
        composeTestRule.setContent {
            var category by remember { mutableStateOf("Food") }
            Sheet(category = category, onCategoryChange = { category = it })
        }

        composeTestRule.onNodeWithText("Category").performClick()
        composeTestRule.onNodeWithText("Transport").performClick()

        composeTestRule.onNodeWithText("Transport").assertExists()
        composeTestRule.onNodeWithText("Shopping").assertDoesNotExist()
    }

    @Test
    fun `choosing Split it asks to split`() {
        var split: Boolean? = null
        composeTestRule.setContent { Sheet(onIsSplitChange = { split = it }) }

        composeTestRule.onNodeWithTag("split_with_friends_button").performClick()

        assertEquals(true, split)
    }

    @Test
    fun `a bill that couldn't be read says so above the empty fields`() {
        composeTestRule.setContent { Sheet(notice = "Couldn't read that bill. Fill in the details, or try a clearer photo.", amount = "") }

        composeTestRule.onNodeWithTag("confirm_notice").assertExists()
        composeTestRule.onNodeWithText("Couldn't read that bill. Fill in the details, or try a clearer photo.").assertExists()
    }
}
