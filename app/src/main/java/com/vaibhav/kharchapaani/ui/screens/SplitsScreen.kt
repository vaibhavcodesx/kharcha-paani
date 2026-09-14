package com.vaibhav.kharchapaani.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.DeleteFriendDialog
import com.vaibhav.kharchapaani.ui.components.GroupCard
import com.vaibhav.kharchapaani.ui.components.PersonBalanceCard
import com.vaibhav.kharchapaani.ui.components.bounceClick
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.numericFontFamily

@Composable
fun SplitsScreen(
    personBalances: List<PersonBalance>,
    groups: List<GroupWithDetails>,
    onOpenAddFriend: () -> Unit,
    onDeleteFriend: (Long) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onSelectGroup: (GroupWithDetails) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onOpenSimplifyDebts: () -> Unit,
    onOpenIouDialog: () -> Unit,
    onSettleUp: (PersonEntity) -> Unit,
    onViewHistory: (PersonEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalOwedToMe = personBalances.filter { it.netBalance > 0 }.sumOf { it.netBalance }
    val totalIOwe = personBalances.filter { it.netBalance < 0 }.sumOf { it.netBalance }

    var selectedSection by remember { mutableIntStateOf(0) } // 0: Friends, 1: Trips & Groups
    var friendToDelete by remember { mutableStateOf<PersonBalance?>(null) }

    DeleteFriendDialog(
        person = friendToDelete?.person,
        currentBalance = friendToDelete?.netBalance ?: 0.0,
        isOpen = friendToDelete != null,
        onConfirmDelete = { onDeleteFriend(it) },
        onDismiss = { friendToDelete = null }
    )

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
                        text = "Split & Trip Ledger",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText
                        )
                    )
                    Text(
                        text = "Real-time 2-way debt settlement & group trips",
                        style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = WarmAmberPrimary,
                    modifier = Modifier
                        .bounceClick {
                            if (selectedSection == 0) onOpenAddFriend() else onOpenCreateGroup()
                        }
                        .clickable {
                            if (selectedSection == 0) onOpenAddFriend() else onOpenCreateGroup()
                        }
                        .testTag("add_split_top_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (selectedSection == 0) Icons.Default.PersonAdd else Icons.Default.Add,
                            contentDescription = null,
                            tint = CharcoalBg,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedSection == 0) "Add Friend" else "New Group",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = CharcoalBg,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }

        // Section Tabs: Friends vs Groups
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CharcoalCard)
                    .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                    .padding(4.dp)
            ) {
                // Friends Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedSection == 0) WarmAmberPrimary else Color.Transparent)
                        .clickable { selectedSection = 0 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (selectedSection == 0) CharcoalBg else MutedWarmGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Friends (${personBalances.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (selectedSection == 0) CharcoalBg else MutedWarmGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Trips & Groups Tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedSection == 1) WarmAmberPrimary else Color.Transparent)
                        .clickable { selectedSection = 1 }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = if (selectedSection == 1) CharcoalBg else MutedWarmGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Trips & Groups (${groups.size})",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (selectedSection == 1) CharcoalBg else MutedWarmGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // Summary Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(CharcoalCard)
                    .border(1.2.dp, CharcoalBorder, RoundedCornerShape(20.dp))
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Total to Collect",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = ChartreuseSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedRollingNumber(
                            targetValue = totalOwedToMe,
                            prefix = "+₹",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = ChartreuseSecondary,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = numericFontFamily
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(44.dp)
                            .background(CharcoalBorder)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total to Pay",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = WarmAmberPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        AnimatedRollingNumber(
                            targetValue = kotlin.math.abs(totalIOwe),
                            prefix = "-₹",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = WarmAmberPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = numericFontFamily
                            )
                        )
                    }
                }
            }
        }

        // Action Toolbar (Simplify & IOU)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenSimplifyDebts,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .bounceClick { onOpenSimplifyDebts() }
                        .testTag("simplify_debts_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalElevated,
                        contentColor = OffWhiteText
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Simplify Debts",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Button(
                    onClick = onOpenIouDialog,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .bounceClick { onOpenIouDialog() }
                        .testTag("record_iou_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CharcoalElevated,
                        contentColor = OffWhiteText
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.Handshake,
                        contentDescription = null,
                        tint = ChartreuseSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Record IOU",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        if (selectedSection == 0) {
            // FRIENDS LIST
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Friends & Balances (${personBalances.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (personBalances.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CharcoalCard)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No teammates added yet. Tap '+ Add Friend' above to start splitting.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray)
                        )
                    }
                }
            } else {
                items(personBalances, key = { it.person.id }) { pBalance ->
                    PersonBalanceCard(
                        personBalance = pBalance,
                        onSettleUp = { onSettleUp(pBalance.person) },
                        onViewHistory = { onViewHistory(pBalance.person) },
                        onDelete = { friendToDelete = pBalance }
                    )
                }
            }
        } else {
            // TRIPS & GROUPS LIST
            item {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Active Trip Groups (${groups.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = OffWhiteText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Join",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable(onClickLabel = "Join a group with an invite") { onJoinGroup() }
                                .padding(vertical = 12.dp)
                        )
                        Text(
                            text = "+ Create",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = WarmAmberPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clickable { onOpenCreateGroup() }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }

            if (groups.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CharcoalCard)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No trip groups created yet.",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = OffWhiteText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Create a group for your next trip, outing, or apartment to track shared expenses together.",
                                style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                            )
                        }
                    }
                }
            } else {
                items(groups, key = { it.group.id }) { groupItem ->
                    GroupCard(
                        groupDetails = groupItem,
                        onClick = { onSelectGroup(groupItem) },
                        onDeleteGroup = { onDeleteGroup(groupItem.group.id) }
                    )
                }
            }
        }
    }
}
