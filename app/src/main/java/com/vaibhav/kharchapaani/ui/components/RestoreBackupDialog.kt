package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.domain.LocalBackup
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What a picked backup holds, before any of it goes in. A restore only adds what's missing, but it
 * adds to a ledger in use, so it's a step to take on purpose, with a file you can see is the right one.
 */
@Composable
fun RestoreBackupDialog(
    backup: LocalBackup?,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    if (backup == null) return
    val taken = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
        .format(Instant.ofEpochMilli(backup.exportedAt).atZone(zone))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        modifier = Modifier.testTag("restore_backup_dialog"),
        title = { Text("Restore this backup?", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Taken $taken, from version ${backup.appVersion}.", style = MaterialTheme.typography.bodyMedium)
                Text(backup.summary().replaceFirstChar { it.uppercase() } + ".", style = MaterialTheme.typography.bodyLarge, color = WarmAmberLight)
                Text(
                    "Only what isn't on this phone already is added. Nothing here is changed or removed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onRestore, modifier = Modifier.testTag("confirm_restore_button")) {
                Text("Restore", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MutedWarmGray)
            }
        },
        titleContentColor = OffWhiteText,
    )
}
