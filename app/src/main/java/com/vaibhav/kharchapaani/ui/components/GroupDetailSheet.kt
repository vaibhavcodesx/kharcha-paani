package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.domain.GroupExpenseCredit
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun GroupDetailSheet(
    groupDetails: GroupWithDetails?,
    onAddExpense: (groupId: Long) -> Unit,
    onEditTransaction: (FullTransaction) -> Unit,
    onDismiss: () -> Unit,
    sharedGroupPanel: @Composable (GroupWithDetails) -> Unit = {},
) {
    if (groupDetails == null) return
    KpModalSheet(onDismiss = onDismiss) {
        GroupDetailContent(groupDetails, onAddExpense, onEditTransaction, sharedGroupPanel)
    }
}

/**
 * A group: what it's spent and where you stand, the shared-group controls when it's shared, and its
 * expenses with who added and who paid each. Adding an expense floats in the glass bar; archived
 * groups are read-only (decision 4), so the bar says so instead.
 */
@Composable
fun GroupDetailContent(
    groupDetails: GroupWithDetails,
    onAddExpense: (groupId: Long) -> Unit,
    onEditTransaction: (FullTransaction) -> Unit,
    sharedGroupPanel: @Composable (GroupWithDetails) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val group = groupDetails.group
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val accent = remember(group.colorHex) { parseColor(group.colorHex) }
    val net = groupDetails.netBalanceForMe

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = "Add expense",
                disabledLabel = "Archived groups are read-only",
                enabled = !group.isArchived,
                onClick = { onAddExpense(group.id) },
                buttonTestTag = "add_group_expense_button",
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.l)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp).clip(SquircleShape(16.dp)).background(accent.copy(alpha = 0.18f)),
            ) {
                Text(group.emoji, style = MaterialTheme.typography.headlineLarge)
            }
            Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                Text(group.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                if (group.description.isNotBlank()) {
                    Text(group.description, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Figure("Spent", Money.format(groupDetails.totalExpenses), OffWhiteText, Modifier.weight(1f))
            Divider()
            Figure("Your share", Money.format(groupDetails.myShare), OffWhiteText, Modifier.weight(1f))
            Divider()
            Figure(
                label = when {
                    net >= 0.005 -> "You're owed"
                    net <= -0.005 -> "You owe"
                    else -> "Settled"
                },
                value = Money.format(abs(net)),
                color = if (net >= 0.005) ChartreuseSecondary else if (net <= -0.005) OffWhiteText else MutedWarmGray,
                modifier = Modifier.weight(1f),
            )
        }

        if (group.isShared) {
            Box(Modifier.padding(top = Spacing.xl)) { sharedGroupPanel(groupDetails) }
        }

        Text(
            text = "Expenses · ${groupDetails.transactions.size}",
            style = MaterialTheme.typography.titleSmall,
            color = OffWhiteText,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).semantics { heading() },
        )
        if (groupDetails.transactions.isEmpty()) {
            Text(
                text = if (group.isArchived) "Nothing was logged in this group." else "Nothing logged here yet. Add the first expense below.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            groupDetails.transactions.forEachIndexed { index, expense ->
                GroupExpenseRow(
                    expense = expense,
                    detail = "${dateFormat.format(Date(expense.transaction.date))} · ${GroupExpenseCredit.line(expense, groupDetails.members)}",
                    position = RowPosition.of(index, groupDetails.transactions.size),
                    // Whoever added it or paid for it can edit it (item 11).
                    onClick = if (expense.canEdit) ({ onEditTransaction(expense) }) else null,
                )
            }
        }
    }
}

@Composable
private fun GroupExpenseRow(expense: FullTransaction, detail: String, position: RowPosition, onClick: (() -> Unit)?) {
    val tx = expense.transaction
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Edit", onClick = onClick) else Modifier.semantics(mergeDescendants = true) {})
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .testTag("group_expense_${tx.id}"),
    ) {
        CategoryBadge(tx.category)
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(tx.merchant.ifBlank { tx.category }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(tx.amount), style = MoneyText.row, color = OffWhiteText)
            Text("yours ${Money.format(expense.myPersonalShare)}", style = MoneyText.small)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = EyebrowText, maxLines = 1)
        Text(value, style = MoneyText.title, color = color, maxLines = 1)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(horizontal = Spacing.m).width(1.dp).fillMaxHeight().background(CharcoalBorder))
}
