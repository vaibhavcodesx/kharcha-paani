package com.vaibhav.kharchapaani.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.CategoryHelper
import com.vaibhav.kharchapaani.ui.components.bounceClick
import com.vaibhav.kharchapaani.ui.components.shimmerLoading
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.ElectricIndigo
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

enum class InsightsViewTab {
    CHARTS,
    NOTION_TABLE,
    AI_INTEL
}

enum class TableSortField {
    DATE_DESC,
    DATE_ASC,
    AMOUNT_DESC,
    SHARE_DESC
}

@Composable
fun InsightsScreen(
    analytics: MonthlyAnalytics,
    transactions: List<FullTransaction> = emptyList(),
    people: List<PersonEntity> = emptyList(),
    aiSummary: String?,
    isGeneratingSummary: Boolean,
    onGenerateAiSummary: () -> Unit,
    onExportCsv: (Context) -> Unit,
    onEditTransaction: ((FullTransaction) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentViewTab by remember { mutableStateOf(InsightsViewTab.CHARTS) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(TableSortField.DATE_DESC) }

    val peopleMap = remember(people) { people.associateBy { it.id } }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Data & Analytics",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText
                        )
                    )
                    Text(
                        text = "Multi-view expenses, charts & AI intelligence",
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CharcoalElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier
                        .clickable { onExportCsv(context) }
                        .testTag("export_csv_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export CSV",
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }

        // Notion-Style Multi-View Switcher Tab Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                NotionViewTabButton(
                    title = "Charts & Pie",
                    icon = Icons.Default.PieChart,
                    isSelected = currentViewTab == InsightsViewTab.CHARTS,
                    onClick = { currentViewTab = InsightsViewTab.CHARTS },
                    testTag = "tab_view_charts",
                    modifier = Modifier.weight(1f)
                )

                NotionViewTabButton(
                    title = "Notion Table",
                    icon = Icons.Default.TableChart,
                    isSelected = currentViewTab == InsightsViewTab.NOTION_TABLE,
                    onClick = { currentViewTab = InsightsViewTab.NOTION_TABLE },
                    testTag = "tab_view_table",
                    modifier = Modifier.weight(1f)
                )

                NotionViewTabButton(
                    title = "AI Intel",
                    icon = Icons.Default.AutoAwesome,
                    isSelected = currentViewTab == InsightsViewTab.AI_INTEL,
                    onClick = { currentViewTab = InsightsViewTab.AI_INTEL },
                    testTag = "tab_view_ai",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        when (currentViewTab) {
            InsightsViewTab.CHARTS -> {
                // Cash Flow Overview Card
                item {
                    CashFlowOverviewCard(analytics = analytics)
                }

                // Interactive Donut / Pie Chart Section
                item {
                    DonutPieChartSection(
                        analytics = analytics,
                        selectedCategory = selectedCategoryFilter,
                        onSelectCategory = { selectedCategoryFilter = if (selectedCategoryFilter == it) null else it }
                    )
                }

                // If a category is selected, show its contributing transactions
                if (selectedCategoryFilter != null) {
                    val filteredTxs = transactions.filter {
                        it.transaction.category.equals(selectedCategoryFilter, ignoreCase = true)
                    }
                    item {
                        CategoryDrilldownSection(
                            category = selectedCategoryFilter!!,
                            transactions = filteredTxs,
                            peopleMap = peopleMap,
                            onClear = { selectedCategoryFilter = null }
                        )
                    }
                }

                // Spend Velocity & Metrics
                item {
                    SpendMetricsSection(analytics = analytics, transactions = transactions)
                }

                // Full Category Breakdown Progress Bars
                item {
                    CategoryBreakdownList(
                        summaries = analytics.categorySummaries,
                        selectedCategory = selectedCategoryFilter,
                        onSelectCategory = { selectedCategoryFilter = if (selectedCategoryFilter == it) null else it }
                    )
                }
            }

            InsightsViewTab.NOTION_TABLE -> {
                // Notion-Style Table View Component
                item {
                    NotionTableView(
                        transactions = transactions,
                        peopleMap = peopleMap,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedCategory = selectedCategoryFilter,
                        onSelectCategory = { selectedCategoryFilter = it },
                        sortOrder = sortOrder,
                        onSortChange = { sortOrder = it },
                        onEditTransaction = onEditTransaction
                    )
                }
            }

            InsightsViewTab.AI_INTEL -> {
                // Gemini AI Intelligence View
                item {
                    GeminiAiIntelligenceCard(
                        analytics = analytics,
                        aiSummary = aiSummary,
                        isGeneratingSummary = isGeneratingSummary,
                        onGenerateAiSummary = onGenerateAiSummary
                    )
                }
            }
        }
    }
}

