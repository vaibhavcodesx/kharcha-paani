package com.vaibhav.kharchapaani.ui.groups

import com.vaibhav.kharchapaani.ui.theme.LocalMyColour
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.ui.components.PersonAvatar
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
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
    unsyncedChanges: Int,
    onSyncNow: () -> Unit,
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
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.shared_group_copy_panel_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                text = when {
                    group.isArchived -> stringResource(R.string.shared_group_copy_archived)
                    group.syncState == SyncState.SYNCED -> stringResource(R.string.shared_group_copy_synced)
                    group.syncState == SyncState.FAILED -> stringResource(R.string.shared_group_copy_failed)
                    else -> stringResource(R.string.shared_group_copy_syncing)
                },
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    group.isArchived -> MutedWarmGray
                    group.syncState == SyncState.SYNCED -> ChartreuseSecondary
                    group.syncState == SyncState.FAILED -> WarmAmberLight
                    else -> MutedWarmGray
                },
            )
        }

        Text(stringResource(R.string.shared_group_copy_people, members.size + 1), style = EyebrowText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            MemberChip(name = stringResource(R.string.shared_group_copy_you), colorHex = LocalMyColour.current.hex)
            members.forEach { MemberChip(name = it.name, colorHex = it.avatarColor, emoji = it.avatarEmoji) }
        }

        if (!group.isArchived) {
            InviteSection(invite, onCreateInvite, onShareInvite, onRevokeInvite)
        } else {
            Text(stringResource(R.string.shared_group_copy_readonly), style = MaterialTheme.typography.bodyMedium)
        }

        Row(Modifier.offset(x = -Spacing.m)) {
            if (isCreator) {
                TextButton(onClick = { onSetArchived(!group.isArchived) }) {
                    Text(if (group.isArchived) stringResource(R.string.shared_group_copy_restore) else stringResource(R.string.shared_group_copy_archive), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
                }
            }
            TextButton(onClick = { confirmingLeave = true }, enabled = !isLeaving) {
                Text(if (isLeaving) stringResource(R.string.shared_group_copy_leaving) else stringResource(R.string.shared_group_copy_leave_group), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        }
    }

    if (confirmingLeave) {
        AlertDialog(
            onDismissRequest = { confirmingLeave = false },
            containerColor = CharcoalSurface,
            shape = KpShapes.large,
            title = { Text(stringResource(R.string.shared_group_copy_leave_title, group.name), style = MaterialTheme.typography.headlineMedium, color = OffWhiteText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(
                        stringResource(R.string.shared_group_copy_leave_explanation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (unsyncedChanges > 0) {
                        // Leaving first would throw these away: the server refuses them from a non-member.
                        Text(
                            text = pluralStringResource(R.plurals.shared_group_copy_unsynced, unsyncedChanges, unsyncedChanges),
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmAmberLight,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmingLeave = false; onLeave() }) {
                    Text(if (unsyncedChanges > 0) stringResource(R.string.shared_group_copy_leave_anyway) else stringResource(R.string.shared_group_copy_leave), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                }
            },
            dismissButton = {
                if (unsyncedChanges > 0) {
                    TextButton(onClick = { confirmingLeave = false; onSyncNow() }) {
                        Text(stringResource(R.string.shared_group_copy_sync_now), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
                    }
                } else {
                    TextButton(onClick = { confirmingLeave = false }) {
                        Text(stringResource(R.string.shared_group_copy_cancel), style = MaterialTheme.typography.labelLarge, color = MutedWarmGray)
                    }
                }
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
        invite?.isLoading == true -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        link != null && invite.code != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(KpShapes.medium)
                    .background(CharcoalBg)
                    .padding(Spacing.l),
            ) {
                Text(stringResource(R.string.shared_group_copy_invite_code), style = EyebrowText)
                Text(invite.code.chunked(4).joinToString(" "), style = MoneyText.large, color = OffWhiteText)
                invite.expiresAtMillis?.let {
                    Text(
                        text = stringResource(R.string.shared_group_copy_expires, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PanelButton(label = stringResource(R.string.shared_group_copy_share), filled = true, onClick = { onShareInvite(link) }, modifier = Modifier.weight(1f))
                PanelButton(label = stringResource(R.string.shared_group_copy_revoke), filled = false, onClick = onRevokeInvite)
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PanelButton(label = stringResource(R.string.shared_group_copy_invite), filled = false, onClick = onCreateInvite, modifier = Modifier.fillMaxWidth())
            invite?.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = WarmAmberLight) }
        }
    }
}

@Composable
private fun PanelButton(label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(KpShapes.small)
            .background(if (filled) WarmAmberPrimary else CharcoalElevated)
            .then(if (filled) Modifier else Modifier.border(1.dp, OutlineInteractive, KpShapes.small))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.l),
    ) {
        if (filled) Icon(KpIcons.Send, contentDescription = null, tint = CharcoalBg, modifier = Modifier.size(18.dp).padding(end = Spacing.xxs))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (filled) CharcoalBg else OffWhiteText, modifier = Modifier.padding(start = if (filled) Spacing.s else 0.dp))
    }
}

@Composable
private fun MemberChip(name: String, colorHex: String, emoji: String? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(KpShapes.small)
            .background(CharcoalElevated)
            .padding(start = Spacing.xs, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
    ) {
        PersonAvatar(name = name, colorHex = colorHex, size = 26.dp, emoji = emoji)
        Text(name, style = MaterialTheme.typography.labelLarge, color = OffWhiteText, modifier = Modifier.padding(start = Spacing.s))
    }
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
            unsyncedChanges = 0,
            onSyncNow = {},
            onCreateInvite = {},
            onShareInvite = {},
            onRevokeInvite = {},
            onSetArchived = {},
            onLeave = {},
        )
    }
}
