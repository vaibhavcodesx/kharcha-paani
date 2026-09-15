package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.IconButton
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
 * get no menu.
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

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(start = Spacing.l, end = if (item.involvesMe) Spacing.xs else Spacing.l, top = Spacing.m, bottom = Spacing.m)
            .testTag("settlement_item_${settlement.id}"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(KpShapes.small)
                .background(CharcoalElevated),
        ) {
            Icon(
                imageVector = if (settlement.isIou) KpIcons.Lend else KpIcons.Settle,
                contentDescription = null,
                tint = if (incoming) ChartreuseSecondary else WarmAmberLight,
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
        if (item.involvesMe) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(KpIcons.More, contentDescription = "More options for ${item.title}", tint = MutedWarmGray, modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = CharcoalElevated, shape = KpShapes.medium) {
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
    }
}

private const val ME = -1L
