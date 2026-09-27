package com.vaibhav.kharchapaani.ui.components

import com.vaibhav.kharchapaani.ui.theme.LocalMyColour
import androidx.compose.foundation.background
import kotlin.math.roundToInt
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.BarTrack
import com.vaibhav.kharchapaani.domain.AI_DAILY_CAP
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.domain.AskState
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.domain.MemberShare
import com.vaibhav.kharchapaani.domain.GroupFeed
import com.vaibhav.kharchapaani.domain.GroupFeedEntry
import com.vaibhav.kharchapaani.domain.GroupInsights
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt
import com.vaibhav.kharchapaani.data.model.CategorySummary
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.domain.GroupExpenseCredit
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyOut
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.components.KpButton
import com.vaibhav.kharchapaani.ui.components.KpButtonTier
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
    onSettleUpWith: (personId: Long) -> Unit = {},
    onExportGroup: (groupId: Long) -> Unit = {},
    onAskTripReview: (groupId: Long) -> Unit = {},
    tripReview: AskState = AskState.Idle,
    aiAsksLeft: Int = AI_DAILY_CAP,
    aiAvailable: Boolean = true,
) {
    if (groupDetails == null) return
    KpModalSheet(onDismiss = onDismiss) {
        // Every argument named and forwarded. Four of them used to stop here: the sheet took
        // onAskTripReview, tripReview, aiAsksLeft and aiAvailable and passed none of them on, so
        // the content fell back to its defaults — and the default for onAskTripReview is `{}`.
        // The button drew, enabled, at full contrast, and ran an empty lambda. That is the whole
        // of "the trip AI doesn't work at all, it isn't even clickable": it was wired to nothing.
        // Defaults on a composable's callbacks make this invisible to the compiler, so the rule
        // here is that a forwarding call names every parameter rather than relying on any of them.
        GroupDetailContent(
            groupDetails = groupDetails,
            onAddExpense = onAddExpense,
            onEditTransaction = onEditTransaction,
            sharedGroupPanel = sharedGroupPanel,
            onSettleUpWith = onSettleUpWith,
            onExportGroup = onExportGroup,
            onAskTripReview = onAskTripReview,
            tripReview = tripReview,
            aiAsksLeft = aiAsksLeft,
            aiAvailable = aiAvailable,
        )
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
    onSettleUpWith: (personId: Long) -> Unit = {},
    onExportGroup: (groupId: Long) -> Unit = {},
    onAskTripReview: (groupId: Long) -> Unit = {},
    tripReview: AskState = AskState.Idle,
    aiAsksLeft: Int = AI_DAILY_CAP,
    aiAvailable: Boolean = true,
) {
    val group = groupDetails.group
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val accent = remember(group.colorHex) { parseColor(group.colorHex) }
    val net = groupDetails.netBalanceForMe
    val myColour = LocalMyColour.current.hex
    val shares = remember(groupDetails, myColour) { GroupInsights.memberShares(groupDetails.transactions, groupDetails.members, myColor = myColour) }
    val debts = remember(groupDetails) { GroupInsights.owesWhom(groupDetails.transactions, groupDetails.settlements, groupDetails.members) }
    val categories = remember(groupDetails) { GroupInsights.categoryTotals(groupDetails.transactions) }

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

        if (group.isArchived) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(KpShapes.medium)
                    .background(WarmAmberLight.copy(alpha = 0.12f))
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .padding(Spacing.m)
                    .testTag("group_archived_banner"),
            ) {
                Icon(KpIcons.Statement, contentDescription = null, tint = WarmAmberLight, modifier = Modifier.size(18.dp))
                Text(
                    "Archived. Its history stays, but nothing new can be added or changed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmAmberLight,
                    modifier = Modifier.padding(start = Spacing.s),
                )
            }
            Spacer(Modifier.height(Spacing.l))
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
                color = if (net >= 0.005) MoneyIn else if (net <= -0.005) MoneyOut else MutedWarmGray,
                modifier = Modifier.weight(1f),
            )
        }

        if (group.isShared) {
            Box(Modifier.padding(top = Spacing.xl)) { sharedGroupPanel(groupDetails) }
        }

        if (shares.size > 1) {
            SectionTitle("Who spent what", top = Spacing.xxl)
            MemberShareDonut(shares, Modifier.padding(top = Spacing.s))
        }

        // The trip read lives here rather than on Insights: it is about a group, and asking it from
        // Insights would need a group picker for no reason (owner decision, 2026-09-19).
        if (aiAvailable && groupDetails.transactionsCount > 0) {
            SectionTitle("How did this trip go?", top = Spacing.xxl)
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.s)
                    .clip(KpShapes.large)
                    .background(CharcoalCard)
                    .padding(Spacing.l)
                    .testTag("trip_review_card"),
            ) {
                AskAnswer(
                    asked = AiPrompt.TripReview,
                    state = tripReview,
                    hasSpending = true,
                    asksLeft = aiAsksLeft,
                    onRetry = { onAskTripReview(group.id) },
                    // The Insights wording told you to pick a question, above a card with no
                    // questions on it and a single button.
                    idleMessage = "A read of the whole group: who carried it, what it cost, how it went.",
                    failure = (tripReview as? AskState.Failed)?.reason,
                )
                if (tripReview !is AskState.Answered && tripReview !is AskState.Asking) {
                    KpButton(
                        label = "Read this trip",
                        onClick = { onAskTripReview(group.id) },
                        // Outline, not Ghost: Ghost's fill is CharcoalElevated, which against the
                        // CharcoalCard this button sits on is very nearly the same colour, leaving
                        // it with no visible edge at all. It read as a caption rather than a
                        // button, which is why it was reported as "not even clickable".
                        tier = KpButtonTier.Outline,
                        enabled = aiAsksLeft > 0,
                        testTag = "trip_review_button",
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
            }
        }

        if (debts.isNotEmpty()) {
            SectionTitle("Who owes whom", top = Spacing.xxl)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                debts.forEachIndexed { index, debt ->
                    DebtRow(
                        debt = debt,
                        position = RowPosition.of(index, debts.size),
                        // Settling is between this account and one person, so only rows that include me.
                        onSettleUp = when {
                            group.isArchived -> null
                            debt.fromPersonId == GroupInsights.ME -> ({ onSettleUpWith(debt.toPersonId) })
                            debt.toPersonId == GroupInsights.ME -> ({ onSettleUpWith(debt.fromPersonId) })
                            else -> null
                        },
                    )
                }
            }
        }

        if (categories.isNotEmpty()) {
            SectionTitle("Where it went", top = Spacing.xxl)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val largest = categories.maxOf { it.amount }.coerceAtLeast(0.01)
                categories.forEach { summary ->
                    GroupCategoryRow(summary = summary, fraction = (summary.amount / largest).toFloat())
                }
            }
        }

        // Expenses *and* settlements. The list used to render transactions alone, so a settlement
        // or an IOU recorded in this group appeared nowhere in it — while the balances at the top
        // counted it, which is the worst of both: the figures moved and the screen didn't say why.
        val feed = remember(groupDetails) { GroupFeed.of(groupDetails.transactions, groupDetails.settlements) }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl, bottom = Spacing.s)) {
            Text(
                text = "Activity · ${feed.size}",
                style = MaterialTheme.typography.titleSmall,
                color = OffWhiteText,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (groupDetails.transactions.isNotEmpty()) {
                TextButton(onClick = { onExportGroup(group.id) }, modifier = Modifier.testTag("export_group_button")) {
                    Text("Export", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                }
            }
        }
        if (feed.isEmpty()) {
            Text(
                text = if (group.isArchived) "Nothing was logged in this group." else "Nothing logged here yet. Add the first expense below.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            feed.forEachIndexed { index, entry ->
                val position = RowPosition.of(index, feed.size)
                when (entry) {
                    is GroupFeedEntry.Expense -> GroupExpenseRow(
                        expense = entry.transaction,
                        detail = "${dateFormat.format(Date(entry.date))} · ${GroupExpenseCredit.line(entry.transaction, groupDetails.members)}",
                        position = position,
                        // Whoever added it or paid for it can edit it (item 11).
                        onClick = if (entry.transaction.canEdit) ({ onEditTransaction(entry.transaction) }) else null,
                    )
                    is GroupFeedEntry.Movement -> GroupMovementRow(
                        settlement = entry.settlement,
                        members = groupDetails.members,
                        detail = dateFormat.format(Date(entry.date)),
                        position = position,
                    )
                }
            }
        }
    }
}

