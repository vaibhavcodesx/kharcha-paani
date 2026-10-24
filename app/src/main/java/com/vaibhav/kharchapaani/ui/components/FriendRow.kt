package com.vaibhav.kharchapaani.ui.components

import com.vaibhav.kharchapaani.ui.format.AskMessage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyOut
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.components.KpButton
import com.vaibhav.kharchapaani.ui.components.KpButtonTier
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import kotlin.math.abs

/**
 * One friend and where things stand with them. Tap for the history, the button to settle or record
 * a payment. Deleting is deliberately a step away (long-press, or TalkBack's actions) rather than a
 * bin icon on every row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FriendRow(
    balance: PersonBalance,
    onOpenHistory: () -> Unit,
    onSettleUp: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
    // Ask-to-pay: when you last asked them to pay you back, if they still owe you since.
    askedAt: Long? = null,
    now: Long = System.currentTimeMillis(),
) {
    val person = balance.person
    val owesMe = balance.netBalance >= 0.005
    val iOwe = balance.netBalance <= -0.005
    var showMenu by remember { mutableStateOf(false) }
    val currentOnSettleUp by rememberUpdatedState(onSettleUp)
    val currentOnDelete by rememberUpdatedState(onDelete)
    // A groupmate belongs to their groups' membership; the ledger won't delete them locally (it would
    // corrupt shared expenses), so the row doesn't pretend it can. They go when they leave.
    val canDelete = person.profileId == null
    val status = when {
        owesMe && askedAt != null -> "Owes you · ${AskMessage.askedLabel(askedAt, now)}"
        owesMe -> "Owes you"
        iOwe -> "You owe them"
        else -> "All settled"
    } + if (canDelete) "" else " · Shared"

    Box(modifier.fillMaxWidth().clip(position.shape)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .background(CharcoalCard)
                .combinedClickable(
                    onClickLabel = "See history with ${person.name}",
                    onLongClickLabel = if (canDelete) "More options" else null,
                    onLongClick = if (canDelete) ({ showMenu = true }) else null,
                    onClick = onOpenHistory,
                )
                .semantics {
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(if (owesMe || iOwe) "Settle up" else "Record a payment") { currentOnSettleUp(); true },
                        if (canDelete) CustomAccessibilityAction("Delete ${person.name}") { currentOnDelete(); true } else null,
                    )
                }
                .padding(start = Spacing.l, end = Spacing.s, top = Spacing.m, bottom = Spacing.m)
                .testTag("person_balance_card_${person.id}"),
        ) {
            PersonAvatar(name = person.name, colorHex = person.avatarColor, emoji = person.avatarEmoji)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text(person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (owesMe || iOwe) {
                Text(
                    text = Money.format(abs(balance.netBalance)),
                    style = MoneyText.row,
                    color = if (owesMe) MoneyIn else MoneyOut,
                )
            }
            // Every row has the button, so amounts line up whether or not there's anything to
            // settle — but settling a real debt and logging an IOU with someone already square are
            // not the same action, so they no longer carry the same weight.
            val settling = owesMe || iOwe
            KpButton(
                label = if (settling) "Settle" else "Record",
                onClick = onSettleUp,
                tier = if (settling) KpButtonTier.Outline else KpButtonTier.Ghost,
                onClickLabel = if (settling) "Settle up with ${person.name}" else "Record a payment with ${person.name}",
                testTag = "settle_person_${person.id}",
                modifier = Modifier.padding(start = Spacing.s).widthIn(min = 76.dp),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = CharcoalElevated,
            shape = KpShapes.medium,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            DropdownMenuItem(
                text = { Text("Delete ${person.name}", color = OffWhiteText) },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                modifier = Modifier.testTag("delete_person_${person.id}"),
            )
        }
    }
}