@Composable
fun NotionViewTabButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) WarmAmberPrimary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) CharcoalBg else MutedWarmGray,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (isSelected) CharcoalBg else MutedWarmGray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp
                )
            )
        }
    }
}

@Composable
fun CashFlowOverviewCard(analytics: MonthlyAnalytics) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MONTHLY PERSONAL SPEND",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MutedWarmGray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "Splits Accounted",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ChartreuseSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Your Share (Spent)",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Money.format(analytics.totalExpense),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = WarmAmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = ChartreuseSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Income",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = Money.format(analytics.totalIncome),
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = ChartreuseSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CharcoalBorder)
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = null,
                        tint = if (analytics.netCashflow >= 0) ChartreuseSecondary else WarmAmberPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Net Cash Flow",
                        style = MaterialTheme.typography.bodyMedium.copy(color = OffWhiteText)
                    )
                }

                Text(
                    text = Money.format(analytics.netCashflow, showPlus = true),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (analytics.netCashflow >= 0) ChartreuseSecondary else WarmAmberPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun DonutPieChartSection(
    analytics: MonthlyAnalytics,
    selectedCategory: String?,
    onSelectCategory: (String) -> Unit
) {
    val summaries = analytics.categorySummaries
    val chartAnimation = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }

    androidx.compose.runtime.LaunchedEffect(summaries) {
        chartAnimation.snapTo(0f)
        chartAnimation.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 800,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
            .testTag("pie_chart_section")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CATEGORY PIE BREAKDOWN",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MutedWarmGray,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                if (selectedCategory != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CharcoalElevated,
                        modifier = Modifier
                            .bounceClick { onSelectCategory(selectedCategory) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("Reset", style = MaterialTheme.typography.labelSmall.copy(color = WarmAmberPrimary))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.Close, contentDescription = null, tint = WarmAmberPrimary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (summaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No expense data recorded yet.", style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray))
                }
            } else {
                // Interactive Donut Canvas with entry animation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(180.dp)
                            .testTag("interactive_pie_chart_canvas")
                    ) {
                        val strokeWidth = 28.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2f
                        val centerOffset = Offset(size.width / 2f, size.height / 2f)

                        var startAngle = -90f

                        for (item in summaries) {
                            val sweepAngle = (item.percentage / 100f) * 360f * chartAnimation.value
                            val color = CategoryHelper.getCategoryColor(item.category)
                            val isHighlighted = selectedCategory == null || selectedCategory == item.category

                            drawArc(
                                color = if (isHighlighted) color else color.copy(alpha = 0.25f),
                                startAngle = startAngle + 1.5f,
                                sweepAngle = (sweepAngle - 3f).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = Offset(centerOffset.x - radius, centerOffset.y - radius),
                                size = Size(diameter, diameter),
                                style = Stroke(
                                    width = if (selectedCategory == item.category) strokeWidth + 6.dp.toPx() else strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )
                            startAngle += sweepAngle
                        }
                    }

                    // Center Content Inside Donut
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        val highlightedItem = summaries.find { it.category == selectedCategory }
                        Text(
                            text = highlightedItem?.category ?: "Total Spent",
                            style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = Money.format(highlightedItem?.amount ?: analytics.totalExpense),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (highlightedItem != null) CategoryHelper.getCategoryColor(highlightedItem.category) else OffWhiteText,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                        if (highlightedItem != null) {
                            Text(
                                text = "${String.format(Locale.US, "%.1f", highlightedItem.percentage)}%",
                                style = MaterialTheme.typography.labelSmall.copy(color = SubMutedWarmGray)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Legend Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(summaries) { item ->
                        val isSelected = selectedCategory == item.category
                        val catColor = CategoryHelper.getCategoryColor(item.category)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.25f) else CharcoalElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) catColor else CharcoalBorder
                            ),
                            modifier = Modifier
                                .clickable { onSelectCategory(item.category) }
                                .testTag("pie_legend_${item.category}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(catColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${item.category} (${String.format(Locale.US, "%.0f", item.percentage)}%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) catColor else OffWhiteText,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
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
fun CategoryDrilldownSection(
    category: String,
    transactions: List<FullTransaction>,
    peopleMap: Map<Long, PersonEntity>,
    onClear: () -> Unit
) {
    val catColor = CategoryHelper.getCategoryColor(category)
    val sdf = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CharcoalElevated)
            .border(1.dp, catColor.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(catColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transactions in $category",
                        style = MaterialTheme.typography.titleMedium.copy(color = OffWhiteText, fontWeight = FontWeight.Bold)
                    )
                }

                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedWarmGray, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (transactions.isEmpty()) {
                Text("No transactions found in this category.", style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    transactions.take(5).forEach { ft ->
                        val tx = ft.transaction
                        val myShare = ft.myPersonalShare
                        val isSplit = ft.split != null

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CharcoalCard)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.merchant.ifBlank { "Expense" },
                                    style = MaterialTheme.typography.bodyMedium.copy(color = OffWhiteText, fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = sdf.format(Date(tx.date)),
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray, fontSize = 11.sp)
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Money.format(myShare),
                                    style = MaterialTheme.typography.bodyMedium.copy(color = WarmAmberPrimary, fontWeight = FontWeight.Bold)
                                )
                                if (isSplit) {
                                    Text(
                                        text = "Split bill: ${Money.format(tx.amount)}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = ChartreuseSecondary, fontSize = 10.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpendMetricsSection(
    analytics: MonthlyAnalytics,
    transactions: List<FullTransaction>
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Merchant Card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CharcoalCard)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Store,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Top Merchant",
                        style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = analytics.topMerchant,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Daily Streak Card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(CharcoalCard)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = ChartreuseSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Log Streak",
                        style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${analytics.dailyStreak} Days Active",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ChartreuseSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

@Composable
fun CategoryBreakdownList(
    summaries: List<CategorySummary>,
    selectedCategory: String?,
    onSelectCategory: (String) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Category Ranking",
            style = MaterialTheme.typography.titleMedium.copy(color = OffWhiteText, fontWeight = FontWeight.Bold)
        )

        summaries.forEach { catSummary ->
            val catColor = CategoryHelper.getCategoryColor(catSummary.category)
            val catIcon = CategoryHelper.getCategoryIcon(catSummary.category)
            val isSelected = selectedCategory == catSummary.category

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(
                        1.dp,
                        if (isSelected) catColor else CharcoalBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectCategory(catSummary.category) }
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(catColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = catIcon,
                                    contentDescription = null,
                                    tint = catColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = catSummary.category,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = OffWhiteText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "${catSummary.count} transactions",
                                    style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray)
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Money.format(catSummary.amount),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = OffWhiteText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "${String.format(Locale.US, "%.1f", catSummary.percentage)}%",
                                style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (catSummary.percentage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = catColor,
                        trackColor = CharcoalElevated
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------
// NOTION-STYLE DATABASE TABLE VIEW
// ----------------------------------------------------
@Composable
fun NotionTableView(
    transactions: List<FullTransaction>,
    peopleMap: Map<Long, PersonEntity>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    sortOrder: TableSortField,
    onSortChange: (TableSortField) -> Unit,
    onEditTransaction: ((FullTransaction) -> Unit)?
) {
    val horizontalScrollState = rememberScrollState()
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    // Filter & Sort
    val filteredList = remember(transactions, searchQuery, selectedCategory, sortOrder) {
        var list = transactions

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter {
                it.transaction.merchant.lowercase().contains(q) ||
                it.transaction.category.lowercase().contains(q) ||
                it.transaction.note.lowercase().contains(q) ||
                it.transaction.paymentMethod.lowercase().contains(q)
            }
        }

        if (selectedCategory != null) {
            list = list.filter { it.transaction.category.equals(selectedCategory, ignoreCase = true) }
        }

        when (sortOrder) {
            TableSortField.DATE_DESC -> list.sortedByDescending { it.transaction.date }
            TableSortField.DATE_ASC -> list.sortedBy { it.transaction.date }
            TableSortField.AMOUNT_DESC -> list.sortedByDescending { it.transaction.amount }
            TableSortField.SHARE_DESC -> list.sortedByDescending { it.myPersonalShare }
        }
    }

    val totalBillSum = filteredList.sumOf { it.transaction.amount }
    val totalYourShareSum = filteredList.sumOf { it.myPersonalShare }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Table Controls Bar: Search & Category Chips & Sort Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Filter items in database...", color = MutedWarmGray, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(16.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = MutedWarmGray, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WarmAmberPrimary,
                    unfocusedBorderColor = CharcoalBorder,
                    focusedTextColor = OffWhiteText,
                    unfocusedTextColor = OffWhiteText,
                    focusedContainerColor = CharcoalCard,
                    unfocusedContainerColor = CharcoalCard
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("notion_table_search")
            )

            // Sort Toggle Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CharcoalCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                modifier = Modifier
                    .clickable {
                        val next = when (sortOrder) {
                            TableSortField.DATE_DESC -> TableSortField.AMOUNT_DESC
                            TableSortField.AMOUNT_DESC -> TableSortField.SHARE_DESC
                            TableSortField.SHARE_DESC -> TableSortField.DATE_ASC
                            TableSortField.DATE_ASC -> TableSortField.DATE_DESC
                        }
                        onSortChange(next)
                    }
                    .height(48.dp)
                    .testTag("notion_table_sort_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (sortOrder) {
                            TableSortField.DATE_DESC -> "Date ↓"
                            TableSortField.DATE_ASC -> "Date ↑"
                            TableSortField.AMOUNT_DESC -> "Total ↓"
                            TableSortField.SHARE_DESC -> "Share ↓"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(color = OffWhiteText, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        // Notion Table Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(CharcoalCard)
                .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                .testTag("notion_database_table")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Table Headers Row (Scrollable horizontally)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalElevated)
                        .horizontalScroll(horizontalScrollState)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TableCellHeader(title = "Date", width = 90.dp)
                    TableCellHeader(title = "Merchant / Title", width = 140.dp)
                    TableCellHeader(title = "Type", width = 75.dp)
                    TableCellHeader(title = "Category", width = 100.dp)
                    TableCellHeader(title = "Payment", width = 85.dp)
                    TableCellHeader(title = "Total Bill", width = 100.dp)
                    TableCellHeader(title = "Your Share", width = 105.dp)
                    TableCellHeader(title = "Split Type", width = 110.dp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CharcoalBorder)
                )

                // Table Rows
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching transactions in database.", style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray))
                    }
                } else {
                    filteredList.forEachIndexed { index, ft ->
                        val isEven = index % 2 == 0
                        val tx = ft.transaction
                        val split = ft.split
                        val shares = ft.shares
                        val myShare = ft.myPersonalShare
                        val isIncome = tx.type.equals("income", ignoreCase = true)
                        val catColor = CategoryHelper.getCategoryColor(tx.category)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isEven) CharcoalCard else CharcoalElevated.copy(alpha = 0.4f))
                                .horizontalScroll(horizontalScrollState)
                                .clickable { onEditTransaction?.invoke(ft) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Date
                            Text(
                                text = sdf.format(Date(tx.date)),
                                style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray, fontSize = 12.sp),
                                modifier = Modifier.width(90.dp)
                            )

                            // Merchant / Title
                            Text(
                                text = tx.merchant.ifBlank { "Untitled Item" },
                                style = MaterialTheme.typography.bodySmall.copy(color = OffWhiteText, fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(140.dp)
                            )

                            // Type Tag
                            Box(modifier = Modifier.width(75.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isIncome) ChartreuseSecondary.copy(alpha = 0.2f) else WarmAmberPrimary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isIncome) "Income" else "Expense",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isIncome) ChartreuseSecondary else WarmAmberPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Notion Category Tag
                            Box(modifier = Modifier.width(100.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = catColor.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, catColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = tx.category,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = catColor,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Payment Method Tag
                            Box(modifier = Modifier.width(85.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = ElectricIndigo.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = tx.paymentMethod.ifBlank { "UPI" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = ElectricIndigo,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 10.sp
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            // Total Bill
                            Text(
                                text = Money.format(tx.amount),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isIncome) ChartreuseSecondary else OffWhiteText,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                modifier = Modifier.width(100.dp)
                            )

                            // Your Share (Highlighting split math)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(105.dp)
                            ) {
                                Text(
                                    text = Money.format(myShare),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (split != null) ChartreuseSecondary else WarmAmberPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                if (split != null) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Group,
                                        contentDescription = "Split",
                                        tint = ChartreuseSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }

                            // Split Type / Participants Count
                            Text(
                                text = if (split != null) "${split.splitType.replaceFirstChar { it.uppercase() }} (${shares.size}p)" else "Solo",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (split != null) ChartreuseSecondary else SubMutedWarmGray,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.width(110.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.5.dp)
                                .background(CharcoalBorder.copy(alpha = 0.5f))
                        )
                    }

                    // Table Summary Footer
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CharcoalElevated)
                            .horizontalScroll(horizontalScrollState)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${filteredList.size} items",
                            style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(90.dp)
                        )
                        Text(
                            text = "SUM",
                            style = MaterialTheme.typography.labelSmall.copy(color = MutedWarmGray, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(400.dp)
                        )
                        Text(
                            text = Money.format(totalBillSum),
                            style = MaterialTheme.typography.bodySmall.copy(color = OffWhiteText, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(100.dp)
                        )
                        Text(
                            text = Money.format(totalYourShareSum),
                            style = MaterialTheme.typography.bodySmall.copy(color = ChartreuseSecondary, fontWeight = FontWeight.Bold),
                            modifier = Modifier.width(105.dp)
                        )
                        Spacer(modifier = Modifier.width(110.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun TableCellHeader(title: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall.copy(
            color = MutedWarmGray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        ),
        modifier = Modifier.width(width)
    )
}

// ----------------------------------------------------
// GEMINI AI FINANCIAL INTELLIGENCE CARD
// ----------------------------------------------------
@Composable
fun GeminiAiIntelligenceCard(
    analytics: MonthlyAnalytics,
    aiSummary: String?,
    isGeneratingSummary: Boolean,
    onGenerateAiSummary: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalCard)
            .border(1.dp, WarmAmberPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(18.dp)
            .testTag("ai_summary_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(WarmAmberPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Gemini AI Financial Intel",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Deep budget analysis & spending velocity",
                            style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray)
                        )
                    }
                }

                if (isGeneratingSummary) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = WarmAmberPrimary,
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (aiSummary != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalElevated)
                        .padding(14.dp)
                ) {
                    Text(
                        text = aiSummary,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = OffWhiteText,
                            lineHeight = 22.sp
                        )
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalElevated)
                        .shimmerLoading(enabled = isGeneratingSummary)
                        .padding(16.dp)
                ) {
                    Text(
                        text = if (isGeneratingSummary) "Gemini is analyzing your spending records, categorized split overheads, and formulating budget optimization tips..." else "Tap below to get an instant AI audit of your monthly spending patterns, personal split overhead, and savings recommendations.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isGeneratingSummary) WarmAmberPrimary else MutedWarmGray,
                            lineHeight = 18.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onGenerateAiSummary,
                enabled = !isGeneratingSummary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .bounceClick { if (!isGeneratingSummary) onGenerateAiSummary() }
                    .testTag("generate_ai_summary_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmAmberPrimary,
                    contentColor = CharcoalBg
                )
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (aiSummary == null) "Generate AI Intelligence Audit" else "Refresh Financial Audit",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
