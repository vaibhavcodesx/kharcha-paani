package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedGroupUiTest {
    private val copyContext = ApplicationProvider.getApplicationContext<Context>()

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `a previewed invite shows who invited you and joins on tap`() {
        var joins = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                JoinGroupContent(
                    state = JoinGroupState(input = "AB2CD3EF", preview = InvitePreview("g", "Goa trip", "🏖️", "Bob", 3)),
                    onInputChange = {},
                    onJoin = { joins++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Invited by Bob • 3 members").assertIsDisplayed()
        composeTestRule.onNodeWithText("Join Goa trip").performClick()

        assertEquals(1, joins)
    }

    @Test
    fun `a bad code shows the error and can't be joined`() {
        composeTestRule.setContent {
            SplitFlowTheme {
                JoinGroupContent(
                    state = JoinGroupState(input = "ZZ2ZZ3ZZ", error = copyContext.getString(SharedGroupsController.INVALID_CODE)),
                    onInputChange = {},
                    onJoin = {},
                )
            }
        }

        composeTestRule.onNodeWithText(copyContext.getString(SharedGroupsController.INVALID_CODE)).assertIsDisplayed()
        composeTestRule.onNodeWithText("Join group").assertIsNotEnabled()
    }

    @Test
    fun `a member sees the invite code and can share it, but only the creator can archive`() {
        var shared: String? = null
        composeTestRule.setContent {
            SplitFlowTheme {
                SharedGroupPanel(
                    group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
                    members = listOf(PersonEntity(name = "Bob", avatarColor = "#70C1FF", profileId = "bob")),
                    isCreator = false,
                    invite = GroupInviteState(groupId = 1, code = "AB2CD3EF", expiresAtMillis = 1_800_000_000_000),
                    isLeaving = false,
                    unsyncedChanges = 0,
                    onSyncNow = {},
                    onCreateInvite = {},
                    onShareInvite = { shared = it },
                    onRemoveMember = { error("Only the creator can remove someone") },
                    onRevokeInvite = {},
                    onSetArchived = {},
                    onLeave = {},
                )
            }
        }

        composeTestRule.onNodeWithText("AB2C D3EF").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share link").performClick()
        assertEquals("kharchapaani://join/AB2CD3EF", shared)
        composeTestRule.onNodeWithText("Leave group").assertIsEnabled()
        composeTestRule.onNodeWithText("Archive group").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Remove Bob").assertDoesNotExist()
    }

    @Test
    fun `creator sees the member name and outstanding balance before confirming removal`() {
        var removed: String? = null
        composeTestRule.setContent {
            SplitFlowTheme {
                SharedGroupPanel(
                    group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
                    members = listOf(PersonEntity(id = 12, name = "Bob", avatarColor = "#70C1FF", profileId = "bob")),
                    isCreator = true,
                    invite = null,
                    isLeaving = false,
                    unsyncedChanges = 0,
                    onSyncNow = {},
                    onCreateInvite = {},
                    onShareInvite = {},
                    onRevokeInvite = {},
                    onSetArchived = {},
                    onLeave = {},
                    onRemoveMember = { removed = it },
                    outstandingMembers = setOf(12L),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Remove Bob").performClick()
        composeTestRule.onNodeWithText("Remove Bob from the group?").assertExists()
        composeTestRule.onNodeWithText("They have an outstanding balance in this group.").assertExists()
        assertEquals(null, removed)
        composeTestRule.onNodeWithText("Remove").performClick()
        assertEquals("bob", removed)
    }

    @Test
    fun `leaving a group shows what I owe or am owed, and nothing when settled`() {
        val balance = androidx.compose.runtime.mutableStateOf(-250.0)
        composeTestRule.setContent {
            SplitFlowTheme {
                SharedGroupPanel(
                    group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
                    members = emptyList(),
                    isCreator = false,
                    invite = null,
                    isLeaving = false,
                    unsyncedChanges = 0,
                    onSyncNow = {},
                    onCreateInvite = {},
                    onShareInvite = {},
                    onRevokeInvite = {},
                    onSetArchived = {},
                    onLeave = {},
                    myBalance = balance.value,
                )
            }
        }

        composeTestRule.onNodeWithText("Leave group").performClick()
        composeTestRule.onNodeWithText("You owe ₹250 in this group. Leaving does not clear it, so settle up first.").assertExists()
        composeTestRule.onNodeWithText("Cancel").performClick()

        balance.value = 80.0
        composeTestRule.onNodeWithText("Leave group").performClick()
        composeTestRule.onNodeWithText("You are owed ₹80 in this group. Leaving does not collect it, so ask to be paid first.").assertExists()
        composeTestRule.onNodeWithText("Cancel").performClick()

        balance.value = 0.0
        composeTestRule.onNodeWithText("Leave group").performClick()
        composeTestRule.onNodeWithText("Its expenses and settlements will be removed from this phone. Other members keep them, and you can rejoin with a new invite.").assertExists()
        composeTestRule.onAllNodesWithText("in this group", substring = true).assertCountEquals(0)
    }

    @Test
    fun `leaving with unsynced changes offers to sync first`() {
        var leaves = 0
        var syncs = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                SharedGroupPanel(
                    group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
                    members = emptyList(),
                    isCreator = false,
                    invite = null,
                    isLeaving = false,
                    unsyncedChanges = 2,
                    onSyncNow = { syncs++ },
                    onCreateInvite = {},
                    onShareInvite = {},
                    onRevokeInvite = {},
                    onSetArchived = {},
                    onLeave = { leaves++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Leave group").performClick()
        composeTestRule.onNodeWithText("2 changes you made here haven't synced yet and would be lost. Sync first to keep them.").assertExists()
        composeTestRule.onNodeWithText("Sync now").performClick()
        assertEquals(listOf(1, 0), listOf(syncs, leaves))

        composeTestRule.onNodeWithText("Leave group").performClick()
        composeTestRule.onNodeWithText("Leave anyway").performClick()
        assertEquals(1, leaves)
    }

    @Test
    fun `leaving asks for confirmation first`() {
        var leaves = 0
        composeTestRule.setContent {
            SplitFlowTheme {
                SharedGroupPanel(
                    group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
                    members = emptyList(),
                    isCreator = true,
                    invite = null,
                    isLeaving = false,
                    unsyncedChanges = 0,
                    onSyncNow = {},
                    onCreateInvite = {},
                    onShareInvite = {},
                    onRevokeInvite = {},
                    onSetArchived = {},
                    onLeave = { leaves++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Leave group").performClick()
        assertEquals(0, leaves)
        composeTestRule.onNodeWithText("Leave").performClick()
        assertEquals(1, leaves)
    }
}
