package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.components.ChoiceChip
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

/**
 * Lets a settlement or IOU with a groupmate be recorded in a shared group so it syncs to them.
 * Shows nothing for friends without an account: a record with one can't go in a group, and from 1.3
 * on it backs up privately to this account instead (docs/local-friend-sync-v2.md).
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
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Text(text = "Record in group", style = MaterialTheme.typography.titleSmall)
        if (options.canSync) {
            Text(
                text = if (selectedGroupId == null) {
                    "Choose the group this belongs to so $otherPersonName sees it too."
                } else {
                    "Syncs to everyone in the group."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                options.groups.forEach { group ->
                    ChoiceChip(
                        label = "${group.emoji} ${group.name}",
                        selected = group.id == selectedGroupId,
                        onClick = { onSelectGroup(group.id) },
                    )
                }
            }
        } else {
            Text(
                text = "You and $otherPersonName aren't in an active shared group, so this would stay on this phone. " +
                    "Create one and invite them to keep it in sync.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onCreateGroup, modifier = Modifier.offset(x = -Spacing.m)) {
                Text("Create a shared group", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        }
    }
}
