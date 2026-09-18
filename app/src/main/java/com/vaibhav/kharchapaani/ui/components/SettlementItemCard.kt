package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.perforation
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A settlement or IOU in the Home timeline. Money that came to you is green; money you paid or lent
 * is plain; a settlement between two groupmates is muted. Only a party can retract one, so others
 * get no menu; the row isn't tappable for them at all.
 */
@Composable
fun SettlementItemCard(
    item: ActivityItem.Settlement,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
) {
    val settlement = item.settlement
    var showMenu by remember { mutableStateOf(false) }
    val outgoing = settlement.fromPersonId == ME
    val incoming = settlement.toPersonId == ME
    val time = remember(settlement.date) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(settlement.date)) }
    val detail = listOfNotNull(
        time,
        if (settlement.isIou) "IOU" else "Settlement",
        item.groupName,
        settlement.note.takeIf { it.isNotBlank() },
    ).joinToString(" · ")

    val accent = if (incoming) ChartreuseSecondary else WarmAmberLight

    // The row itself opens the menu, so its amount lines up with every other row's instead of
    // sitting left of a ⋯ button.
    // The ticket motif, extended from the group cards to the other place money changes hands
    // (backlog item 17). Deliberately not the dock, which is navigation, not a receipt.
    Box(modifier.fillMaxWidth().clip(KpShapes.ticket)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .background(CharcoalCard)
                .perforation(fraction = 0.82f)
                .then(
                    if (item.involvesMe) {
                        Modifier.clickable(onClickLabel = "More options for ${item.title}") { showMenu = true }
                    } else {
                        Modifier.semantics(mergeDescendants = true) {}
                    }
                )
                .padding(horizontal = Spacing.l, vertical = Spacing.m)
                .testTag("settlement_item_${settlement.id}"),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(KpShapes.small)
                    .background(accent.copy(alpha = 0.14f)),
            ) {
                Icon(
                    imageVector = if (settlement.isIou) KpIcons.Lend else KpIcons.Settle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = (if (outgoing) "-" else if (incoming) "+" else "") + Money.format(settlement.amount),
                style = MoneyText.row,
                color = when {
                    incoming -> ChartreuseSecondary
                    outgoing -> OffWhiteText
                    else -> MutedWarmGray
                },
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
                text = { Text(if (settlement.isIou) "Delete IOU" else "Retract settlement", color = OffWhiteText) },
                onClick = {
                    showMenu = false
                    onRetract()
                },
            )
        }
    }
}

private const val ME = -1L
