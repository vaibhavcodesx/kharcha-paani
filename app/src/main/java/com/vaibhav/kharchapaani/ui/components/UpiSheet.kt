package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.UpiLink
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

@Composable
fun UpiSheet(
    isOpen: Boolean,
    saved: UpiPayee?,
    defaultName: String,
    onSave: (UpiPayee) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { UpiContent(saved, defaultName, onSave, onRemove) }
}

/**
 * Your UPI ID, so a friend settling up can scan or copy it. Saved on this phone and published to your
 * profile, which only you and people you share a group with can read (MainViewModel.saveUpi).
 */
@Composable
fun UpiContent(
    saved: UpiPayee?,
    defaultName: String,
    onSave: (UpiPayee) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var id by rememberSaveable(saved) { mutableStateOf(saved?.upiId.orEmpty()) }
    var name by rememberSaveable(saved) { mutableStateOf(saved?.name ?: defaultName) }
    val cleanId = UpiLink.cleanId(id)
    val cleanName = name.trim().takeIf { it.isNotEmpty() }
    val payee = if (cleanId != null && cleanName != null) UpiPayee(cleanId, cleanName) else null
    val changed = payee != null && payee != saved

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (saved == null) "Save UPI ID" else "Save changes",
                disabledLabel = when {
                    payee == null && id.isBlank() -> "Add your UPI ID"
                    cleanId == null -> "Check the UPI ID"
                    cleanName == null -> "Add a name"
                    else -> "Saved"
                },
                enabled = changed,
                onClick = { payee?.let(onSave) },
                buttonTestTag = "upi_save_button",
            )
        },
    ) {
        SheetTitle(
            title = "Get paid by UPI",
            supporting = "Friends settling up with you can scan your code or copy your ID. People in your shared groups can see it too.",
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetRow(label = "UPI ID", position = RowPosition.First) {
                SheetTextInput(
                    value = id,
                    onValueChange = { id = it },
                    placeholder = "name@bank",
                    keyboardType = KeyboardType.Email,
                    fieldTestTag = "upi_id_input",
                )
            }
            SheetRow(label = "Name", position = RowPosition.Last) {
                SheetTextInput(value = name, onValueChange = { name = it }, placeholder = "Shown in their UPI app", fieldTestTag = "upi_name_input")
            }
        }
        val idInvalid = id.isNotBlank() && cleanId == null
        Text(
            text = if (idInvalid) "That doesn't look like a UPI ID. It's usually yourname@bank, like priya@okaxis." else "Find it in your UPI app's profile.",
            style = MaterialTheme.typography.bodySmall,
            color = if (idInvalid) WarmAmberLight else SubMutedWarmGray,
            modifier = Modifier
                .padding(top = Spacing.s)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("upi_id_hint"),
        )

        if (payee != null) {
            UpiPaymentCard(payee = payee, modifier = Modifier.padding(top = Spacing.xl))
        }
        if (saved != null) {
            TextButton(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).testTag("upi_remove_button"),
            ) {
                Text("Remove UPI ID", style = MaterialTheme.typography.labelLarge, color = DestructiveText)
            }
        }
    }
}
