package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * Lets a settlement or IOU with a groupmate be recorded in a shared group so it syncs to them.
 * Shows nothing for friends without an account, whose records stay on this phone as before.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordInGroupSection(
    otherPersonName: String,
    options: SharedRecordGroups.Options,
    selectedGroupId: Long?,
    onSelectGroup: (Long) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!options.involvesGroupmates) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CharcoalElevated)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Record in group",
            style = MaterialTheme.typography.labelLarge.copy(color = OffWhiteText, fontWeight = FontWeight.Bold),
        )
        if (options.canSync) {
            Text(
                text = if (selectedGroupId == null) {
                    "Choose the group this belongs to so $otherPersonName sees it too."
                } else {
                    "Syncs to everyone in the group."
                },
                style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.groups.forEach { group ->
                    FilterChip(
                        selected = group.id == selectedGroupId,
                        onClick = { onSelectGroup(group.id) },
                        label = { Text("${group.emoji} ${group.name}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = WarmAmberPrimary,
                            selectedLabelColor = CharcoalBg,
                            labelColor = OffWhiteText,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = group.id == selectedGroupId,
                            borderColor = CharcoalBorder,
                            selectedBorderColor = WarmAmberPrimary,
                        ),
                    )
                }
            }
        } else {
            Text(
                text = "You and $otherPersonName aren't in an active shared group, so this would stay on this phone. " +
                    "Create one and invite them to keep it in sync.",
                style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray),
            )
            TextButton(onClick = onCreateGroup, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Create a shared group", color = WarmAmberPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
