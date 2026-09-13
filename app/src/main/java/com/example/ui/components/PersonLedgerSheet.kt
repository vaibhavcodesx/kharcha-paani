package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.entity.PersonEntity
import com.example.data.model.PersonLedgerItem
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.ChartreuseSecondary
import com.example.ui.theme.MutedWarmGray
import com.example.ui.theme.OffWhiteText
import com.example.ui.theme.SubMutedWarmGray
import com.example.ui.theme.WarmAmberPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonLedgerSheet(
    person: PersonEntity?,
    ledgerItems: List<PersonLedgerItem>,
    onSettleUp: (PersonEntity) -> Unit,
    onDismiss: () -> Unit
) {
    if (person == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    val avatarColor = try {
        Color(android.graphics.Color.parseColor(person.avatarColor))
    } catch (e: Exception) {
        WarmAmberPrimary
    }

    val totalNet = ledgerItems.sumOf { it.netChange }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CharcoalSurface,
        contentColor = OffWhiteText,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(CharcoalBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.2f))
                            .border(1.5.dp, avatarColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = person.name.take(2).uppercase(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = avatarColor,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = when {
                                totalNet > 0.01 -> "Owes you ₹${String.format("%.2f", totalNet)}"
                                totalNet < -0.01 -> "You owe ₹${String.format("%.2f", abs(totalNet))}"
                                else -> "Settled up"
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = when {
                                    totalNet > 0.01 -> ChartreuseSecondary
                                    totalNet < -0.01 -> WarmAmberPrimary
                                    else -> MutedWarmGray
                                },
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MutedWarmGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Settle Up
            Button(
                onClick = { onSettleUp(person) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("ledger_settle_up_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WarmAmberPrimary,
                    contentColor = CharcoalBg
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Payment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Settle Up / Record Payment",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                "Ledger History",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = OffWhiteText,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (ledgerItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No shared split transactions or settlements yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ledgerItems, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CharcoalElevated)
                                .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (item.isSettlement) ChartreuseSecondary.copy(alpha = 0.15f) else WarmAmberPrimary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (item.isSettlement) Icons.Default.Payment else Icons.Default.Receipt,
                                        contentDescription = null,
                                        tint = if (item.isSettlement) ChartreuseSecondary else WarmAmberPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = OffWhiteText,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    Text(
                                        text = "${sdf.format(Date(item.date))} • ${item.subtitle}",
                                        style = MaterialTheme.typography.bodySmall.copy(color = SubMutedWarmGray)
                                    )
                                }
                            }

                            Text(
                                text = "${if (item.netChange > 0) "+" else "-"}₹${String.format("%.2f", abs(item.netChange))}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = if (item.netChange > 0) ChartreuseSecondary else WarmAmberPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
