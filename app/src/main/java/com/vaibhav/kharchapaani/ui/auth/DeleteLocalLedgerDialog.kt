package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.DestructiveText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText

/**
 * Asked before this phone's guest ledger is deleted (UX-01): it is removed for good, so it says exactly
 * what goes, and that a backup is the way to keep it. Nothing happens until the second tap.
 */
@Composable
fun DeleteLocalLedgerDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        modifier = Modifier.testTag("delete_local_dialog"),
        title = { Text(stringResource(R.string.guest_delete_title), style = MaterialTheme.typography.headlineMedium) },
        text = { Text(stringResource(R.string.guest_delete_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("delete_local_confirm")) {
                Text(stringResource(R.string.guest_delete_confirm), style = MaterialTheme.typography.labelLarge, color = DestructiveText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("delete_local_cancel")) {
                Text(stringResource(R.string.guest_delete_cancel), style = MaterialTheme.typography.labelLarge, color = MutedWarmGray)
            }
        },
        titleContentColor = OffWhiteText,
    )
}
