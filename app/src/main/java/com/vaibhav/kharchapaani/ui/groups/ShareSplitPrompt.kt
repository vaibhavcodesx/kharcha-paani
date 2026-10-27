package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * Shown when saving a split with groupmates outside a shared group: such a split never reaches
 * them (decision 1), so the person picks a group, creates one, or keeps it on this phone knowingly.
 */
@Composable
fun ShareSplitPrompt(
    options: SharedRecordGroups.Options?,
    onShareIn: (groupId: Long) -> Unit,
    onCreateGroup: () -> Unit,
    onKeepLocal: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (options == null) return
    val noAccount = options.withoutAccount.joinToString { it.name }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        title = { Text(if (options.canSync) stringResource(R.string.shared_group_copy_share_split) else stringResource(R.string.shared_group_copy_wont_sync)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        options.canSync && options.guests.isNotEmpty() -> {
                            val names = options.guests.joinToString { it.name }
                            pluralStringResource(R.plurals.shared_group_copy_guests, options.guests.size, names)
                        }
                        options.canSync -> stringResource(R.string.shared_group_copy_split_explanation)
                        options.withoutAccount.isNotEmpty() ->
                            pluralStringResource(R.plurals.shared_group_copy_no_account, options.withoutAccount.size, noAccount)
                        else -> stringResource(R.string.shared_group_copy_no_everyone)
                    },
                    style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray),
                )
                if (options.canSync) {
                    options.groups.forEach { group ->
                        OutlinedButton(
                            onClick = { onShareIn(group.id) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.shared_group_copy_group_label, group.emoji, group.name), color = OffWhiteText, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (options.withoutAccount.isEmpty()) {
                    TextButton(onClick = onCreateGroup) {
                        Text(stringResource(R.string.shared_group_copy_create), color = WarmAmberPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeepLocal) { Text(stringResource(R.string.shared_group_copy_keep_local), color = MutedWarmGray) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.shared_group_copy_back), color = MutedWarmGray) }
        },
    )
}
