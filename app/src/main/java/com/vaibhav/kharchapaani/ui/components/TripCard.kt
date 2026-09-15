package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.TicketShape
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import kotlin.math.abs

/**
 * A group as a ticket: who and what above the perforation, the money below. The two halves are the
 * same height, so the perforation meets the notches. Local groups can be deleted from a long-press
 * menu; shared ones are archived or left from their detail sheet, never deleted (decision 4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripCard(
    details: GroupWithDetails,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val group = details.group
    val owed = details.netBalanceForMe >= 0.005
    val owe = details.netBalanceForMe <= -0.005
    val canDelete = !group.isShared
    var showMenu by remember { mutableStateOf(false) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val accent = remember(group.colorHex) { parseColor(group.colorHex) }
    val meta = listOfNotNull(
        "${details.members.size + 1} people",
        "${details.transactionsCount} ${if (details.transactionsCount == 1) "expense" else "expenses"}",
        when {
            group.isArchived -> "Archived"
            group.isShared -> "Shared"
            else -> null
        },
    ).joinToString(" · ")

    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(Ticket)
                .background(CharcoalCard)
                .drawWithContent {
                    drawContent()
                    val y = size.height / 2
                    val inset = 18.dp.toPx()
                    drawLine(
                        color = CharcoalBorder,
                        start = Offset(inset, y),
                        end = Offset(size.width - inset, y),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
                    )
                }
                .combinedClickable(
                    onClickLabel = "Open ${group.name}",
                    onLongClickLabel = if (canDelete) "More options" else null,
                    onLongClick = if (canDelete) ({ showMenu = true }) else null,
                    onClick = onOpen,
                )
                .semantics {
                    if (canDelete) customActions = listOf(CustomAccessibilityAction("Delete ${group.name}") { currentOnDelete(); true })
                }
                .testTag("group_card_${group.id}"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(HalfHeight).padding(horizontal = Spacing.l),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(SquircleShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                ) {
                    Text(group.emoji, style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            owed -> "YOU'RE OWED"
                            owe -> "YOU OWE"
                            else -> "SETTLED"
                        },
                        style = EyebrowText,
                    )
                    Text(
                        text = Money.format(abs(details.netBalanceForMe)),
                        style = MoneyText.row,
                        color = when {
                            owed -> ChartreuseSecondary
                            owe -> OffWhiteText
                            else -> MutedWarmGray
                        },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                modifier = Modifier.fillMaxWidth().height(HalfHeight).padding(horizontal = Spacing.l),
            ) {
                Figure("Spent", Money.format(details.totalExpenses))
                Figure("Your share", Money.format(details.myShare))
                Box(Modifier.weight(1f))
                MemberStack(details)
            }
        }
        if (canDelete) {
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = CharcoalElevated,
                shape = KpShapes.medium,
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                DropdownMenuItem(
                    text = { Text("Delete ${group.name}", color = OffWhiteText) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = EyebrowText)
        Text(value, style = MoneyText.small, color = OffWhiteText)
    }
}

/** Me first, then up to three members, overlapping; a "+n" for the rest. */
@Composable
private fun MemberStack(details: GroupWithDetails) {
    val shown = details.members.take(3)
    val extra = details.members.size - shown.size
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(name = "Me", colorHex = "#FF7A3D", size = 30.dp, ringColor = CharcoalCard)
        shown.forEach { PersonAvatar(name = it.name, colorHex = it.avatarColor, size = 30.dp, ringColor = CharcoalCard) }
        if (extra > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(30.dp).clip(SquircleShape(10.dp)).background(CharcoalElevated),
            ) {
                Text("+$extra", style = MaterialTheme.typography.labelMedium, color = OffWhiteText)
            }
        }
    }
}

private val HalfHeight = 68.dp
private val Ticket = TicketShape(corner = 20.dp, notchRadius = 9.dp)