/** A settlement or IOU inside a group, in the same shape as the expense rows beside it. */
@Composable
private fun GroupMovementRow(
    settlement: SettlementEntity,
    members: List<PersonEntity>,
    detail: String,
    position: RowPosition,
) {
    val incoming = GroupFeed.isIncoming(settlement)
    val accent = if (incoming) ChartreuseSecondary else WarmAmberLight
    val title = remember(settlement, members) { GroupFeed.describe(settlement, members) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .testTag("group_settlement_${settlement.id}"),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(40.dp).clip(KpShapes.small).background(accent.copy(alpha = 0.14f)),
        ) {
            Icon(
                imageVector = if (settlement.isIou) KpIcons.Lend else KpIcons.Settle,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = listOfNotNull(detail, if (settlement.isIou) "IOU" else "Settlement", settlement.note.takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = (if (incoming) "+" else "-") + Money.format(settlement.amount),
            style = MoneyText.row,
            color = if (incoming) ChartreuseSecondary else OffWhiteText,
        )
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

@Composable
private fun SectionTitle(text: String, top: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = OffWhiteText,
        modifier = Modifier.padding(top = top, bottom = Spacing.s).semantics { heading() },
    )
}

/**
 * Each member's share of the group's spending. A donut earns its place here, where the parts are of
 * one whole and there are only a few: the ring shows the split at a glance and the rows carry the
 * numbers. Colour follows the person, the same colour as their avatar everywhere else.
 */
@Composable
private fun MemberShareDonut(shares: List<MemberShare>, modifier: Modifier = Modifier) {
    // The ring's own total, so the centre always adds up to the slices around it.
    val total = shares.sumOf { it.amount }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l)
            .testTag("member_share_donut"),
    ) {
        val colors = shares.map { parseColor(it.colorHex) }
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 18.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                shares.forEachIndexed { index, share ->
                    val sweep = share.fraction * 360f
                    drawArc(
                        color = colors[index],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    // A hairline of the card between slices, so neighbouring colours stay apart.
                    drawArc(
                        color = CharcoalCard,
                        startAngle = start,
                        sweepAngle = 1f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPENT", style = EyebrowText, maxLines = 1)
                Text(Money.format(total), style = MoneyText.small, color = OffWhiteText, maxLines = 1)
            }
        }
        Column(Modifier.weight(1f).padding(start = Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            shares.forEachIndexed { index, share ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(colors[index]))
                    Text(
                        text = share.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OffWhiteText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = Spacing.s),
                    )
                    Text("${(share.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = Spacing.s))
                    Text(Money.format(share.amount), style = MoneyText.small, color = OffWhiteText)
                }
            }
        }
    }
}

