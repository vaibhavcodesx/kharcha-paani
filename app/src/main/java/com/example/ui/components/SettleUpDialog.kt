package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.db.entity.PersonEntity
import com.example.ui.theme.CharcoalBg
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalCard
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.ChartreuseSecondary
import com.example.ui.theme.MutedWarmGray
import com.example.ui.theme.OffWhiteText
import com.example.ui.theme.WarmAmberPrimary

@Composable
fun SettleUpDialog(
    isOpen: Boolean,
    person: PersonEntity?,
    suggestedAmount: Double = 0.0,
    onConfirm: (fromPersonId: Long, toPersonId: Long, amount: Double, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen || person == null) return

    // Direction: 0 = You paid Person (e.g. You paid back or settled debt), 1 = Person paid You
    var direction by remember { mutableStateOf(if (suggestedAmount < 0) 0 else 1) }
    var amountText by remember {
        mutableStateOf(
            if (kotlin.math.abs(suggestedAmount) > 0) String.format("%.2f", kotlin.math.abs(suggestedAmount)) else ""
        )
    }
    var paymentMethod by remember { mutableStateOf("GPay") }
    var noteText by remember { mutableStateOf("Settled up via GPay") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = CharcoalCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settle Balance",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = OffWhiteText,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MutedWarmGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direction Toggle (Who paid whom)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalElevated)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (direction == 0) WarmAmberPrimary else Color.Transparent)
                            .clickable { direction = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You paid ${person.name}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (direction == 0) CharcoalBg else MutedWarmGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (direction == 1) ChartreuseSecondary else Color.Transparent)
                            .clickable { direction = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${person.name} paid You",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (direction == 1) CharcoalBg else MutedWarmGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Settlement Amount", color = MutedWarmGray) },
                    prefix = { Text("₹", color = WarmAmberPrimary, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settle_amount_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WarmAmberPrimary,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedContainerColor = CharcoalElevated,
                        unfocusedContainerColor = CharcoalElevated,
                        cursorColor = WarmAmberPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note / Memo", color = MutedWarmGray) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = OffWhiteText),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WarmAmberPrimary,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedContainerColor = CharcoalElevated,
                        unfocusedContainerColor = CharcoalElevated,
                        cursorColor = WarmAmberPrimary
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Confirm Button
                val valid = (amountText.toDoubleOrNull() ?: 0.0) > 0
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        if (direction == 0) {
                            // You paid person
                            onConfirm(-1L, person.id, amt, noteText)
                        } else {
                            // Person paid you
                            onConfirm(person.id, -1L, amt, noteText)
                        }
                    },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_settle_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (direction == 1) ChartreuseSecondary else WarmAmberPrimary,
                        contentColor = CharcoalBg
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Payment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Record Settlement",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
