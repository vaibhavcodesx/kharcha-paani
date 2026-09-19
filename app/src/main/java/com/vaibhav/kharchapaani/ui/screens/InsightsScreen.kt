package com.vaibhav.kharchapaani.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.domain.MonthInsights
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.CategoryBadge
import com.vaibhav.kharchapaani.ui.components.CategoryHelper
import com.vaibhav.kharchapaani.ui.components.MonthSwitcher
import com.vaibhav.kharchapaani.ui.components.RowPosition
import com.vaibhav.kharchapaani.ui.components.ScreenHeader
import com.vaibhav.kharchapaani.ui.components.pairedFigureStyle
import com.vaibhav.kharchapaani.ui.components.shimmerLoading
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.format.MonthNames
import com.vaibhav.kharchapaani.ui.shell.dockClearance
import com.vaibhav.kharchapaani.ui.components.AccountAvatar
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.components.AskSection
import com.vaibhav.kharchapaani.ui.components.EmptyState
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.domain.AskBoard
import com.vaibhav.kharchapaani.domain.AI_DAILY_CAP
import com.vaibhav.kharchapaani.ui.components.CategoryRing
import com.vaibhav.kharchapaani.ui.components.DailyFlowChart
import com.vaibhav.kharchapaani.domain.DailySpend
import com.vaibhav.kharchapaani.ui.components.KpButton
import com.vaibhav.kharchapaani.ui.components.KpButtonTier
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.BarTrack
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * One page for a month, this one unless another is picked: what went out against what came in,
 * where it went (ranked bars you can open), a couple of highlights, and an on-demand AI read. The
 * Charts / Notion table / AI tabs are gone; the table repeated Home's feed (a "filter by category"
 * view is on the backlog instead).
 */
