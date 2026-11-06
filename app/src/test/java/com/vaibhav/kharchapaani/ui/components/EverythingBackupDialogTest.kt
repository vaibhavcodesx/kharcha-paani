package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.domain.EverythingBackup
import com.vaibhav.kharchapaani.domain.EverythingPerson
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Component gates only; real Room/archive integrity is covered by EverythingBackupRepositoryTest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class EverythingBackupDialogTest {
    @get:Rule val compose = createComposeRule()
    private val backup = EverythingBackup(exportedAt = 0, appVersion = "test", people = listOf(
        EverythingPerson(11, "Same name", "#123456", 1, null, null, null, null, null),
        EverythingPerson(22, "Same name", "#654321", 2, null, null, null, null, null),
    ))

    private fun awaitValidation() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("everything_backup_progress").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun `preview counts duplicate names separately and restore waits for confirmation`() {
        var restores = 0
        var dismisses = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(backup, true, true, false, { restores++ }, { dismisses++ })
        } }
        compose.onNodeWithText("Friends: 2", substring = true).assertExists()
        compose.onNodeWithText("Shared groups come back as your own copy. Restoring doesn't rejoin them or upload anything.").assertExists()
        assertEquals(0, restores)
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("everything_restore_confirm").fetchSemanticsNode().config
                .getOrNull(SemanticsProperties.Disabled) == null
        }
        compose.onNodeWithTag("everything_restore_confirm").assertIsEnabled().performClick()
        assertEquals(1, restores)
        assertEquals(0, dismisses)
    }

    @Test fun `occupied destination cannot be restored into`() {
        var restores = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(backup, true, false, false, { restores++ }, {})
        } }
        awaitValidation()
        compose.onNodeWithText("This ledger already has entries. A full backup only goes into an empty one.").assertExists()
        compose.onNodeWithTag("everything_restore_confirm").assertIsNotEnabled().performClick()
        assertEquals(0, restores)
    }

    @Test fun `signed in destination cannot be restored into`() {
        var restores = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(backup, false, true, false, { restores++ }, {})
        } }
        awaitValidation()
        compose.onNodeWithText("Switch to On this phone first, then restore.").assertExists()
        compose.onNodeWithTag("everything_restore_confirm").assertIsNotEnabled().performClick()
        assertEquals(0, restores)
    }

    @Test fun `busy restore shows progress and blocks repeated confirmation and dismissal`() {
        var restores = 0
        var dismisses = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(backup, true, true, true, { restores++ }, { dismisses++ })
        } }
        compose.onNodeWithTag("everything_backup_progress").assertExists()
        compose.onNodeWithTag("everything_restore_confirm").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("everything_restore_cancel").assertIsNotEnabled().performClick()
        assertEquals(0, restores)
        assertEquals(0, dismisses)
    }

    @Test fun `malformed archive cannot be confirmed even when local ledger is empty`() {
        var restores = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(backup.copy(version = 999), true, true, false, { restores++ }, {})
        } }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Couldn't read that file. Pick a backup saved by Kharcha Paani.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Couldn't read that file. Pick a backup saved by Kharcha Paani.").assertExists()
        compose.onNodeWithTag("everything_restore_confirm").assertIsNotEnabled().performClick()
        assertEquals(0, restores)
    }

    @Test fun `read failure without a preview remains dismissible and cannot restore`() {
        var restores = 0
        var dismisses = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(null, true, true, false, { restores++ }, { dismisses++ }, hasReadError = true)
        } }
        compose.onNodeWithTag("everything_restore_confirm").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("everything_restore_cancel").performClick()
        assertEquals(0, restores)
        assertEquals(1, dismisses)
    }

    @Test fun `no picked archive has no preview`() {
        compose.setContent { SplitFlowTheme {
            EverythingBackupDialog(null, true, true, false, {}, {})
        } }
        compose.onNodeWithTag("everything_restore_dialog").assertDoesNotExist()
    }

    @Test fun `export explains plaintext scope and waits for an explicit choice`() {
        var exports = 0
        var dismisses = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupExportDialog(true, false, { exports++ }, { dismisses++ })
        } }
        compose.onNodeWithText("The file isn't encrypted, and it has all your money history and bill photos. Keep it somewhere private.").assertExists()
        compose.onNodeWithText("It's what's on this phone right now, without your sign-in or your AI choice.").assertExists()
        assertEquals(0, exports)
        compose.onNodeWithTag("everything_export_cancel").performClick()
        assertEquals(0, exports)
        assertEquals(1, dismisses)
        compose.onNodeWithTag("everything_export_confirm").performClick()
        assertEquals(1, exports)
    }

    @Test fun `busy export cannot be repeated or dismissed`() {
        var exports = 0
        var dismisses = 0
        compose.setContent { SplitFlowTheme {
            EverythingBackupExportDialog(true, true, { exports++ }, { dismisses++ })
        } }
        compose.onNodeWithTag("everything_export_progress").assertExists()
        compose.onNodeWithTag("everything_export_confirm").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("everything_export_cancel").assertIsNotEnabled().performClick()
        assertEquals(0, exports)
        assertEquals(0, dismisses)
    }
}
