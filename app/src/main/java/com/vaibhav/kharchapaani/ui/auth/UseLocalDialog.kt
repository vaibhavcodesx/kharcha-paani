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
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText

/**
 * Asked before "Use a local ledger instead" switches ledgers (UX-31): one tap used to swap the whole
 * screen for a different, separate ledger. It says what will and will not happen, and the switch only
 * runs on the second tap.
 */
@Composable
fun UseLocalDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        modifier = Modifier.testTag("use_local_dialog"),
        title = { Text(stringResource(R.string.guest_use_local_confirm_title), style = MaterialTheme.typography.headlineMedium) },
        text = { Text(stringResource(R.string.guest_use_local_confirm_body), style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("use_local_confirm")) {
                Text(stringResource(R.string.guest_use_local_confirm), style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("use_local_cancel")) {
                Text(stringResource(R.string.guest_use_local_cancel), style = MaterialTheme.typography.labelLarge, color = MutedWarmGray)
            }
        },
        titleContentColor = OffWhiteText,
    )
}
