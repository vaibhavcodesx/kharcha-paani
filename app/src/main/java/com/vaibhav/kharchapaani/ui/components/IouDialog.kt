package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Handshake
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
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

@Composable
fun IouDialog(
    isOpen: Boolean,
    people: List<PersonEntity>,
    onConfirm: (fromPersonId: Long, toPersonId: Long, amount: Double, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var selectedPersonId by remember { mutableStateOf(people.firstOrNull()?.id ?: -1L) }
    // Type: 0 = "I lent money to friend" (Friend owes me), 1 = "I borrowed money from friend" (I owe friend)
    var iouType by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Handshake,
                            contentDescription = null,
                            tint = WarmAmberPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Log Direct IOU / Loan",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = OffWhiteText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MutedWarmGray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Toggle
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
                            .background(if (iouType == 0) WarmAmberPrimary else Color.Transparent)
                            .clickable { iouType = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "I Lent Money",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (iouType == 0) CharcoalBg else MutedWarmGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (iouType == 1) ChartreuseSecondary else Color.Transparent)
                            .clickable { iouType = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "I Borrowed Money",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = if (iouType == 1) CharcoalBg else MutedWarmGray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Person Picker
                Text(
                    text = if (iouType == 0) "Lent To:" else "Borrowed From:",
                    style = MaterialTheme.typography.titleSmall.copy(color = MutedWarmGray)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    people.forEach { person ->
                        val isSelected = selectedPersonId == person.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) WarmAmberPrimary.copy(alpha = 0.2f) else CharcoalSurface,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, WarmAmberPrimary) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalBorder),
                            modifier = Modifier.clickable { selectedPersonId = person.id }
                        ) {
                            Text(
                                text = person.name,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = if (isSelected) WarmAmberPrimary else OffWhiteText,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount", color = MutedWarmGray) },
                    prefix = { Text("₹", color = WarmAmberPrimary, fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        color = OffWhiteText,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("iou_amount_input"),
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

                // Reason / Note
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Reason (e.g. Project domain, Hackathon food)", color = MutedWarmGray) },
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
                val valid = (amountText.toDoubleOrNull() ?: 0.0) > 0 && selectedPersonId != -1L
                Button(
                    onClick = {
                        val amt = amountText.toDoubleOrNull() ?: 0.0
                        val reason = noteText.ifBlank { if (iouType == 0) "Informal Loan" else "Borrowed Amount" }
                        if (iouType == 0) {
                            // I lent money to friend (I paid -> to friend)
                            onConfirm(-1L, selectedPersonId, amt, reason)
                        } else {
                            // Friend lent money to me (Friend paid -> to Me)
                            onConfirm(selectedPersonId, -1L, amt, reason)
                        }
                    },
                    enabled = valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_iou_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (iouType == 0) WarmAmberPrimary else ChartreuseSecondary,
                        contentColor = CharcoalBg
                    )
                ) {
                    Text(
                        "Record IOU",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}