@Composable
fun InsightsScreen(
    accountAvatar: String = "?",
    onOpenAccount: () -> Unit = {},
    syncStatus: SyncStatus = SyncStatus(),
    analytics: MonthlyAnalytics,
    transactions: List<FullTransaction> = emptyList(),
    people: List<PersonEntity> = emptyList(),
    askBoard: AskBoard = AskBoard(),
    onAsk: (AiPrompt) -> Unit,
    aiAsksLeft: Int = AI_DAILY_CAP,
    // No key in this build means the section isn't drawn at all: a button that can never work is
    // worse than no button.
    aiAvailable: Boolean = true,
    onExport: () -> Unit,
    onEditTransaction: ((FullTransaction) -> Unit)? = null,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
    month: YearMonth = YearMonth.from(today),
    earliestMonth: YearMonth = month,
    onMonthChange: (YearMonth) -> Unit = {},
) {
    val isThisMonth = month == YearMonth.from(today)
    // A category opened in one month closes when the month changes.
    var selected by rememberSaveable(month) { mutableStateOf<String?>(null) }
    var selectedDay by rememberSaveable(month) { mutableStateOf<Int?>(null) }
    val dailySpend = remember(transactions, month, today) {
        DailySpend.of(transactions, month, today, ZoneId.systemDefault())
    }
    val selectedEntries = remember(transactions, selected, month) {
        selected?.let { MonthInsights.categoryEntries(transactions, it, month, ZoneId.systemDefault()) }.orEmpty()
    }
    val possessive = MonthNames.possessive(month, today)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBg),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, top = Spacing.l, bottom = dockClearance()),
    ) {
        item(key = "header") {
            ScreenHeader(title = "Insights", supporting = "Where your money went, month by month") {
                KpButton(
                    label = "Export",
                    onClick = onExport,
                    tier = KpButtonTier.Ghost,
                    testTag = "export_csv_button",
                )
                AccountAvatar(avatar = accountAvatar, syncStatus = syncStatus, onClick = onOpenAccount)
            }
        }
        item(key = "month") {
            MonthSwitcher(
                month = month,
                earliest = earliestMonth,
                latest = YearMonth.from(today),
                onMonthChange = onMonthChange,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        item(key = "cashflow") {
            CashFlow(
                analytics = analytics,
                emptyText = if (isThisMonth) "Nothing logged this month yet." else "Nothing was logged in ${MonthNames.short(month, today)}.",
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
        item(key = "ring") {
            // Insights had no chart at all — only flat bars, while the app's one donut sat on the
            // group sheet. The ring and the list below share a selection, so they are two views of
            // one state rather than two controls.
            CategoryRing(
                categories = analytics.categorySummaries,
                total = analytics.totalExpense,
                selected = selected,
                onSelect = { selected = it },
                modifier = Modifier.padding(top = Spacing.xxl),
            )
        }
        item(key = "flow") {
            DailyFlowChart(
                days = dailySpend,
                selectedDay = selectedDay,
                onSelectDay = { selectedDay = it },
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
        item(key = "categories") {
            CategoryBreakdown(
                summaries = analytics.categorySummaries,
                selected = selected,
                onSelect = { selected = if (selected == it) null else it },
                entries = selectedEntries,
                onEditTransaction = onEditTransaction,
                possessive = possessive,
                emptyText = if (isThisMonth) {
                    "Log an expense and this fills in: where it went, day by day, and what stood out."
                } else {
                    "Nothing was spent in ${MonthNames.short(month, today)}."
                },
                modifier = Modifier.padding(top = Spacing.xxl),
            )
        }
        item(key = "highlights") {
            Highlights(
                analytics = analytics,
                perDay = MonthInsights.dailyAverage(analytics.totalExpense, month, today),
                perDayLabel = if (isThisMonth) "Per day so far" else "Per day",
                modifier = Modifier.padding(top = Spacing.xxl),
            )
        }
        if (aiAvailable) {
            item(key = "ask") {
                AskSection(
                    prompts = AiPrompt.forInsights,
                    board = askBoard,
                    hasSpending = analytics.totalExpense > 0,
                    asksLeft = aiAsksLeft,
                    possessive = possessive,
                    onAsk = onAsk,
                    modifier = Modifier.padding(top = Spacing.xxl),
                )
            }
        }
    }
}

/** Out against in, and what's left, as numbers rather than a chart: three values are a table, not a plot. */
@Composable
private fun CashFlow(analytics: MonthlyAnalytics, emptyText: String, modifier: Modifier = Modifier) {
    val left = analytics.netCashflow
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val style = pairedFigureStyle(analytics.totalExpense, analytics.totalIncome)
            Figure("Spent", analytics.totalExpense, OffWhiteText, style, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = Spacing.xl).width(1.dp).fillMaxHeight().background(CharcoalBorder))
            Figure("Came in", analytics.totalIncome, ChartreuseSecondary, style, Modifier.weight(1f))
        }
        Text(
            text = when {
                analytics.totalIncome <= 0 && analytics.totalExpense <= 0 -> emptyText
                left >= 0.005 -> "${Money.format(left)} more came in than went out."
                left <= -0.005 -> "${Money.format(abs(left))} more went out than came in."
                else -> "Exactly as much went out as came in."
            },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

@Composable
private fun Figure(label: String, amount: Double, color: Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = EyebrowText)
        AnimatedRollingNumber(targetValue = amount, style = style, color = color)
    }
}

/**
 * Where it went: one ranked bar per category, in one hue. Colour here would only repeat what the
 * label says, and the old per-category pastels had nothing to do with the brand. Opening a category
 * greys the other bars and lists what's behind its total.
 */
@Composable
private fun CategoryBreakdown(
    summaries: List<CategorySummary>,
    selected: String?,
    onSelect: (String) -> Unit,
    entries: List<FullTransaction>,
    onEditTransaction: ((FullTransaction) -> Unit)?,
    possessive: String,
    emptyText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            "By category",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() }.padding(bottom = Spacing.m),
        )
        if (summaries.isEmpty()) {
            EmptyState(
                illustration = R.drawable.kp_empty_insights,
                title = "Nothing to break down",
                body = emptyText,
            )
            return@Column
        }
        val largest = summaries.maxOf { it.amount }.coerceAtLeast(0.01)
        // Tapping a slice on the ring selects a row down here, and that row is very often below the
        // fold — so the chart responded and, from where you were looking, nothing happened. The
        // selected row now asks to be scrolled to; the request travels up through the page's own
        // scroller, so it works wherever the row happens to be.
        val bringSelectedIntoView = remember { BringIntoViewRequester() }
        LaunchedEffect(selected) {
            if (selected != null) bringSelectedIntoView.bringIntoView()
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            summaries.forEachIndexed { index, summary ->
                val isSelected = summary.category == selected
                CategoryBarRow(
                    summary = summary,
                    fraction = (summary.amount / largest).toFloat(),
                    emphasis = selected == null || isSelected,
                    isSelected = isSelected,
                    position = RowPosition.of(index, summaries.size),
                    possessive = possessive,
                    onClick = { onSelect(summary.category) },
                    modifier = if (isSelected) Modifier.bringIntoViewRequester(bringSelectedIntoView) else Modifier,
                )
                AnimatedVisibility(visible = isSelected, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    CategoryEntries(entries, onEditTransaction)
                }
            }
        }
    }
}

