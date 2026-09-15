package com.vaibhav.kharchapaani.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.domain.ActivityDay
import com.vaibhav.kharchapaani.domain.ActivityDays
import com.vaibhav.kharchapaani.domain.ActivityFeed
import com.vaibhav.kharchapaani.ui.components.RowPosition
import com.vaibhav.kharchapaani.ui.components.SettlementItemCard
import com.vaibhav.kharchapaani.ui.components.TransactionItemCard
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.home.BalanceHeader
import com.vaibhav.kharchapaani.ui.home.CaptureCard
import com.vaibhav.kharchapaani.ui.home.MonthStrip
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.shell.dockClearance
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    activity: List<ActivityItem>,
    totalNetOwed: Double,
    monthlyAnalytics: MonthlyAnalytics,
    isParsing: Boolean,
    searchQuery: String,
    selectedFilter: String,
    onSearchChange: (String) -> Unit,
    onFilterChange: (String) -> Unit,
    onParseText: (String) -> Unit,
    onParseImage: (Bitmap) -> Unit,
    onParsePdf: (ByteArray, String) -> Unit,
    onEditTransaction: (FullTransaction) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onRetractSettlement: (Long) -> Unit,
    onOpenIouDialog: () -> Unit,
    onOpenSimplifyDebts: () -> Unit,
    accountInitial: String,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    var showSearch by rememberSaveable { mutableStateOf(searchQuery.isNotEmpty()) }
    val days = remember(activity, today) { ActivityDays.group(activity, today, ZoneId.systemDefault()) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBg),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, top = Spacing.l, bottom = dockClearance()),
    ) {
        item(key = "balance") {
            BalanceHeader(
                netBalance = totalNetOwed,
                accountInitial = accountInitial,
                onOpenAccount = onOpenAccount,
                onOpenSimplifyDebts = onOpenSimplifyDebts,
            )
        }
        item(key = "month") { MonthStrip(monthlyAnalytics, Modifier.padding(top = Spacing.s)) }
        item(key = "capture") {
            CaptureCard(
                isParsing = isParsing,
                onParseText = onParseText,
                onParseImage = onParseImage,
                onParsePdf = onParsePdf,
                onOpenIou = onOpenIouDialog,
                modifier = Modifier.padding(top = Spacing.xl),
            )
        }
        item(key = "feed_header") {
            FeedHeader(
                showSearch = showSearch,
                onToggleSearch = {
                    if (showSearch) onSearchChange("")
                    showSearch = !showSearch
                },
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
                selectedFilter = selectedFilter,
                onFilterChange = onFilterChange,
                modifier = Modifier.padding(top = Spacing.xxl),
            )
        }

        if (days.isEmpty()) {
            item(key = "empty") {
                EmptyFeed(
                    searching = searchQuery.isNotBlank(),
                    filtered = selectedFilter != ActivityFeed.ALL,
                    modifier = Modifier.padding(top = Spacing.l),
                )
            }
        }
        days.forEach { day ->
            stickyHeader(key = "day_${day.date}") { DayHeader(day) }
            itemsIndexed(day.items, key = { _, item -> item.key }) { index, item ->
                val position = RowPosition.of(index, day.items.size)
                val rowModifier = Modifier
                    .animateItem()
                    .padding(top = if (index == 0) 0.dp else 2.dp)
                when (item) {
                    is ActivityItem.Transaction -> TransactionItemCard(
                        fullTx = item.fullTransaction,
                        isReadOnly = item.isReadOnly,
                        onEdit = { onEditTransaction(item.fullTransaction) },
                        onDelete = { onDeleteTransaction(item.fullTransaction.transaction.id) },
                        position = position,
                        modifier = rowModifier,
                    )
                    is ActivityItem.Settlement -> SettlementItemCard(
                        item = item,
                        onRetract = { onRetractSettlement(item.settlement.id) },
                        position = position,
                        modifier = rowModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedHeader(
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: String,
    onFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Activity",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onToggleSearch, modifier = Modifier.testTag("home_search_button")) {
                Icon(
                    imageVector = if (showSearch) KpIcons.Close else KpIcons.Search,
                    contentDescription = if (showSearch) "Close search" else "Search activity",
                    tint = if (showSearch) WarmAmberPrimary else MutedWarmGray,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        AnimatedVisibility(visible = showSearch, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(top = Spacing.s)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(KpShapes.medium)
                    .background(CharcoalCard)
                    .padding(horizontal = Spacing.l),
            ) {
                Icon(KpIcons.Search, contentDescription = null, tint = SubMutedWarmGray, modifier = Modifier.size(18.dp))
                Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                    if (searchQuery.isEmpty()) {
                        Text("Merchant, category or note", style = MaterialTheme.typography.bodyLarge, color = SubMutedWarmGray)
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OffWhiteText),
                        cursorBrush = SolidColor(WarmAmberPrimary),
                        modifier = Modifier.fillMaxWidth().testTag("search_activity_input"),
                    )
                }
            }
        }
        FilterRow(selected = selectedFilter, onSelect = onFilterChange, modifier = Modifier.padding(top = Spacing.m))
    }
}

@Composable
private fun FilterRow(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
    ) {
        listOf(ActivityFeed.ALL, ActivityFeed.EXPENSES, ActivityFeed.INCOME, ActivityFeed.SPLITS).forEach { filter ->
            val isSelected = filter == selected
            Text(
                text = filter,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) OffWhiteText else MutedWarmGray,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(KpShapes.small)
                    .background(if (isSelected) CharcoalElevated else CharcoalBg)
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(filter) })
                    .padding(horizontal = Spacing.l, vertical = Spacing.s + Spacing.xxs)
                    .testTag("filter_chip_$filter"),
            )
        }
    }
}

/** Sticks to the top while its day scrolls by; solid, since rows pass underneath it. */
@Composable
private fun DayHeader(day: ActivityDay) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .background(CharcoalBg)
            .padding(top = Spacing.xl, bottom = Spacing.s)
            .semantics(mergeDescendants = true) { heading() },
    ) {
        Text(day.label, style = MaterialTheme.typography.titleSmall, color = OffWhiteText, modifier = Modifier.weight(1f))
        if (day.spent > 0) {
            Text("${Money.format(day.spent)} spent", style = MoneyText.small)
        }
    }
}

@Composable
private fun EmptyFeed(searching: Boolean, filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.xl),
    ) {
        Text(
            text = when {
                searching -> "Nothing matches that"
                filtered -> "Nothing here yet"
                else -> "Your first entry goes here"
            },
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = when {
                searching -> "Try a merchant, a category like Food, or a word from a note."
                filtered -> "Switch back to All to see everything."
                else -> "Type what you spent above, like “chai 40 cash”, and it's logged with the category and payment filled in."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
