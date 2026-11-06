package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.domain.EverythingBackup
import com.vaibhav.kharchapaani.domain.validate
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

/** Preview only. The caller must recheck the local session and empty destination at commit time. */
@Composable
fun EverythingBackupDialog(
    backup: EverythingBackup?,
    isLocal: Boolean,
    isEmptyLedger: Boolean,
    isWorking: Boolean,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    hasReadError: Boolean = false,
) {
    if (backup == null && !isWorking && !hasReadError) return
    val validation = remember(backup) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(backup) {
        validation.value = withContext(Dispatchers.Default) {
            if (backup == null) false else try {
                backup.validate()
                true
            } catch (_: IllegalArgumentException) {
                false
            }
        }
    }
    val checking = backup != null && validation.value == null && !hasReadError
    val invalid = hasReadError || (backup != null && validation.value == false)
    val canRestore = validation.value == true && !invalid && isLocal && isEmptyLedger && !isWorking
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        modifier = Modifier.testTag("everything_restore_dialog"),
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        title = { Text(stringResource(R.string.everything_backup_restore_title), style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                if (invalid) {
                    Text(stringResource(R.string.everything_backup_read_error))
                } else if (backup != null) {
                    Text(stringResource(
                        R.string.everything_backup_counts,
                        backup.transactions.size, backup.people.size, backup.groups.size,
                        backup.members.size, backup.splits.size, backup.shares.size,
                        backup.settlements.size, backup.subscriptions.size,
                        backup.paymentAsks.size, backup.receipts.size,
                    ), color = WarmAmberLight)
                    Text(stringResource(R.string.everything_backup_settings_included))
                    Text(stringResource(R.string.everything_backup_offline_copy))
                    Text(stringResource(R.string.everything_backup_restore_scope))
                }
                if (!isLocal) Text(stringResource(R.string.everything_backup_requires_local))
                else if (!isEmptyLedger) Text(stringResource(R.string.everything_backup_requires_empty))
                if (isWorking || checking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("everything_backup_progress"))
                    Text(stringResource(R.string.everything_backup_restore_working))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canRestore,
                onClick = { if (canRestore) onRestore() },
                modifier = Modifier.testTag("everything_restore_confirm"),
            ) { Text(stringResource(R.string.everything_backup_restore)) }
        },
        dismissButton = {
            TextButton(
                enabled = !isWorking,
                onClick = { if (!isWorking) onDismiss() },
                modifier = Modifier.testTag("everything_restore_cancel"),
            ) { Text(stringResource(R.string.everything_backup_cancel), color = MutedWarmGray) }
        },
        titleContentColor = OffWhiteText,
        textContentColor = OffWhiteText,
    )
}

/** Warn before launching the document picker; export does not alter ledger rows. */
@Composable
fun EverythingBackupExportDialog(
    visible: Boolean,
    isWorking: Boolean,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        modifier = Modifier.testTag("everything_export_dialog"),
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        title = { Text(stringResource(R.string.everything_backup_export_title), style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
            ) {
                Text(stringResource(R.string.everything_backup_export_private))
                Text(stringResource(R.string.everything_backup_export_contents))
                Text(stringResource(R.string.everything_backup_export_scope))
                if (isWorking) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("everything_export_progress"))
                    Text(stringResource(R.string.everything_backup_export_working))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isWorking,
                onClick = { if (!isWorking) onExport() },
                modifier = Modifier.testTag("everything_export_confirm"),
            ) { Text(stringResource(R.string.everything_backup_export)) }
        },
        dismissButton = {
            TextButton(
                enabled = !isWorking,
                onClick = { if (!isWorking) onDismiss() },
                modifier = Modifier.testTag("everything_export_cancel"),
            ) { Text(stringResource(R.string.everything_backup_cancel), color = MutedWarmGray) }
        },
        titleContentColor = OffWhiteText,
        textContentColor = OffWhiteText,
    )
}
