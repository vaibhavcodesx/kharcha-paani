package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText

/** A removal asks once, explains what changes, and leaves the record untouched on dismissal. */
@Composable
internal fun RemovalConfirmationDialog(
    title: String,
    description: String,
    confirmLabel: String,
    dialogTag: String,
    confirmTag: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        modifier = Modifier.testTag(dialogTag),
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = { Text(description, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(confirmTag)) {
                Text(confirmLabel, style = MaterialTheme.typography.labelLarge, color = DestructiveText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.controls_cancel), style = MaterialTheme.typography.labelLarge, color = MutedWarmGray)
            }
        },
        titleContentColor = OffWhiteText,
    )
}
