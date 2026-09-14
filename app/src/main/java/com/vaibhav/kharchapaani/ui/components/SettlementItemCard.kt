package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.numericFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A settlement or IOU in Recent Activity. Money you paid or lent shows as outgoing (amber), money
 * you received as incoming (green); a groupmates' settlement between two other people is neutral.
 * Only a party can retract one, so others get no menu.
 */
@Composable
fun SettlementItemCard(
    item: ActivityItem.Settlement,
    onRetract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settlement = item.settlement
    var showMenu by remember { mutableStateOf(false) }
    val outgoing = settlement.fromPersonId == ME
    val incoming = settlement.toPersonId == ME
    val accent = when {
        outgoing -> WarmAmberPrimary
        incoming -> ChartreuseSecondary
        else -> MutedWarmGray
    }
    val dateText = remember(settlement.date) { formatDate(settlement.date) }
    val detail = listOfNotNull(
        dateText,
        if (settlement.isIou) "IOU" else "Settlement",
        item.groupName,
        settlement.note.takeIf { it.isNotBlank() },
    ).joinToString(" • ")

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
            .testTag("settlement_item_${settlement.id}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Handshake, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = OffWhiteText, fontSize = 15.sp),
                    maxLines = 1,
                )
                Text(text = detail, style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray), maxLines = 1)
            }
            Text(
                text = (if (outgoing) "-" else if (incoming) "+" else "") + "₹" + String.format(Locale.US, "%,.2f", settlement.amount).removeSuffix(".00"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = accent, fontFamily = numericFontFamily),
            )
            if (item.involvesMe) {
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options for ${item.title}", tint = MutedWarmGray)
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = CharcoalSurface) {
                        DropdownMenuItem(
                            text = { Text(if (settlement.isIou) "Delete IOU" else "Retract settlement", color = OffWhiteText) },
                            onClick = {
                                showMenu = false
                                onRetract()
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        "Today, " + SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(millis))
    } else {
        SimpleDateFormat("MMM d • hh:mm a", Locale.getDefault()).format(Date(millis))
    }
}

private const val ME = -1L
