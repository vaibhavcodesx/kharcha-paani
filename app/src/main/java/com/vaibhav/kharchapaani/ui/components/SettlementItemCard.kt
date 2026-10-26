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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
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
 * get no menu. IOUs can still be expanded to read their full note and attribution.
 */
@Composable
fun SettlementItemCard(
    item: ActivityItem.Settlement,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
    // Opens the confirm / "that's not right" sheet for one a groupmate recorded (design item 10).
    onAnswer: () -> Unit = {},
) {
    val settlement = item.settlement
    var showMenu by remember { mutableStateOf(false) }
    var showDetails by remember(settlement.id) { mutableStateOf(false) }
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
    val settled = settlement.status == SettlementStatus.CONFIRMED
    val rejected = settlement.status == SettlementStatus.REJECTED
    // Pending or rejected ones never moved a balance, so their amount doesn't wear the money colours.
    val statusLine = item.statusLine

    // The row opens IOU details or payment actions, keeping its amount aligned with other rows
    // instead of sitting left of a ⋯ button.
    //
    // The ticket motif, extended from the group cards to the other place money changes hands
    // (backlog item 17). Deliberately not the dock, which is navigation, not a receipt.
    //
    // The notched edges stay; the perforation does not. It was drawn at 0.82 of the height while
    // the notches this shape cuts sit at 0.5, so the line met nothing at either end — it ran
    // across the card a few pixels under the subtitle with only padding beneath it, which is what
    // "looks oddly placed" was seeing. Moving it to 0.5 would not have fixed it either: a
    // perforation separates two bands of content and this row is one band, so on a TripCard it
    // tears a stub off and here it would cut straight through the text. The motif came across
    // without the layout that earns it (round 2, §7).
    Box(modifier.fillMaxWidth().clip(KpShapes.ticket)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .background(CharcoalCard)
                    .then(
                        if (settlement.isIou) {
                            Modifier.clickable(
                                role = Role.Button,
                                onClickLabel = if (showDetails) "Close IOU details" else "Show IOU details",
                            ) { showDetails = !showDetails }.semantics {
                                stateDescription = if (showDetails) "Expanded" else "Collapsed"
                            }
                        } else if (item.awaitsMyAnswer) {
                            Modifier.clickable(onClickLabel = "Confirm or say this isn't right") { onAnswer() }
                        } else if (item.involvesMe && (settled || item.recordedByMe)) {
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
                    if (statusLine != null) {
                        Text(
                            text = statusLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.awaitsMyAnswer) WarmAmberLight else MutedWarmGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("settlement_status_${settlement.id}"),
                        )
                    }
                }
                Text(
                    text = (if (outgoing) "-" else if (incoming) "+" else "") + Money.format(settlement.amount),
                    style = if (rejected) MoneyText.row.copy(textDecoration = TextDecoration.LineThrough) else MoneyText.row,
                    color = when {
                        !settled -> MutedWarmGray
                        incoming -> ChartreuseSecondary
                        outgoing -> OffWhiteText
                        else -> MutedWarmGray
                    },
                )
                if (settlement.isIou) {
                    Icon(
                        KpIcons.ChevronDown,
                        contentDescription = null,
                        tint = MutedWarmGray,
                        modifier = Modifier.padding(start = Spacing.s).size(18.dp).rotate(if (showDetails) 180f else 0f),
                    )
                }
            }
            if (settlement.isIou && showDetails) {
                IouDetails(item, onShowOptions = { showMenu = true }, onAnswer = onAnswer)
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            containerColor = CharcoalElevated,
            shape = KpShapes.medium,
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        when {
                            // Mine, not yet answered: nothing has counted, so it's withdrawn, not retracted.
                            settlement.status == SettlementStatus.PENDING -> "Withdraw"
                            rejected -> "Clear it away"
                            settlement.isIou -> "Delete IOU"
                            else -> "Retract settlement"
                        },
                        color = OffWhiteText,
                    )
                },
                onClick = {
                    showMenu = false
                    onRetract()
                },
            )
        }
    }
}

/** The complete stored note; an unknown recorder stays unknown, including older local entries. */
@Composable
private fun IouDetails(item: ActivityItem.Settlement, onShowOptions: () -> Unit, onAnswer: () -> Unit) {
    val settlement = item.settlement
    val fullDate = remember(settlement.date) {
        SimpleDateFormat("d MMM yyyy · h:mm a", Locale.getDefault()).format(Date(settlement.date))
    }
    val recorder = if (settlement.recordedByPersonId == ME) "you" else item.recordedByName
    Column(
        Modifier.fillMaxWidth().background(CharcoalCard).padding(horizontal = Spacing.l, vertical = Spacing.m)
            .testTag("iou_details_${settlement.id}"),
    ) {
        Text(fullDate, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("iou_date_${settlement.id}"))
        Text(
            if (recorder == null) "Recorder isn't available for this entry" else "Recorded by $recorder",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.xs),
        )
        Text(
            settlement.note.ifBlank { "No note added." },
            style = MaterialTheme.typography.bodyLarge,
            color = OffWhiteText,
            modifier = Modifier.padding(top = Spacing.m).testTag("iou_note_${settlement.id}"),
        )
        if (item.awaitsMyAnswer) {
            TextButton(onClick = onAnswer, modifier = Modifier.testTag("iou_answer_${settlement.id}")) {
                Text("Confirm or say this isn't right", color = WarmAmberLight)
            }
        } else if (item.involvesMe && (settlement.isConfirmed || item.recordedByMe)) {
            TextButton(onClick = onShowOptions, modifier = Modifier.testTag("iou_options_${settlement.id}")) {
                Text("More options", color = WarmAmberLight)
            }
        }
    }
}

private const val ME = -1L
