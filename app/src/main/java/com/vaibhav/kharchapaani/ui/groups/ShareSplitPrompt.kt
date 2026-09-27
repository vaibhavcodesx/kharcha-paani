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
        title = { Text(if (options.canSync) "Share this split?" else "This split won't sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = when {
                        options.canSync && options.guests.isNotEmpty() -> {
                            val names = options.guests.joinToString { it.name }
                            "Splits reach the other people only inside a shared group. $names " +
                                "${if (options.guests.size == 1) "isn't" else "aren't"} on Kharcha Paani, so they come along as your " +
                                "${if (options.guests.size == 1) "guest" else "guests"}: you cover their share in the group, and they owe it to you. Add it to:"
                        }
                        options.canSync -> "Splits reach the other people only inside a shared group. Add it to:"
                        options.withoutAccount.isNotEmpty() ->
                            "$noAccount ${if (options.withoutAccount.size == 1) "isn't" else "aren't"} on Kharcha Paani, " +
                                "so a split including them can only stay on this phone."
                        else -> "You aren't in an active shared group with everyone in this split. " +
                            "Create one and invite them to keep splits in sync."
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
                            Text("${group.emoji} ${group.name}", color = OffWhiteText, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (options.withoutAccount.isEmpty()) {
                    TextButton(onClick = onCreateGroup) {
                        Text("Create a shared group", color = WarmAmberPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onKeepLocal) { Text("Save on this phone only", color = MutedWarmGray) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Back", color = MutedWarmGray) }
        },
    )
}
