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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.vaibhav.kharchapaani.data.model.PersonBalance
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
import kotlin.math.abs

@Composable
fun PersonBalanceCard(
    personBalance: PersonBalance,
    onSettleUp: () -> Unit,
    onViewHistory: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val person = personBalance.person
    val net = personBalance.netBalance
    val isOwedToMe = net > 0.01
    val iOweThem = net < -0.01
    val isSettled = !isOwedToMe && !iOweThem

    val avatarColor = try {
        Color(android.graphics.Color.parseColor(person.avatarColor))
    } catch (e: Exception) {
        WarmAmberPrimary
    }

    val cardBorderColor = when {
        isOwedToMe -> ChartreuseSecondary.copy(alpha = 0.35f)
        iOweThem -> WarmAmberPrimary.copy(alpha = 0.35f)
        else -> CharcoalBorder
    }

    val statusBadgeBg = when {
        isOwedToMe -> ChartreuseSecondary.copy(alpha = 0.12f)
        iOweThem -> WarmAmberPrimary.copy(alpha = 0.12f)
        else -> CharcoalElevated
    }

    val statusBadgeText = when {
        isOwedToMe -> ChartreuseSecondary
        iOweThem -> WarmAmberPrimary
        else -> MutedWarmGray
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CharcoalCard)
            .border(1.2.dp, cardBorderColor, RoundedCornerShape(20.dp))
            .bounceClick(onClick = onViewHistory)
            .padding(16.dp)
            .testTag("person_balance_card_${person.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Avatar Circle with Initials
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(avatarColor.copy(alpha = 0.22f))
                        .border(2.dp, avatarColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = person.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = avatarColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Name and Status Pill
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = person.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = OffWhiteText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBadgeBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, statusBadgeText.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = when {
                                isOwedToMe -> "Owes you money"
                                iOweThem -> "You owe them"
                                else -> "All Settled Up"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusBadgeText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Balance Number & Delete
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedRollingNumber(
                            targetValue = if (isSettled) 0.0 else abs(net),
                            prefix = if (isOwedToMe) "+₹" else if (iOweThem) "-₹" else "₹",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = when {
                                    isOwedToMe -> ChartreuseSecondary
                                    iOweThem -> WarmAmberPrimary
                                    else -> OffWhiteText
                                },
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = numericFontFamily
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("delete_person_${person.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Friend",
                            tint = MutedWarmGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons with Spring Touch Feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onViewHistory,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .bounceClick(onClick = onViewHistory),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OffWhiteText),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = OffWhiteText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "History",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = OffWhiteText,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Button(
                    onClick = onSettleUp,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .bounceClick(onClick = onSettleUp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSettled) CharcoalElevated else WarmAmberPrimary,
                        contentColor = if (isSettled) OffWhiteText else CharcoalBg
                    )
                ) {
                    Icon(
                        imageVector = if (isSettled) Icons.Default.CheckCircle else Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isSettled) "Log Payment" else "Settle Up",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }
    }
}
