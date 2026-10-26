package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.vaibhav.kharchapaani.ui.components.DestructiveText
import com.vaibhav.kharchapaani.ui.components.GlassActionBar
import com.vaibhav.kharchapaani.ui.components.KpModalSheet
import com.vaibhav.kharchapaani.ui.components.SheetScaffold
import com.vaibhav.kharchapaani.ui.components.SheetTitle
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * "Delete my account" (plan T1.1, decision D1), in two steps. The sheet says what goes and what
 * stays and asks the one question D1 leaves to the person: how they appear on past group expenses,
 * "Former member" unless they choose otherwise. Then a dialog asks once more, because nothing brings
 * the account back. With changes still waiting to sync, it says so first: those would be lost.
 */
@Composable
fun DeleteAccountSheet(
    isOpen: Boolean,
    unsyncedChanges: Int,
    isDeleting: Boolean,
    error: String?,
    onSyncNow: () -> Unit,
    onDelete: (keepName: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) {
        DeleteAccountContent(unsyncedChanges, isDeleting, error, onSyncNow, onDelete, onDismiss)
    }
}

@Composable
fun DeleteAccountContent(
    unsyncedChanges: Int,
    isDeleting: Boolean,
    error: String?,
    onSyncNow: () -> Unit,
    onDelete: (keepName: Boolean) -> Unit,
    onKeepAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var keepName by rememberSaveable { mutableStateOf(false) }
    var unsyncedAccepted by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val blockedByUnsynced = unsyncedChanges > 0 && !unsyncedAccepted

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            containerColor = CharcoalSurface,
            shape = KpShapes.large,
            title = { Text("Delete your account for good?", style = MaterialTheme.typography.headlineMedium) },
            text = { Text("This can't be undone. You'll be signed out, and this phone keeps nothing of the account.", style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete(keepName)
                    },
                    modifier = Modifier.testTag("delete_account_confirm"),
                ) {
                    Text("Delete my account", style = MaterialTheme.typography.labelLarge, color = DestructiveText)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text("Keep it", style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
                }
            },
            titleContentColor = OffWhiteText,
        )
    }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (blockedByUnsynced) "Discard them and continue" else "Continue",
                enabled = !isDeleting,
                busy = isDeleting,
                onClick = { if (blockedByUnsynced) unsyncedAccepted = true else confirming = true },
                buttonTestTag = "delete_account_continue",
            )
        },
    ) {
        SheetTitle(title = "Delete your account?", supporting = "Here's what happens.")

        Text("GOES", style = EyebrowText, modifier = Modifier.padding(top = Spacing.s))
        Text(
            "Your sign-in, your own expenses and income, splits and IOUs with friends who aren't on the app, and " +
                "your profile: picture, emoji, colour and UPI ID. Bill photos and everything else on this phone go too.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text("STAYS", style = EyebrowText, modifier = Modifier.padding(top = Spacing.l))
        Text(
            "Shared expenses and payments stay in your groups, so nobody's balance changes. Groups you made pass " +
                "to the person who's been in them longest.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.xs),
        )

        Text("ON PAST GROUP EXPENSES, SHOW ME AS", style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl))
        Column(Modifier.selectableGroup().padding(top = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            NameChoice("Former member", "Nothing of you stays but that", selected = !keepName, onSelect = { keepName = false }, tag = "delete_account_former_member")
            NameChoice("My name, as it is now", "Only the name; nothing else about you", selected = keepName, onSelect = { keepName = true }, tag = "delete_account_keep_name")
        }

        if (unsyncedChanges > 0) {
            Row(Modifier.padding(top = Spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (unsyncedChanges == 1) "1 change hasn't synced yet. Sync first, or it's lost with the account."
                    else "$unsyncedChanges changes haven't synced yet. Sync first, or they're lost with the account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmAmberLight,
                    modifier = Modifier.weight(1f).testTag("delete_account_unsynced"),
                )
                TextButton(onClick = onSyncNow, modifier = Modifier.testTag("delete_account_sync_now")) {
                    Text("Sync now", style = MaterialTheme.typography.labelLarge, color = WarmAmberPrimary)
                }
            }
        }

        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = WarmAmberLight,
                modifier = Modifier
                    .padding(top = Spacing.m)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("delete_account_error"),
            )
        }

        TextButton(onClick = onKeepAccount, modifier = Modifier.fillMaxWidth().padding(top = Spacing.m)) {
            Text("Keep my account", style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
        }
    }
}

@Composable
private fun NameChoice(title: String, detail: String, selected: Boolean, onSelect: () -> Unit, tag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.xs)
            .testTag(tag),
    ) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = WarmAmberPrimary, unselectedColor = MutedWarmGray))
        Column(Modifier.padding(start = Spacing.s)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = OffWhiteText)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
