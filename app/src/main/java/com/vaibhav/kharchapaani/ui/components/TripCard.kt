package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyOut
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.TicketShape
import com.vaibhav.kharchapaani.ui.theme.perforation
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import kotlin.math.abs

/**
 * A group as a ticket: who and what above the perforation, the money below. The two halves are the
 * same minimum height; large text uses a plain card so content can grow. Local groups can be deleted from a long-press
 * menu; shared ones are archived or left from their detail sheet, never deleted (decision 4).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun TripCard(
    details: GroupWithDetails,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val largeText = LocalDensity.current.fontScale > 1.5f
    val group = details.group
    val owed = details.netBalanceForMe >= 0.005
    val owe = details.netBalanceForMe <= -0.005
    val canDelete = !group.isShared
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember(group.id) { mutableStateOf(false) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val accent = remember(group.colorHex) { parseColor(group.colorHex) }
    // Whether the group is shared or archived is a *state*, not a count, and as a third item on
    // the end of the counts it was the one that got cut: "3 people · 14 expenses · Shar…". It sits
    // beside the name as a tag instead, where it reads better, has room, and matches the payment
    // pill the transaction rows now use.
    val state = when {
        group.isArchived -> "Archived"
        group.isShared -> "Shared"
        else -> null
    }
    val meta = "${details.members.size + 1} people · " +
        "${details.transactionsCount} ${if (details.transactionsCount == 1) "expense" else "expenses"}"

    if (canDelete && showDeleteConfirmation) {
        RemovalConfirmationDialog(
            title = "Delete ${group.name}?",
            description = "Expenses and settlements stay in your history, without this group. Its member list is removed. This can't be undone.",
            confirmLabel = "Delete group",
            dialogTag = "delete_group_dialog",
            confirmTag = "confirm_delete_group",
            onConfirm = {
                showDeleteConfirmation = false
                currentOnDelete()
            },
            onDismiss = { showDeleteConfirmation = false },
        )
    }

    Box(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // An archived group is finished business kept for the record, but it drew at
                // exactly the weight of a live one, so a list of three trips gave equal billing to
                // the one nobody is spending on. Dimming the whole card, tag included, says
                // "still here, no longer current" without a second treatment to learn.
                .alpha(if (group.isArchived) ARCHIVED_ALPHA else 1f)
                .clip(if (largeText) KpShapes.large else Ticket)
                .background(CharcoalCard)
                .then(if (largeText) Modifier else Modifier.perforation())
                .combinedClickable(
                    onClickLabel = "Open ${group.name}",
                    onLongClickLabel = if (canDelete) "More options" else null,
                    onLongClick = if (canDelete) ({ showMenu = true }) else null,
                    onClick = onOpen,
                )
                .semantics {
                    if (canDelete) customActions = listOf(CustomAccessibilityAction("Delete ${group.name}") { showDeleteConfirmation = true; true })
                }
                .testTag("group_card_${group.id}"),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().heightIn(min = HalfHeight).padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(44.dp).clip(SquircleShape(14.dp)).background(accent.copy(alpha = 0.18f)),
                ) {
                    Text(group.emoji, style = MaterialTheme.typography.headlineMedium)
                }
                Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = if (largeText) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        state?.let { StateTag(it, Modifier.padding(start = Spacing.s)) }
                    }
                    Text(meta, style = MaterialTheme.typography.bodySmall, maxLines = if (largeText) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                }
                Column(Modifier.widthIn(max = 160.dp), horizontalAlignment = Alignment.End) {
                    Text(
                        text = when {
                            owed -> "YOU'RE OWED"
                            owe -> "YOU OWE"
                            else -> "SETTLED"
                        },
                        style = EyebrowText,
                    )
                    GroupMoney(
                        text = Money.format(abs(details.netBalanceForMe)),
                        style = MoneyText.row,
                        color = when {
                            owed -> MoneyIn
                            owe -> MoneyOut
                            else -> MutedWarmGray
                        },
                    )
                }
            }
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                modifier = Modifier.fillMaxWidth().heightIn(min = HalfHeight).padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                Figure("Spent", Money.format(details.totalExpenses))
                Figure("Your share", Money.format(details.myShare))
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
                        showDeleteConfirmation = true
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
        GroupMoney(value, MoneyText.small, OffWhiteText)
    }
}


/** Keep every digit; an amount never uses an ellipsis, even in a narrow large-text card. */
@Composable
private fun GroupMoney(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color) {
    BasicText(
        text = text,
        style = style.copy(color = color, lineHeight = androidx.compose.ui.unit.TextUnit.Unspecified),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
    )
}

/** Up to four members, overlapping, and a "+n" for the rest. "People" already counts you. */
@Composable
private fun MemberStack(details: GroupWithDetails) {
    val shown = details.members.take(4)
    val extra = details.members.size - shown.size
    Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
        // One initial each: at 30dp, overlapped, a second letter ("RM") is half hidden by the next avatar.
        shown.forEach { PersonAvatar(name = it.name.trim().substringBefore(' '), colorHex = it.avatarColor, emoji = it.avatarEmoji, size = 30.dp, ringColor = CharcoalCard) }
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

/** Clearly recessed, still readable: an archived group is history, not hidden. */
private const val ARCHIVED_ALPHA = 0.55f

/** "Shared" or "Archived" beside a group's name: an outlined tag, the same quiet form as [PaymentPill]. */
@Composable
private fun StateTag(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        style = EyebrowText.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
        color = SubMutedWarmGray,
        maxLines = 1,
        modifier = modifier
            .clip(KpShapes.small)
            .border(1.dp, SubMutedWarmGray.copy(alpha = 0.35f), KpShapes.small)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}
