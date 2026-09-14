package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedGroupUiTest {

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
                    state = JoinGroupState(input = "ZZ2ZZ3ZZ", error = SharedGroupsController.INVALID_CODE),
                    onInputChange = {},
                    onJoin = {},
                )
            }
        }

        composeTestRule.onNodeWithText(SharedGroupsController.INVALID_CODE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Join group").assertIsNotEnabled()
    }

    @Test
    fun `a member sees the invite code and can share it, but only the creator can archive`() {
        var shared: String? = null
        composeTestRule.setContent {
            SplitFlowTheme {
                SharedGroupPanel(
                    group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
                    members = listOf(PersonEntity(name = "Bob", avatarColor = "#70C1FF")),
                    isCreator = false,
                    invite = GroupInviteState(groupId = 1, code = "AB2CD3EF", expiresAtMillis = 1_800_000_000_000),
                    isLeaving = false,
                    unsyncedChanges = 0,
                    onSyncNow = {},
                    onCreateInvite = {},
                    onShareInvite = { shared = it },
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
