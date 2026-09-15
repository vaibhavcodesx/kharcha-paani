package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.components.SettleUpContent
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecordInGroupUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val bob = PersonEntity(id = 2, name = "Bob", avatarColor = "#70C1FF", profileId = "b")
    private val goa = GroupEntity(id = 10, name = "Goa", emoji = "🏖️", syncState = SyncState.SYNCED)
    private val flat = GroupEntity(id = 11, name = "Flat", emoji = "🏠", syncState = SyncState.SYNCED)

    private data class Recorded(val from: Long, val to: Long, val amount: Double, val groupId: Long?)

    private fun settleUp(options: SharedRecordGroups.Options, onCreateGroup: () -> Unit = {}): MutableList<Recorded> {
        val recorded = mutableListOf<Recorded>()
        composeTestRule.setContent {
            SplitFlowTheme {
                SettleUpContent(
                    person = bob,
                    suggestedAmount = 120.0,
                    groupOptions = options,
                    onCreateGroup = onCreateGroup,
                    onConfirm = { from, to, amount, _, groupId -> recorded += Recorded(from, to, amount, groupId) },
                )
            }
        }
        return recorded
    }

    @Test
    fun `settling with a groupmate in one shared group records it in that group`() {
        val recorded = settleUp(SharedRecordGroups.Options(listOf(goa), emptyList(), involvesGroupmates = true))

        composeTestRule.onNodeWithTag("confirm_settle_button").performClick()

        assertEquals(listOf(Recorded(from = 2, to = -1, amount = 120.0, groupId = 10)), recorded)
    }

    @Test
    fun `with several shared groups a group must be chosen before recording`() {
        val recorded = settleUp(SharedRecordGroups.Options(listOf(goa, flat), emptyList(), involvesGroupmates = true))

        composeTestRule.onNodeWithTag("confirm_settle_button").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Choose a group").assertExists()
        composeTestRule.onNodeWithText("🏠 Flat").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("confirm_settle_button").assertIsEnabled().performClick()

        assertEquals(11L, recorded.single().groupId)
    }

    @Test
    fun `with no shared group it offers to create one and says it stays on this phone`() {
        var creates = 0
        val recorded = settleUp(SharedRecordGroups.Options(emptyList(), emptyList(), involvesGroupmates = true), onCreateGroup = { creates++ })

        composeTestRule.onNodeWithText("Create a shared group").performScrollTo().performClick()
        assertEquals(1, creates)
        composeTestRule.onNodeWithText("Save on this phone only").performClick()
        assertEquals(null, recorded.single().groupId)
    }

    @Test
    fun `a friend without an account settles locally with no group section`() {
        val recorded = settleUp(SharedRecordGroups.Options(emptyList(), emptyList(), involvesGroupmates = false))

        composeTestRule.onNodeWithText("Record in group").assertDoesNotExist()
        composeTestRule.onNodeWithTag("settle_amount_input").performTextReplacement("50")
        composeTestRule.onNodeWithText("Record settlement").performClick()
        assertEquals(Recorded(from = 2, to = -1, amount = 50.0, groupId = null), recorded.single())
    }

    @Test
    fun `the split prompt shares in the chosen group, or keeps it local`() {
        var sharedIn: Long? = null
        var keptLocal = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                ShareSplitPrompt(
                    options = SharedRecordGroups.Options(listOf(goa, flat), emptyList(), involvesGroupmates = true),
                    onShareIn = { sharedIn = it },
                    onCreateGroup = {},
                    onKeepLocal = { keptLocal++ },
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("🏖️ Goa").performClick()
        assertEquals(10L, sharedIn)
        composeTestRule.onNodeWithText("Save on this phone only").performClick()
        assertEquals(1, keptLocal)
    }

    @Test
    fun `the split prompt explains when a friend has no account`() {
        composeTestRule.setContent {
            SplitFlowTheme {
                ShareSplitPrompt(
                    options = SharedRecordGroups.Options(
                        emptyList(),
                        listOf(PersonEntity(id = 4, name = "Rahul", avatarColor = "#fff")),
                        involvesGroupmates = true,
                    ),
                    onShareIn = {},
                    onCreateGroup = {},
                    onKeepLocal = {},
                    onDismiss = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Rahul isn't on Kharcha Paani, so a split including them can only stay on this phone.").assertExists()
        composeTestRule.onNodeWithText("Create a shared group").assertDoesNotExist()
    }
}
