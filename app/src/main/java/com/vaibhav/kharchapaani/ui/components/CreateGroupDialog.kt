package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

private val EMOJI_OPTIONS = listOf("⛰️", "🏖️", "🚗", "🏠", "🍕", "🎉", "✈️", "🍿", "💼", "🏕️")
private const val MAX_SHARED_NAME = 60
private val COLOR_OPTIONS = listOf("#FF7A3D", "#C6F24C", "#70C1FF", "#FFB3BA", "#818CF8", "#FBBF24")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupDialog(
    isOpen: Boolean,
    allPeople: List<PersonEntity>,
    initialShared: Boolean = false,
    onSaveGroup: (name: String, description: String, emoji: String, colorHex: String, memberIds: List<Long>, shared: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var groupName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("⛰️") }
    var selectedColor by remember { mutableStateOf("#FF7A3D") }
    val selectedMemberIds = remember { mutableStateListOf<Long>() }
    var isShared by remember { mutableStateOf(initialShared) }
    // The server caps shared group names at 60 characters.
    val nameIsValid = groupName.isNotBlank() && (!isShared || groupName.trim().length <= MAX_SHARED_NAME)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(1.5.dp, CharcoalBorder, RoundedCornerShape(24.dp)),
            color = CharcoalSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(WarmAmberPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = selectedEmoji, fontSize = 20.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Create Trip / Group",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = OffWhiteText
                                )
                            )
                            Text(
                                text = "Track shared trip & flat expenses",
                                style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MutedWarmGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Group Name
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name (e.g. Lonavala 2026)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WarmAmberPrimary,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedLabelColor = WarmAmberPrimary,
                        unfocusedLabelColor = MutedWarmGray,
                        focusedTextColor = OffWhiteText,
                        unfocusedTextColor = OffWhiteText,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description / Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WarmAmberPrimary,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedLabelColor = WarmAmberPrimary,
                        unfocusedLabelColor = MutedWarmGray,
                        focusedTextColor = OffWhiteText,
                        unfocusedTextColor = OffWhiteText,
                        focusedContainerColor = CharcoalCard,
                        unfocusedContainerColor = CharcoalCard
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Emoji Picker Row
                Text(
                    text = "Pick Icon Emoji",
                    style = MaterialTheme.typography.labelMedium.copy(color = SubMutedWarmGray)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EMOJI_OPTIONS.forEach { emoji ->
                        val isSelected = selectedEmoji == emoji
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) WarmAmberPrimary.copy(alpha = 0.25f) else CharcoalCard)
                                .border(
                                    1.5.dp,
                                    if (isSelected) WarmAmberPrimary else CharcoalBorder,
                                    CircleShape
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalCard)
                        .toggleable(value = isShared, role = Role.Switch, onValueChange = { isShared = it })
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Share with others",
                            style = MaterialTheme.typography.titleSmall.copy(color = OffWhiteText, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = if (isShared) {
                                "Everyone you invite sees and adds expenses. Invite them by link once it's created."
                            } else {
                                "Only on this phone. Friends here don't need the app."
                            },
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = isShared,
                        onCheckedChange = null,
                        colors = SwitchDefaults.colors(checkedTrackColor = WarmAmberPrimary, checkedThumbColor = CharcoalBg)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // A shared group's members join through invites, not from the local friends list.
                if (!isShared) {
                    // Members Multi-Select
                    Text(
                        text = "Select Group Members (You are included)",
                        style = MaterialTheme.typography.labelMedium.copy(color = SubMutedWarmGray)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (allPeople.isEmpty()) {
                        Text(
                            text = "No friends added yet. You can add more members later.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray)
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            allPeople.forEach { person ->
                                val isSelected = selectedMemberIds.contains(person.id)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (isSelected) {
                                            selectedMemberIds.remove(person.id)
                                        } else {
                                            selectedMemberIds.add(person.id)
                                        }
                                    },
                                    label = { Text(person.name) },
                                    leadingIcon = {
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ChartreuseSecondary,
                                        selectedLabelColor = CharcoalBg,
                                        selectedLeadingIconColor = CharcoalBg,
                                        containerColor = CharcoalCard,
                                        labelColor = OffWhiteText
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = CharcoalBorder,
                                        selectedBorderColor = ChartreuseSecondary
                                    )
                                )
                            }
                        }
                    }
                } else if (groupName.trim().length > MAX_SHARED_NAME) {
                    Text(
                        text = "Shared group names can be up to $MAX_SHARED_NAME characters.",
                        style = MaterialTheme.typography.bodySmall.copy(color = WarmAmberPrimary)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CharcoalElevated,
                            contentColor = MutedWarmGray
                        )
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            if (nameIsValid) {
                                onSaveGroup(
                                    groupName,
                                    description,
                                    selectedEmoji,
                                    selectedColor,
                                    if (isShared) emptyList() else selectedMemberIds.toList(),
                                    isShared
                                )
                            }
                        },
                        enabled = nameIsValid,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_group_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WarmAmberPrimary,
                            contentColor = CharcoalBg
                        )
                    ) {
                        Text("Create Group", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
