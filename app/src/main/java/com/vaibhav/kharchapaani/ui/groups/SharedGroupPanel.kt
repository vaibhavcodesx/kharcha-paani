package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.text.DateFormat
import java.util.Date

/** Members, invites, archiving and leaving for a shared group, shown inside its detail sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SharedGroupPanel(
    group: GroupEntity,
    members: List<PersonEntity>,
    isCreator: Boolean,
    invite: GroupInviteState?,
    isLeaving: Boolean,
    onCreateInvite: () -> Unit,
    onShareInvite: (link: String) -> Unit,
    onRevokeInvite: () -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Shared group",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = OffWhiteText),
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            StatusChip(
                text = when {
                    group.isArchived -> "Archived"
                    group.syncState == SyncState.SYNCED -> "Synced"
                    group.syncState == SyncState.FAILED -> "Not synced"
                    else -> "Syncing…"
                },
                color = when {
                    group.isArchived -> MutedWarmGray
                    group.syncState == SyncState.SYNCED -> ChartreuseSecondary
                    group.syncState == SyncState.FAILED -> WarmAmberPrimary
                    else -> MutedWarmGray
                },
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusChip("You", OffWhiteText)
            members.forEach { StatusChip(it.name, OffWhiteText) }
        }

        if (!group.isArchived) {
            InviteSection(invite, onCreateInvite, onShareInvite, onRevokeInvite)
        } else {
            Text(
                text = "Archived groups are read-only and can't take new members.",
                style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isCreator) {
                TextButton(onClick = { onSetArchived(!group.isArchived) }) {
                    Text(if (group.isArchived) "Restore group" else "Archive group", color = MutedWarmGray)
                }
            }
            TextButton(onClick = { confirmingLeave = true }, enabled = !isLeaving) {
                Text(if (isLeaving) "Leaving…" else "Leave group", color = WarmAmberPrimary)
            }
        }
    }

    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = { confirmingLeave = false },
            containerColor = CharcoalSurface,
            title = { Text("Leave ${group.name}?") },
            text = {
                Text("Its expenses and settlements will be removed from this phone. Other members keep them, and you can rejoin with a new invite.")
            },
            confirmButton = {
                TextButton(onClick = { confirmingLeave = false; onLeave() }) { Text("Leave", color = WarmAmberPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingLeave = false }) { Text("Cancel", color = MutedWarmGray) }
            },
        )
    }
}

@Composable
private fun InviteSection(
    invite: GroupInviteState?,
    onCreateInvite: () -> Unit,
    onShareInvite: (String) -> Unit,
    onRevokeInvite: () -> Unit,
) {
    val link = invite?.link
    when {
        invite?.isLoading == true -> CircularProgressIndicator(color = WarmAmberPrimary, modifier = Modifier.size(24.dp))
        link != null && invite.code != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CharcoalElevated)
                    .padding(12.dp),
            ) {
                Column {
                    Text(
                        text = invite.code.chunked(4).joinToString(" "),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, color = OffWhiteText),
                    )
                    invite.expiresAtMillis?.let {
                        Text(
                            text = "Anyone with this code can join until ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}.",
                            style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onShareInvite(link) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary, contentColor = CharcoalBg),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share link", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onRevokeInvite, shape = RoundedCornerShape(12.dp)) {
                    Text("Turn off", color = MutedWarmGray)
                }
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onCreateInvite, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Invite people", color = OffWhiteText, fontWeight = FontWeight.Bold)
            }
            invite?.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall.copy(color = WarmAmberPrimary))
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Preview
@Composable
private fun SharedGroupPanelPreview() {
    SplitFlowTheme {
        SharedGroupPanel(
            group = GroupEntity(name = "Goa trip", syncState = SyncState.SYNCED),
            members = listOf(PersonEntity(name = "Bob", avatarColor = "#70C1FF"), PersonEntity(name = "Carol", avatarColor = "#FFB3BA")),
            isCreator = true,
            invite = GroupInviteState(groupId = 1, code = "AB2CD3EF", expiresAtMillis = 1_800_000_000_000),
            isLeaving = false,
            onCreateInvite = {},
            onShareInvite = {},
            onRevokeInvite = {},
            onSetArchived = {},
            onLeave = {},
        )
    }
}
