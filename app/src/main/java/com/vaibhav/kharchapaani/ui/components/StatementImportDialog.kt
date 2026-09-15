package com.vaibhav.kharchapaani.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.model.StatementMonthGroup
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.ElectricIndigo
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

val STATEMENT_CATEGORIES = listOf(
    "Food",
    "Transport",
    "Shopping",
    "Bills",
    "Groceries",
    "Entertainment",
    "Health",
    "Rent",
    "Investment",
    "Income (Salary)",
    "Income (Client)",
    "Income (Other)",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementImportDialog(
    parseResult: StatementParseResult,
    onConfirmImport: (List<StatementTransactionItem>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local mutable state for all items to allow toggling checkboxes & modifying category
    var itemsList by remember(parseResult) {
        mutableStateOf(parseResult.allItems)
    }

    // Expanded state for month accordion cards
    val monthExpandedState = remember(parseResult) {
        mutableStateMapOf<String, Boolean>().apply {
            parseResult.monthGroups.forEachIndexed { index, group ->
                put(group.monthSortKey, index == 0) // First month expanded by default
            }
        }
    }

    val selectedCount = itemsList.count { it.isSelected }
    val totalExpenseSelected = itemsList.filter { it.isSelected && it.type == "expense" }.sumOf { it.amount }
    val totalIncomeSelected = itemsList.filter { it.isSelected && it.type == "income" }.sumOf { it.amount }

    // Group current items by month
    val groupedItems = remember(itemsList) {
        itemsList.groupBy { it.monthSortKey }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalBg,
        dragHandle = { SheetHandle() },
        modifier = modifier.fillMaxHeight(0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(WarmAmberPrimary.copy(alpha = 0.2f))
                            .border(1.dp, WarmAmberPrimary, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = parseResult.bankOrApp,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (parseResult.statementPeriod.isNotBlank())
                                "Period: ${parseResult.statementPeriod}"
                            else
                                "${parseResult.totalTransactionsCount} transactions detected",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                        )
                    }
                }

                val closeInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onDismiss,
                    interactionSource = closeInteraction,
                    modifier = Modifier.pressBounce(closeInteraction)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MutedWarmGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Overview Summary Metrics Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Total Items
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            "SELECTED",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MutedWarmGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "$selectedCount / ${itemsList.size}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Total Expense
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    Column {
                        Text(
                            "TOTAL EXPENSES",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MutedWarmGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "-${Money.format(totalExpenseSelected)}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = WarmAmberPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Total Income
                if (totalIncomeSelected > 0) {
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(CharcoalCard)
                            .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                "TOTAL INCOME",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = MutedWarmGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "+${Money.format(totalIncomeSelected)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = ChartreuseSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quick Batch Toggle Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "EXPENSES BY MONTH",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MutedWarmGray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (selectedCount == itemsList.size) "Deselect All" else "Select All",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = WarmAmberPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val selectAll = selectedCount != itemsList.size
                                itemsList = itemsList.map { it.copy(isSelected = selectAll) }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Month-Wise Accordion List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                parseResult.monthGroups.forEach { monthGroup ->
                    val sortKey = monthGroup.monthSortKey
                    val currentMonthItems = groupedItems[sortKey] ?: emptyList()
                    val isExpanded = monthExpandedState[sortKey] == true
                    val allInMonthSelected = currentMonthItems.isNotEmpty() && currentMonthItems.all { it.isSelected }
                    val monthExpense = currentMonthItems.filter { it.isSelected && it.type == "expense" }.sumOf { it.amount }
                    val monthIncome = currentMonthItems.filter { it.isSelected && it.type == "income" }.sumOf { it.amount }

                    item(key = sortKey) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(CharcoalCard)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                        ) {
                            // Month Header Row (Click to expand/collapse)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        monthExpandedState[sortKey] = !isExpanded
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = allInMonthSelected,
                                        onCheckedChange = { checked ->
                                            itemsList = itemsList.map { item ->
                                                if (item.monthSortKey == sortKey) {
                                                    item.copy(isSelected = checked)
                                                } else {
                                                    item
                                                }
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = WarmAmberPrimary,
                                            uncheckedColor = MutedWarmGray,
                                            checkmarkColor = CharcoalBg
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Column {
                                        Text(
                                            text = monthGroup.monthYearKey,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = OffWhiteText,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                        Text(
                                            text = "${currentMonthItems.count { it.isSelected }}/${currentMonthItems.size} items • ${Money.format(monthExpense)} spent",
                                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Toggle Month",
                                        tint = MutedWarmGray
                                    )
                                }
                            }

                            // Expanded Items List
                            AnimatedVisibility(
                                visible = isExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    currentMonthItems.forEach { item ->
                                        StatementTransactionRow(
                                            item = item,
                                            onToggleSelected = { checked ->
                                                itemsList = itemsList.map {
                                                    if (it.id == item.id) it.copy(isSelected = checked) else it
                                                }
                                            },
                                            onChangeCategory = { newCategory ->
                                                itemsList = itemsList.map {
                                                    if (it.id == item.id) it.copy(category = newCategory) else it
                                                }
                                            },
                                            onDeleteItem = {
                                                itemsList = itemsList.filter { it.id != item.id }
                                            }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Sticky Confirm Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val cancelInteraction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onDismiss,
                    interactionSource = cancelInteraction,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .pressBounce(cancelInteraction),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MutedWarmGray),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Text(
                        "Cancel",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = MutedWarmGray,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Button(
                    onClick = {
                        val toImport = itemsList.filter { it.isSelected }
                        onConfirmImport(toImport)
                    },
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .weight(2f)
                        .height(52.dp)
                        .bounceClick {
                            val toImport = itemsList.filter { it.isSelected }
                            onConfirmImport(toImport)
                        }
                        .testTag("confirm_import_statement_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WarmAmberPrimary,
                        contentColor = CharcoalBg,
                        disabledContainerColor = CharcoalElevated,
                        disabledContentColor = MutedWarmGray
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import $selectedCount Expenses",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
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
    onDeleteItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isCategoryMenuOpen by remember { mutableStateOf(false) }

    val categoryColor = CategoryHelper.getCategoryColor(item.category)
    val categoryIcon = CategoryHelper.getCategoryIcon(item.category)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalElevated)
            .border(1.dp, CharcoalBorder.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox
        Checkbox(
            checked = item.isSelected,
            onCheckedChange = onToggleSelected,
            colors = CheckboxDefaults.colors(
                checkedColor = WarmAmberPrimary,
                uncheckedColor = MutedWarmGray,
                checkmarkColor = CharcoalBg
            ),
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Category Icon with tap-to-change Dropdown
        Box {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(categoryColor.copy(alpha = 0.15f))
                    .clickable { isCategoryMenuOpen = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = item.category,
                    tint = categoryColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = isCategoryMenuOpen,
                onDismissRequest = { isCategoryMenuOpen = false },
                modifier = Modifier.background(CharcoalSurface)
            ) {
                STATEMENT_CATEGORIES.forEach { cat ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (cat == item.category) WarmAmberPrimary else OffWhiteText
                                )
                            )
                        },
                        onClick = {
                            onChangeCategory(cat)
                            isCategoryMenuOpen = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Merchant, Date & Payment Method
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.merchant,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.dateFormatted,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SubMutedWarmGray,
                        fontSize = 11.sp
                    )
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SubMutedWarmGray,
                        fontSize = 10.sp
                    )
                )
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = categoryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (item.paymentMethod.isNotBlank()) {
                    Text(
                        text = "(${item.paymentMethod})",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = SubMutedWarmGray,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Amount & Delete Icon
        Column(horizontalAlignment = Alignment.End) {
            val isIncome = item.type.equals("income", ignoreCase = true)
            Text(
                text = "${if (isIncome) "+" else "-"}${Money.format(item.amount)}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isIncome) ChartreuseSecondary else WarmAmberPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        IconButton(
            onClick = onDeleteItem,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Exclude row",
                tint = MutedWarmGray.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun StatementParsingLoadingDialog(
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(CharcoalElevated)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(24.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(WarmAmberPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = WarmAmberPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Analyzing Bank / UPI Statement",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Gemini AI is reading transactions, categorizing expenses, and sorting dates into respective months...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = SubMutedWarmGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                )
            }
        }
    }
}
