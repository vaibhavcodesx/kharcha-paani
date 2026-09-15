package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CategoriesContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var prefs by mutableStateOf(CategoryPrefs())

    private fun show() {
        composeTestRule.setContent {
            SplitFlowTheme { CategoriesContent(prefs = prefs, onChange = { change -> prefs = change(prefs) }) }
        }
    }

    @Test
    fun `a new spending category is added and offered in the picker`() {
        show()

        composeTestRule.onNodeWithTag("add_expense_input").performScrollTo().performTextReplacement("  Pets ")
        composeTestRule.onNodeWithTag("add_expense_button").performClick()

        assertEquals(listOf("Pets"), prefs.customExpense)
        assertTrue("Pets" in Categories.expenseCategories(prefs))
        composeTestRule.onNodeWithTag("choice_expense_Pets").performScrollTo()
    }

    @Test
    fun `a name that's already a category can't be added`() {
        show()

        composeTestRule.onNodeWithTag("add_expense_input").performScrollTo().performTextReplacement("groceries")

        composeTestRule.onNodeWithTag("add_expense_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("That one's already there.").assertExists()
    }

    @Test
    fun `a default can be hidden and shown again, but Other stays`() {
        show()

        composeTestRule.onNodeWithTag("choice_expense_Team Payment").performScrollTo().performClick()
        assertFalse("Team Payment" in Categories.expenseCategories(prefs))

        composeTestRule.onNodeWithTag("choice_expense_Team Payment").performClick()
        assertTrue("Team Payment" in Categories.expenseCategories(prefs))

        composeTestRule.onNodeWithTag("choice_expense_Other").performScrollTo().performClick()
        assertTrue("Other" in Categories.expenseCategories(prefs))
    }

    @Test
    fun `your own category can be removed`() {
        prefs = CategoryPrefs(customIncome = listOf("Tutoring"))
        show()

        composeTestRule.onNodeWithTag("remove_income_Tutoring").performScrollTo().performClick()

        assertEquals(emptyList<String>(), prefs.customIncome)
    }

    @Test
    fun `payment methods have their own tab`() {
        show()

        composeTestRule.onNodeWithTag("categories_tab_PaymentMethods").performClick()
        composeTestRule.onNodeWithTag("add_payment_input").performScrollTo().performTextReplacement("Amex")
        composeTestRule.onNodeWithTag("add_payment_button").performClick()
        composeTestRule.onNodeWithTag("choice_payment_PopUPI").performScrollTo().performClick()

        assertEquals(listOf("Amex"), prefs.customPaymentMethods)
        assertEquals(setOf("PopUPI"), prefs.hiddenPaymentMethods)
    }
}
