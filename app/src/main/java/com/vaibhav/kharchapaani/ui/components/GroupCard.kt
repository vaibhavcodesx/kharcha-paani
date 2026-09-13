package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import kotlin.math.abs

@Composable
fun GroupCard(
    groupDetails: GroupWithDetails,
    onClick: () -> Unit,
    onDeleteGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val group = groupDetails.group
    val isOwed = groupDetails.netBalanceForMe > 0.01
    val iOwe = groupDetails.netBalanceForMe < -0.01

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalCard)
            .border(1.2.dp, CharcoalBorder, RoundedCornerShape(20.dp))
            .bounceClick(onClick = onClick)
            .padding(16.dp)
            .testTag("group_card_${group.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(WarmAmberPrimary.copy(alpha = 0.15f))
                            .border(1.dp, WarmAmberPrimary.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = group.emoji, fontSize = 22.sp)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = OffWhiteText,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${groupDetails.members.size + 1} members • ${groupDetails.transactionsCount} expenses",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                        )
                    }
                }

                IconButton(
                    onClick = onDeleteGroup,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Group",
                        tint = MutedWarmGray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Financial Summary Row inside Group
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalElevated)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Spent",
                        style = MaterialTheme.typography.labelSmall.copy(color = SubMutedWarmGray)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedRollingNumber(
                        targetValue = groupDetails.totalExpenses,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(CharcoalBorder)
                )

                Column {
                    Text(
                        text = "Your Share",
                        style = MaterialTheme.typography.labelSmall.copy(color = SubMutedWarmGray)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedRollingNumber(
                        targetValue = groupDetails.myShare,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = OffWhiteText
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(CharcoalBorder)
                )

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (isOwed) "You are owed" else if (iOwe) "You owe" else "Settled",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = when {
                                isOwed -> ChartreuseSecondary
                                iOwe -> WarmAmberPrimary
                                else -> MutedWarmGray
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    AnimatedRollingNumber(
                        targetValue = abs(groupDetails.netBalanceForMe),
                        prefix = if (isOwed) "+₹" else if (iOwe) "-₹" else "₹",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isOwed -> ChartreuseSecondary
                                iOwe -> WarmAmberPrimary
                                else -> MutedWarmGray
                            }
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Member Avatars Preview & Tap hint
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                ) {
                    // You avatar
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(WarmAmberPrimary)
                            .border(1.5.dp, CharcoalCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "ME",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = CharcoalBg
                            )
                        )
                    }

                    // Friends avatars
                    groupDetails.members.take(4).forEach { member ->
                        val col = try {
                            Color(android.graphics.Color.parseColor(member.avatarColor))
                        } catch (e: Exception) {
                            ChartreuseSecondary
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(1.5.dp, CharcoalCard, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = member.name.take(1).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CharcoalBg
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View Breakdown",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = WarmAmberPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = WarmAmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