@Composable
private fun CategoryBarRow(
    summary: CategorySummary,
    fraction: Float,
    emphasis: Boolean,
    isSelected: Boolean,
    position: RowPosition,
    possessive: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grown by animateFloatAsState(fraction, animationSpec = tween(600), label = "bar_${summary.category}")
    // Each category's own colour, the same one its badge two inches to the left is already using.
    // One amber for all of them meant the badge carried the category and the bar carried nothing.
    val categoryColor = CategoryHelper.getCategoryColor(summary.category)
    val barColor = if (emphasis) categoryColor else categoryColor.copy(alpha = 0.35f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(position.shape)
            .background(if (isSelected) CharcoalElevated else CharcoalCard)
            .clickable(role = Role.Button, onClickLabel = if (isSelected) "Close ${summary.category}" else "Show ${summary.category} entries", onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.selected = isSelected
                stateDescription = "${summary.percentage.roundToInt()} percent of $possessive spending"
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.m)
            .testTag("category_row_${summary.category}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(summary.category)
            Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                Text(summary.category, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${summary.count} ${if (summary.count == 1) "entry" else "entries"} · ${summary.percentage.roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(Money.format(summary.amount), style = MoneyText.row, color = OffWhiteText)
        }
        // The bar: 8dp tall, rounded ends, on a recessive track; scaled to the largest category so the
        // top one fills the row and the rest read against it.
        Canvas(
            Modifier
                .padding(top = Spacing.s)
                .fillMaxWidth()
                .height(8.dp),
        ) {
            val radius = CornerRadius(size.height / 2)
            // CharcoalBg on a CharcoalCard row measured 1.2:1 — the empty part of the bar was
            // invisible, so a short bar read as a short line rather than as a small share.
            drawRoundRect(color = BarTrack, cornerRadius = radius)
            val width = (size.width * grown).coerceAtLeast(size.height)
            drawRoundRect(color = barColor, size = Size(width, size.height), cornerRadius = radius)
        }
    }
}

/** What's behind the selected total, newest first; tap one to open it. */
@Composable
private fun CategoryEntries(entries: List<FullTransaction>, onEditTransaction: ((FullTransaction) -> Unit)?) {
    val dateFormat = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
            .clip(KpShapes.medium)
            .background(CharcoalElevated)
            .padding(vertical = Spacing.xs),
    ) {
        if (entries.isEmpty()) {
            Text("No entries to show.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(Spacing.l))
        }
        entries.forEach { ft ->
            val tx = ft.transaction
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .then(if (onEditTransaction != null) Modifier.clickable(onClickLabel = "Open") { onEditTransaction(ft) } else Modifier)
                    .padding(horizontal = Spacing.l, vertical = Spacing.s),
            ) {
                Text(dateFormat.format(Date(tx.date)), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(tx.merchant.ifBlank { tx.category }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (ft.split != null) {
                        Text("Your share of ${Money.format(tx.amount)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(Money.format(ft.myPersonalShare), style = MoneyText.small, color = OffWhiteText)
            }
        }
    }
}

/** Two single numbers, so two stat tiles rather than a chart. */
@Composable
private fun Highlights(analytics: MonthlyAnalytics, perDay: Double, perDayLabel: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
        StatTile(
            label = "Spent most at",
            value = analytics.topMerchant.takeIf { analytics.totalExpense > 0 && it != "None" } ?: "—",
            modifier = Modifier.weight(1f),
        )
        StatTile(label = perDayLabel, value = Money.format(perDay), money = true, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier, money: Boolean = false) {
    Column(
        modifier
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.l),
    ) {
        Text(label.uppercase(), style = EyebrowText)
        Spacer(Modifier.height(Spacing.xs))
        Text(
            value,
            style = if (money) MoneyText.title else MaterialTheme.typography.titleMedium,
            color = OffWhiteText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

