package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettlementAnswerSheet(
    item: ActivityItem.Settlement?,
    onConfirm: () -> Unit,
    onReject: (note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (item == null) return
    KpModalSheet(onDismiss = onDismiss) { SettlementAnswerContent(item, onConfirm, onReject) }
}

/**
 * A groupmate says money changed hands with you (design item 10). Nothing counts until you answer:
 * confirm it and both balances move; say it isn't right and it stays on record, not counted, with
 * your note for them. There's no way to make it disappear from here, and no deadline.
 */
@Composable
fun SettlementAnswerContent(
    item: ActivityItem.Settlement,
    onConfirm: () -> Unit,
    onReject: (note: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settlement = item.settlement
    val who = item.otherPartyName ?: "They"
    val theyPaidMe = settlement.toPersonId == -1L
    var disputing by rememberSaveable(settlement.id) { mutableStateOf(false) }
    var note by rememberSaveable(settlement.id) { mutableStateOf("") }
    val day = SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(settlement.date))

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (disputing) "Send" else "Confirm",
                enabled = true,
                onClick = { if (disputing) onReject(note) else onConfirm() },
                buttonTestTag = if (disputing) "settlement_reject_send" else "settlement_confirm",
            )
        },
    ) {
        SheetTitle(
            eyebrow = "Waiting for you",
            title = when {
                settlement.isIou && theyPaidMe -> "$who says they lent you ${Money.format(settlement.amount)}"
                settlement.isIou -> "$who says you lent them ${Money.format(settlement.amount)}"
                theyPaidMe -> "$who says they paid you ${Money.format(settlement.amount)}"
                else -> "$who says you paid them ${Money.format(settlement.amount)}"
            },
            supporting = listOfNotNull(day, item.groupName, settlement.note.takeIf { it.isNotBlank() }).joinToString(" · "),
        )
        Text(
            if (disputing) {
                "It stays on record, marked not right, and doesn't count. $who sees your note and can fix it."
            } else {
                "Nothing moves until you answer. Confirm it and it counts for both of you."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (disputing) {
            SheetRow(label = "What's wrong", modifier = Modifier.padding(top = Spacing.l)) {
                SheetTextInput(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    placeholder = "Optional, like “it was ₹300”",
                    fieldTestTag = "settlement_reject_note",
                )
            }
        } else {
            KpButton(
                label = "That's not right",
                onClick = { disputing = true },
                tier = KpButtonTier.Outline,
                fillWidth = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.l),
                testTag = "settlement_reject",
            )
        }
    }
}
