package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import kotlin.math.abs

/**
 * Confirming a friend's removal. A destructive, irreversible step, so a dialog (unlike the task
 * sheets), and it says what actually happens, per LedgerRepository.deletePerson: settlements and
 * IOUs with them go; splits with only them become your own expense at what you spent.
 */
@Composable
fun DeleteFriendDialog(
    person: PersonEntity?,
    currentBalance: Double,
    isOpen: Boolean,
    onConfirmDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen || person == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        modifier = Modifier.testTag("delete_friend_dialog"),
        title = { Text("Delete ${person.name}?", style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                if (abs(currentBalance) >= 0.005) {
                    Text(
                        text = if (currentBalance > 0) {
                            "${person.name} still owes you ${Money.format(currentBalance)}. That balance goes too."
                        } else {
                            "You still owe ${person.name} ${Money.format(abs(currentBalance))}. That balance goes too."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = WarmAmberLight,
                    )
                }
                Text(
                    "Settlements and IOUs with them are removed. Expenses you split only with them stay in your history as your own, at what you spent.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmDelete(person.id)
                    onDismiss()
                },
                modifier = Modifier.testTag("confirm_delete_friend_button"),
            ) {
                Text("Delete", style = MaterialTheme.typography.labelLarge, color = DestructiveText)
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

// A light red for text on the dark surface (7.9:1); the swipe reveal's #C62F2F is a fill, too dark for text.
internal val DestructiveText = Color(0xFFFF8A80)
