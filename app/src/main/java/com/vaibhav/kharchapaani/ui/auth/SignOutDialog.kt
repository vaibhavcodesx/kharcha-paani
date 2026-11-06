package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * Signing out, with the choice to leave nothing of the account on this phone (plan T2.6 part 3,
 * SEC L-2). Off by default: signing out normally keeps the ledger so signing back in is instant.
 * The choice is only offered when everything has synced, because removing changes that never
 * reached the server would lose them; with some waiting, it says so instead.
 */
@Composable
fun SignOutDialog(
    isOpen: Boolean,
    unsyncedChanges: Int,
    onSignOut: (removeData: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return
    var removeData by rememberSaveable { mutableStateOf(false) }
    val canRemove = unsyncedChanges == 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        modifier = Modifier.testTag("sign_out_dialog"),
        title = { Text(stringResource(R.string.sign_out_title), style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text(stringResource(R.string.sign_out_body), style = MaterialTheme.typography.bodyMedium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = removeData && canRemove, enabled = canRemove, role = Role.Checkbox) { removeData = it }
                        .padding(vertical = Spacing.xs)
                        .testTag("sign_out_remove_data"),
                ) {
                    Checkbox(
                        checked = removeData && canRemove,
                        onCheckedChange = null,
                        enabled = canRemove,
                        colors = CheckboxDefaults.colors(checkedColor = WarmAmberPrimary, uncheckedColor = MutedWarmGray),
                    )
                    Text(
                        stringResource(R.string.sign_out_remove_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (canRemove) OffWhiteText else SubMutedWarmGray,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
                if (!canRemove) {
                    Text(
                        pluralStringResource(R.plurals.sign_out_unsynced, unsyncedChanges, unsyncedChanges),
                        style = MaterialTheme.typography.bodySmall,
                        color = WarmAmberLight,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSignOut(removeData && canRemove) },
                modifier = Modifier.testTag("confirm_sign_out_button"),
            ) { Text(stringResource(R.string.sign_out_confirm), color = WarmAmberLight) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.sign_out_cancel), color = OffWhiteText) }
        },
    )
}
