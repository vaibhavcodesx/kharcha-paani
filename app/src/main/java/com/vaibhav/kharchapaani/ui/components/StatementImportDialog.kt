package com.vaibhav.kharchapaani.ui.components

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

// The statement parser's category set. It includes Groceries, Investment and Income (Salary), which
// the rest of the app doesn't know; reconciling them is part of editable categories (backlog item 7).
val STATEMENT_CATEGORIES = listOf(
    "Food", "Transport", "Shopping", "Bills", "Groceries", "Entertainment", "Health", "Rent",
    "Investment", "Income (Salary)", "Income (Client)", "Income (Other)", "Other",
)

@Composable
fun StatementImportDialog(
    parseResult: StatementParseResult,
    onConfirmImport: (List<StatementTransactionItem>) -> Unit,
    onDismiss: () -> Unit,
) {
    KpModalSheet(onDismiss = onDismiss) { StatementImportContent(parseResult, onConfirmImport) }
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
                label = if (selected.size == 1) "Import 1 entry" else "Import ${selected.size} entries",
                disabledLabel = "Tick entries to import",
                enabled = selected.isNotEmpty(),
                onClick = { onConfirmImport(selected) },
                summary = "${selected.size} of ${items.size}",
                detail = "selected",
                buttonTestTag = "confirm_import_statement_button",
            )
        },
    ) {
        SheetTitle(
            eyebrow = "From your statement",
            title = parseResult.bankOrApp,
            supporting = parseResult.statementPeriod.takeIf { it.isNotBlank() }
                ?: if (items.size == 1) "1 entry found" else "${items.size} entries found",
        )

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val style = pairedFigureStyle(spent, cameIn)
            Figure("Spent", Money.format(spent), OffWhiteText, style, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = Spacing.xl).width(1.dp).fillMaxHeight().background(CharcoalBorder))
            Figure("Came in", Money.format(cameIn), ChartreuseSecondary, style, Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Spacing.xl)) {
            Text("BY MONTH", style = EyebrowText, modifier = Modifier.weight(1f))
            val allSelected = selected.size == items.size
            TextButton(onClick = { items = items.map { it.copy(isSelected = !allSelected) } }, modifier = Modifier.offset(x = Spacing.m)) {
                Text(if (allSelected) "Untick all" else "Tick all", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
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
                            .clickable(onClickLabel = if (isOpen) "Collapse ${group.monthYearKey}" else "Expand ${group.monthYearKey}") { expanded[key] = !isOpen }
                            .padding(start = Spacing.xs, end = Spacing.l),
                    ) {
                        TickBox(
                            checked = allInMonth,
                            description = "Import all of ${group.monthYearKey}",
                            onToggle = { checked -> items = items.map { if (it.monthSortKey == key) it.copy(isSelected = checked) else it } },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(group.monthYearKey, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(
                                "${monthItems.count { it.isSelected }} of ${monthItems.size} · ${Money.format(monthSpent)} spent",
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
        TickBox(checked = item.isSelected, description = "Import ${item.merchant}", onToggle = onToggleSelected)
        Box {
            CategoryBadge(
                category = item.category,
                modifier = Modifier.clickable(onClickLabel = "Change category, now ${item.category}") { categoryMenu = true },
            )
            DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }, containerColor = CharcoalElevated, shape = KpShapes.medium) {
                STATEMENT_CATEGORIES.forEach { category ->
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

/** Shown while the statement is being read; the sheet opens once there's something to check. */
@Composable
fun StatementParsingLoadingDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(KpShapes.large)
                .background(CharcoalSurface)
                .padding(Spacing.xl),
        ) {
            CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
            Text("Reading your statement", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.l))
            Text(
                "Sorting its entries by month. Nothing is added until you check them.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

private val TickShape = SquircleShape(7.dp)
