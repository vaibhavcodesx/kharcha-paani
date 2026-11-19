package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UX-19: the group form opens filled in when editing, has no Share switch, and saves what was changed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EditGroupSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val asha = PersonEntity(id = 1, name = "Asha", avatarColor = "#70C1FF")
    private val bilal = PersonEntity(id = 2, name = "Bilal", avatarColor = "#FFB3BA")
    private val group = GroupEntity(id = 7, name = "Goa", description = "trip", emoji = "✈️")
    private val details = GroupWithDetails(
        group = group, members = listOf(asha), totalExpenses = 0.0, myPaidAmount = 0.0, myShare = 0.0,
        netBalanceForMe = 0.0, transactionsCount = 0,
    )
    private var saved: List<Any>? = null

    @Test
    fun `editing opens filled in, hides Share, and saves the changed name and members`() {
        composeTestRule.setContent {
            SplitFlowTheme {
                CreateGroupContent(
                    allPeople = listOf(asha, bilal),
                    initialShared = true,
                    editing = details,
                    onSaveGroup = { name, desc, emoji, color, members, shared -> saved = listOf(name, desc, emoji, color, members, shared) },
                )
            }
        }
        composeTestRule.onNodeWithText("Edit group").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goa").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share with others").assertDoesNotExist()

        composeTestRule.onNodeWithTag("group_name_input").performTextReplacement("Goa 2026")
        composeTestRule.onNodeWithText("Bilal").performClick()
        composeTestRule.onNodeWithTag("save_group_button").performClick()

        assertEquals(listOf("Goa 2026", "trip", "✈️", group.colorHex, listOf(1L, 2L), false), saved)
    }
}
