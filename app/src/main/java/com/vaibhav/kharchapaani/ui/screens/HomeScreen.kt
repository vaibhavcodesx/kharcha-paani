package com.vaibhav.kharchapaani.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.QuickCaptureBar
import com.vaibhav.kharchapaani.ui.components.SettlementItemCard
import com.vaibhav.kharchapaani.ui.components.TransactionItemCard
import com.vaibhav.kharchapaani.ui.components.bounceClick
import com.vaibhav.kharchapaani.ui.components.pressBounce
import com.vaibhav.kharchapaani.ui.shell.dockClearance
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.displayFontFamily
import com.vaibhav.kharchapaani.ui.theme.numericFontFamily
import kotlin.math.abs

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
    onManualAdd: (String) -> Unit,
    onEditTransaction: (FullTransaction) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    onRetractSettlement: (Long) -> Unit,
    onOpenIouDialog: () -> Unit,
    onOpenSimplifyDebts: () -> Unit,
    accountInitial: String,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier
) {

    var showSearch by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = dockClearance()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar: Custom Logo, App Name, Streak Flame & Quick IOU action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CharcoalElevated)
                            .border(1.dp, WarmAmberPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_kharcha_logo),
                            contentDescription = "Kharcha Paani Logo",
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Kharcha Paani",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = OffWhiteText,
                                fontSize = 21.sp,
                                fontFamily = displayFontFamily
                            )
                        )
                        Text(
                            text = "Smart splits & daily expenses",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MutedWarmGray,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Daily Streak Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier
                            .bounceClick()
                            .testTag("streak_badge")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Daily Streak",
                                tint = WarmAmberPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${monthlyAnalytics.dailyStreak}d streak",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = OffWhiteText,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = numericFontFamily
                                )
                            )
                        }
                    }

                    // Direct IOU Action Shortcut
                    IconButton(
                        onClick = onOpenIouDialog,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CharcoalCard)
                            .border(1.dp, CharcoalBorder, RoundedCornerShape(10.dp))
                            .testTag("home_iou_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Handshake,
                            contentDescription = "Log IOU",
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // One clickable (bounceClick) rather than IconButton + bounceClick, so TalkBack
                    // sees a single "Account" button. Compose expands the touch target to 48dp.
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WarmAmberPrimary.copy(alpha = 0.2f))
                            .bounceClick(onClick = onOpenAccount)
                            .semantics {
                                contentDescription = "Account"
                                role = Role.Button
                            }
                            .testTag("home_account_button")
                    ) {
                        Text(
                            text = accountInitial,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = WarmAmberLight,
                                fontWeight = FontWeight.Bold,
                                fontFamily = displayFontFamily
                            ),
                            modifier = Modifier.clearAndSetSemantics {}
                        )
                    }
                }
            }
        }

        // Hero Financial Header: Net Balance & Month Summary Cards
        item {
            val isOwedToMe = totalNetOwed > 0.01
            val iOwe = totalNetOwed < -0.01
            val isSettled = !isOwedToMe && !iOwe

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(CharcoalElevated, CharcoalCard)
                        )
                    )
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(22.dp))
                    .padding(18.dp)
                    .testTag("hero_net_balance_card")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "NET SPLIT BALANCE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MutedWarmGray,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )

                        if (!isSettled) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOwedToMe) ChartreuseSecondary.copy(alpha = 0.2f) else WarmAmberPrimary.copy(alpha = 0.2f),
                                modifier = Modifier
                                    .bounceClick(onClick = onOpenSimplifyDebts)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ElectricBolt,
                                        contentDescription = null,
                                        tint = if (isOwedToMe) ChartreuseSecondary else WarmAmberPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Simplify",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isOwedToMe) ChartreuseSecondary else WarmAmberPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    AnimatedRollingNumber(
                        targetValue = if (isSettled) 0.0 else abs(totalNetOwed),
                        prefix = if (isOwedToMe) "+₹" else if (iOwe) "-₹" else "₹",
                        style = MaterialTheme.typography.displayMedium.copy(
                            color = when {
                                isOwedToMe -> ChartreuseSecondary
                                iOwe -> WarmAmberPrimary
                                else -> OffWhiteText
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = numericFontFamily
                        )
                    )

                    Text(
                        text = when {
                            isOwedToMe -> "Teammates and friends owe you overall"
                            iOwe -> "You owe teammates overall"
                            else -> "All shared split balances are even"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(CharcoalBorder)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Secondary Sub-metrics Row with Animated Numbers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Monthly Spent (Share)",
                                style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = WarmAmberPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AnimatedRollingNumber(
                                    targetValue = monthlyAnalytics.totalExpense,
                                    prefix = "₹",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = OffWhiteText,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = numericFontFamily
                                    )
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Monthly Income",
                                style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.TrendingUp,
                                    contentDescription = null,
                                    tint = ChartreuseSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                AnimatedRollingNumber(
                                    targetValue = monthlyAnalytics.totalIncome,
                                    prefix = "₹",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = ChartreuseSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = numericFontFamily
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Fast Multimodal Capture Bar
        item {
            QuickCaptureBar(
                isParsing = isParsing,
                onParseText = onParseText,
                onParseImage = onParseImage,
                onParsePdf = onParsePdf,
                onManualAdd = onManualAdd
            )
        }

        // Activity Feed Header & Filters
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold
                    )
                )

                val searchInteraction = remember { MutableInteractionSource() }
                IconButton(
                    onClick = { showSearch = !showSearch },
                    interactionSource = searchInteraction,
                    modifier = Modifier
                        .size(32.dp)
                        .pressBounce(searchInteraction)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (showSearch) WarmAmberPrimary else MutedWarmGray
                    )
                }
            }

            AnimatedVisibility(
                visible = showSearch,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = { Text("Search transactions, merchants, notes...", color = SubMutedWarmGray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_activity_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WarmAmberPrimary,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedContainerColor = CharcoalElevated,
                            unfocusedContainerColor = CharcoalElevated,
                            cursorColor = WarmAmberPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips Row with Bouncy Physics
            val filters = listOf("All", "Expenses", "Income", "Splits")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filters.forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) WarmAmberPrimary else CharcoalCard,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier
                            .bounceClick { onFilterChange(filter) }
                            .testTag("filter_chip_$filter")
                    ) {
                        Text(
                            text = filter,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (isSelected) CharcoalBg else MutedWarmGray,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Activity list: transactions, settlements and IOUs
        if (activity.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CharcoalCard)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(18.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "No matching transactions found" else "No transactions logged yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Type 'chai 40 cash' or tap the mic above to log your first expense in seconds.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(activity, key = { it.key }) { item ->
                when (item) {
                    is ActivityItem.Transaction -> TransactionItemCard(
                        fullTx = item.fullTransaction,
                        isReadOnly = item.isReadOnly,
                        onEdit = { onEditTransaction(item.fullTransaction) },
                        onDelete = { onDeleteTransaction(item.fullTransaction.transaction.id) },
                        modifier = Modifier.animateItem()
                    )
                    is ActivityItem.Settlement -> SettlementItemCard(
                        item = item,
                        onRetract = { onRetractSettlement(item.settlement.id) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}
