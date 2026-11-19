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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.ui.components.PersonAvatar
import com.vaibhav.kharchapaani.ui.components.QrCode
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
import com.vaibhav.kharchapaani.ui.format.Money
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

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
    onRemoveMember: ((profileId: String) -> Unit)? = null,
    isRemovingMember: Boolean = false,
    outstandingMembers: Set<Long> = emptySet(),
    /** What I am owed (positive) or owe (negative) in this group, in rupees. Shown before leaving. */
    myBalance: Double = 0.0,
    onResetInvite: (() -> Unit)? = null,
) {
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    var memberToRemoveId by rememberSaveable(group.id) { mutableStateOf<Long?>(null) }
    val canRemove = isCreator && !group.isArchived && onRemoveMember != null

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
            members.forEach { member ->
                MemberChip(
                    name = member.name,
                    colorHex = member.avatarColor,
                    emoji = member.avatarEmoji,
                    onRemove = if (canRemove && !member.profileId.isNullOrBlank()) {
                        { memberToRemoveId = member.id }
                    } else null,
                    removeEnabled = !isRemovingMember,
                )
            }
        }

        if (!group.isArchived) {
            InviteSection(invite, onCreateInvite, onShareInvite, onRevokeInvite, onResetInvite)
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

    val memberToRemove = if (canRemove) members.firstOrNull { it.id == memberToRemoveId } else null
    val selectedProfile = memberToRemove?.profileId
    if (memberToRemove != null && selectedProfile != null) {
        AlertDialog(
            onDismissRequest = { memberToRemoveId = null },
            containerColor = CharcoalSurface,
            shape = KpShapes.large,
            title = { Text(stringResource(R.string.shared_group_remove_title, memberToRemove.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    Text(stringResource(R.string.shared_group_remove_detail))
                    if (memberToRemove.id in outstandingMembers) {
                        Text(stringResource(R.string.shared_group_remove_balance), color = WarmAmberLight)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isRemovingMember,
                    onClick = {
                        memberToRemoveId = null
                        onRemoveMember?.invoke(selectedProfile)
                    },
                ) { Text(stringResource(R.string.shared_group_remove_button), color = WarmAmberLight) }
            },
            dismissButton = {
                TextButton(onClick = { memberToRemoveId = null }) {
                    Text(stringResource(R.string.shared_group_copy_cancel))
                }
            },
        )
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
                    if (abs(myBalance) >= 0.005) {
                        // Leaving does not settle anything, so the person should see what is left unsettled.
                        Text(
                            text = stringResource(
                                if (myBalance > 0) R.string.shared_group_copy_leave_owed else R.string.shared_group_copy_leave_owe,
                                Money.format(abs(myBalance)),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = WarmAmberLight,
                        )
                    }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InviteSection(
    invite: GroupInviteState?,
    onCreateInvite: () -> Unit,
    onShareInvite: (String) -> Unit,
    onRevokeInvite: () -> Unit,
    onResetInvite: (() -> Unit)?,
) {
    val link = invite?.link
    when {
        invite?.isLoading == true -> Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
        link != null && invite.code != null -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            var showQr by rememberSaveable(link) { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(KpShapes.medium)
                    .background(CharcoalBg)
                    .padding(Spacing.l),
            ) {
                Text(stringResource(R.string.shared_group_copy_invite_code), style = EyebrowText)
                Text(invite.code.chunked(4).joinToString(" "), style = MoneyText.large, color = OffWhiteText)
                invite.usage?.let {
                    Text(
                        stringResource(R.string.shared_group_copy_invite_uses, it.usedCount, it.maxUses),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                invite.expiresAtMillis?.let {
                    Text(
                        text = stringResource(R.string.shared_group_copy_expires, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                PanelButton(label = stringResource(R.string.shared_group_copy_share), filled = true, onClick = { onShareInvite(link) }, modifier = Modifier.weight(1f))
                PanelButton(
                    label = stringResource(if (showQr) R.string.shared_group_qr_hide else R.string.shared_group_qr_show),
                    filled = false,
                    onClick = { showQr = !showQr },
                    modifier = Modifier.weight(1f),
                )
            }
            if (showQr) {
                QrCode(
                    text = link,
                    contentDescription = stringResource(R.string.shared_group_qr_description),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    size = 168.dp,
                )
            }
            FlowRow(
                modifier = Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End),
            ) {
                if (invite.canReset && onResetInvite != null) {
                    PanelButton(stringResource(R.string.shared_group_copy_reset), filled = false, onClick = onResetInvite)
                }
                PanelButton(stringResource(R.string.shared_group_copy_revoke), filled = false, onClick = onRevokeInvite)
            }
            invite.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = WarmAmberLight) }
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
private fun MemberChip(
    name: String,
    colorHex: String,
    emoji: String? = null,
    onRemove: (() -> Unit)? = null,
    removeEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(KpShapes.small)
            .background(CharcoalElevated)
            .padding(start = Spacing.xs, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs),
    ) {
        PersonAvatar(name = name, colorHex = colorHex, size = 26.dp, emoji = emoji)
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            color = OffWhiteText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false).padding(start = Spacing.s),
        )
        if (onRemove != null) {
            IconButton(onClick = onRemove, enabled = removeEnabled) {
                Icon(
                    KpIcons.Close,
                    contentDescription = stringResource(R.string.shared_group_remove_person, name),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
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
