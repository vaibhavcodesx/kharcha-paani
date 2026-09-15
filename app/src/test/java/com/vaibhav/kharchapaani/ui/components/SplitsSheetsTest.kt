package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SplitsSheetsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val bob = PersonEntity(id = 2, name = "Bob", avatarColor = "#70C1FF")
    private val carol = PersonEntity(id = 3, name = "Carol", avatarColor = "#FFB3BA")
    private val noGroups = SharedRecordGroups.Options(emptyList(), emptyList(), involvesGroupmates = false)

    @Test
    fun `a friend row opens history on tap and settles from its button`() {
        var history = 0
        var settles = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                FriendRow(PersonBalance(bob, 250.0, 250.0, 0.0), onOpenHistory = { history++ }, onSettleUp = { settles++ }, onDelete = {})
            }
        }

        composeTestRule.onNodeWithText("Owes you").assertExists()
        composeTestRule.onNodeWithText("₹250").assertExists()
        composeTestRule.onNodeWithTag("settle_person_2").performClick()
        composeTestRule.onNodeWithText("Bob").performClick()

        assertEquals(1, settles)
        assertEquals(1, history)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `deleting a friend is one step away, not a bin on every row`() {
        var deletes = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                FriendRow(PersonBalance(bob, 0.0, 0.0, 0.0), onOpenHistory = {}, onSettleUp = {}, onDelete = { deletes++ })
            }
        }

        composeTestRule.onNodeWithContentDescription("Delete Friend").assertDoesNotExist()
        composeTestRule.onNodeWithTag("person_balance_card_2").performCustomAccessibilityActionWithLabel("Delete Bob")

        assertEquals(1, deletes)
    }

    @Test
    fun `adding a friend needs a name and keeps the chosen colour`() {
        var added: Pair<String, String>? = null
        composeTestRule.setContent { SplitFlowTheme { AddFriendContent(onAdd = { n, c -> added = n to c }) } }

        composeTestRule.onNodeWithTag("confirm_add_friend_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Type their name").assertExists()
        composeTestRule.onNodeWithTag("add_friend_name_input").performTextReplacement("  Rohan  ")
        composeTestRule.onNodeWithContentDescription("Mint").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("confirm_add_friend_button").assertIsEnabled().performClick()

        assertEquals("Rohan" to "#B5EAD7", added)
    }

    @Test
    fun `an IOU makes you pick the person when there's more than one friend`() {
        val recorded = mutableListOf<List<Any?>>()
        composeTestRule.setContent {
            SplitFlowTheme {
                IouContent(
                    people = listOf(bob, carol),
                    groupOptionsFor = { noGroups },
                    onCreateGroup = {},
                    onConfirm = { from, to, amount, note, groupId -> recorded += listOf(from, to, amount, note, groupId) },
                )
            }
        }

        composeTestRule.onNodeWithTag("iou_amount_input").performTextReplacement("500")
        composeTestRule.onNodeWithTag("confirm_iou_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Choose who you lent to").assertExists()
        composeTestRule.onNodeWithTag("iou_person_3").performClick()
        composeTestRule.onNodeWithTag("confirm_iou_button").assertIsEnabled().performClick()

        assertEquals(listOf(listOf(-1L, 3L, 500.0, "Informal Loan", null)), recorded)
    }

    @Test
    fun `a shared group's name is capped, and a local group takes the members picked`() {
        var saved: List<Any>? = null
        composeTestRule.setContent {
            SplitFlowTheme {
                CreateGroupContent(allPeople = listOf(bob, carol), initialShared = true, onSaveGroup = { name, _, _, _, members, shared ->
                    saved = listOf(name, members, shared)
                })
            }
        }

        composeTestRule.onNodeWithTag("group_name_input").performTextReplacement("x".repeat(61))
        composeTestRule.onNodeWithTag("save_group_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Name is too long").assertExists()

        composeTestRule.onNodeWithText("Share with others").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("group_name_input").performTextReplacement("Flat")
        composeTestRule.onNodeWithText("Carol").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("save_group_button").assertIsEnabled().performClick()

        assertEquals(listOf("Flat", listOf(3L), false), saved)
    }
}