@Composable
private fun DebtRow(debt: SimplifiedDebt, position: RowPosition, onSettleUp: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(start = Spacing.l, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)
            .testTag("debt_${debt.fromPersonId}_${debt.toPersonId}"),
    ) {
        Text(
            text = "${debt.fromPersonName} → ${debt.toPersonName}",
            style = MaterialTheme.typography.bodyLarge,
            color = OffWhiteText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = Spacing.m),
        )
        Text(Money.format(debt.amount), style = MoneyText.row, color = OffWhiteText)
        if (onSettleUp != null) {
            // The most consequential thing on this screen — a real debt between two named people,
            // in an exact amount — and it used to look the same as an inline link.
            KpButton(
                label = "Settle up",
                onClick = onSettleUp,
                tier = KpButtonTier.Outline,
                testTag = "settle_${debt.fromPersonId}_${debt.toPersonId}",
                modifier = Modifier.padding(start = Spacing.s),
            )
        }
    }
}

@Composable
private fun GroupCategoryRow(summary: CategorySummary, fraction: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(KpShapes.medium)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .testTag("group_category_${summary.category}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(summary.category)
            Text(
                text = summary.category,
                style = MaterialTheme.typography.bodyLarge,
                color = OffWhiteText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = Spacing.m),
            )
            Text(Money.format(summary.amount), style = MoneyText.row, color = OffWhiteText)
        }
        // One hue for a ranked list: the bars compare sizes, the badge already says which category.
        Canvas(Modifier.padding(top = Spacing.s).fillMaxWidth().height(6.dp)) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(color = BarTrack, cornerRadius = radius)
            drawRoundRect(
                color = CategoryHelper.getCategoryColor(summary.category),
                size = Size((size.width * fraction).coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
    }
}
