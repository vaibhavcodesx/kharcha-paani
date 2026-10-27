package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

@Composable
fun StatementImportDialog(
    parseResult: StatementParseResult,
    onConfirmImport: (List<StatementTransactionItem>) -> Unit,
    onDismiss: () -> Unit,
    prefs: CategoryPrefs = CategoryPrefs(),
) {
    KpModalSheet(onDismiss = onDismiss) { StatementImportContent(parseResult, onConfirmImport, prefs = prefs) }
}

/**
 * What was read from a bank or UPI statement, grouped by month, with everything ticked. Untick what
 * shouldn't come in, fix a category, and import. Nothing is added until Import.
 */
@Composable
fun StatementImportContent(
    parseResult: StatementParseResult,
    onConfirmImport: (List<StatementTransactionItem>) -> Unit,
    modifier: Modifier = Modifier,
    prefs: CategoryPrefs = CategoryPrefs(),
) {
    var items by remember(parseResult) { mutableStateOf(parseResult.allItems) }
    val expanded = remember(parseResult) {
        mutableStateMapOf<String, Boolean>().apply { parseResult.monthGroups.forEachIndexed { i, g -> put(g.monthSortKey, i == 0) } }
    }
    val selected = items.filter { it.isSelected }
    val spent = selected.filter { it.type == "expense" }.sumOf { it.amount }
    val cameIn = selected.filter { it.type == "income" }.sumOf { it.amount }
    val byMonth = remember(items) { items.groupBy { it.monthSortKey } }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = pluralStringResource(R.plurals.import_entry_action, selected.size, selected.size),
                disabledLabel = stringResource(R.string.import_tick_entries),
                enabled = selected.isNotEmpty(),
                onClick = { onConfirmImport(selected) },
                summary = stringResource(R.string.import_selection_count, selected.size, items.size),
                detail = stringResource(R.string.import_selected),
                buttonTestTag = "confirm_import_statement_button",
            )
        },
    ) {
        SheetTitle(
            eyebrow = stringResource(R.string.import_from_statement),
            title = parseResult.bankOrApp,
            supporting = parseResult.statementPeriod.takeIf { it.isNotBlank() }
                ?: pluralStringResource(R.plurals.import_entries_found, items.size, items.size),
        )

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val style = pairedFigureStyle(spent, cameIn)
            Figure(stringResource(R.string.import_spent), Money.format(spent), OffWhiteText, style, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = Spacing.xl).width(1.dp).fillMaxHeight().background(CharcoalBorder))
            Figure(stringResource(R.string.import_came_in), Money.format(cameIn), ChartreuseSecondary, style, Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xl)) {
            Text(stringResource(R.string.import_by_month), style = EyebrowText, modifier = Modifier.weight(1f))
            val allSelected = selected.size == items.size
            TextButton(onClick = { items = items.map { it.copy(isSelected = !allSelected) } }, modifier = Modifier.offset(x = Spacing.m)) {
                Text(if (allSelected) stringResource(R.string.import_untick_all) else stringResource(R.string.import_tick_all), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            parseResult.monthGroups.forEach { group ->
                val key = group.monthSortKey
                val monthItems = byMonth[key].orEmpty()
                if (monthItems.isEmpty()) return@forEach
                val isOpen = expanded[key] == true
                val allInMonth = monthItems.all { it.isSelected }
                val monthSpent = monthItems.filter { it.isSelected && it.type == "expense" }.sumOf { it.amount }

                Column(Modifier.fillMaxWidth().clip(KpShapes.large).background(CharcoalCard)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clickable(onClickLabel = if (isOpen) stringResource(R.string.import_collapse_month, group.monthYearKey) else stringResource(R.string.import_expand_month, group.monthYearKey)) { expanded[key] = !isOpen }
                            .padding(start = Spacing.xs, end = Spacing.l),
                    ) {
                        TickBox(
                            checked = allInMonth,
                            description = stringResource(R.string.import_whole_month, group.monthYearKey),
                            onToggle = { checked -> items = items.map { if (it.monthSortKey == key) it.copy(isSelected = checked) else it } },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(group.monthYearKey, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(
                                stringResource(R.string.import_month_summary, monthItems.count { it.isSelected }, monthItems.size, Money.format(monthSpent)),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(if (isOpen) KpIcons.Close else KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(18.dp))
                    }
                    AnimatedVisibility(visible = isOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(Modifier.padding(bottom = Spacing.s)) {
                            monthItems.forEach { item ->
                                StatementTransactionRow(
                                    item = item,
                                    // The same choices as everywhere else: an income row offers income categories.
                                    categories = Categories.categoriesFor(if (item.type.equals("income", ignoreCase = true)) "income" else "expense", prefs),
                                    onToggleSelected = { checked -> items = items.map { if (it.id == item.id) it.copy(isSelected = checked) else it } },
                                    onChangeCategory = { category -> items = items.map { if (it.id == item.id) it.copy(category = category) else it } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatementTransactionRow(
    item: StatementTransactionItem,
    onToggleSelected: (Boolean) -> Unit,
    onChangeCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<String> = Categories.expenseCategories(CategoryPrefs()),
) {
    var categoryMenu by remember { mutableStateOf(false) }
    val isIncome = item.type.equals("income", ignoreCase = true)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = Spacing.xs, end = Spacing.l),
    ) {
        TickBox(checked = item.isSelected, description = stringResource(R.string.import_merchant, item.merchant), onToggle = onToggleSelected)
        Box {
            CategoryBadge(
                category = item.category,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.import_change_category, item.category)) { categoryMenu = true },
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }, containerColor = CharcoalElevated, shape = KpShapes.medium) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category, style = MaterialTheme.typography.bodyLarge, color = if (category == item.category) WarmAmberLight else OffWhiteText) },
                        onClick = {
                            onChangeCategory(category)
                            categoryMenu = false
                        },
                    )
                }
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(item.merchant, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.dateFormatted, item.category, item.paymentMethod.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Left unticked on purpose (StatementChecks): say why, so ticking it is a decision.
            item.flag?.let { reason ->
                Text(reason, style = MaterialTheme.typography.bodySmall, color = WarmAmberLight, modifier = Modifier.testTag("statement_flag_${item.id}"))
            }
        }
        Text(
            text = if (isIncome) Money.format(item.amount, showPlus = true) else "-${Money.format(item.amount)}",
            style = MoneyText.small,
            color = if (isIncome) ChartreuseSecondary else OffWhiteText,
        )
    }
}

@Composable
private fun TickBox(checked: Boolean, description: String, onToggle: (Boolean) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
            .semantics { contentDescription = description },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(TickShape)
                .background(if (checked) WarmAmberPrimary else CharcoalCard)
                .border(1.dp, if (checked) WarmAmberPrimary else OutlineInteractive, TickShape),
        ) {
            if (checked) Icon(KpIcons.Check, contentDescription = null, tint = CharcoalBg, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun Figure(label: String, value: String, color: androidx.compose.ui.graphics.Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = EyebrowText)
        Text(value, style = style, color = color, maxLines = 1)
    }
}

/**
 * Shown while the statement is being read; the sheet opens once there's something to check.
 * Cancel, Back and a tap outside all stop the reading ([onCancel]). A long statement can take
 * minutes, so after a while it says so and how to make it quicker.
 */
@Composable
fun StatementParsingLoadingDialog(onCancel: () -> Unit) {
    var takingLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LONG_READ_MILLIS)
        takingLong = true
    }
    Dialog(onDismissRequest = onCancel) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(KpShapes.large)
                .background(CharcoalSurface)
                .padding(Spacing.xl),
        ) {
            CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Text(stringResource(R.string.import_reading), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.l))
            Text(
                if (takingLong) {
                    stringResource(R.string.import_reading_long)
                } else {
                    stringResource(R.string.import_reading_sorting)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onCancel, modifier = Modifier.padding(top = Spacing.m).testTag("statement_reading_cancel")) {
                Text(stringResource(R.string.import_cancel), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
            }
        }
    }
}

/** When the reading dialog starts saying a long statement takes a while. */
private const val LONG_READ_MILLIS = 20_000L

private val TickShape = SquircleShape(7.dp)
