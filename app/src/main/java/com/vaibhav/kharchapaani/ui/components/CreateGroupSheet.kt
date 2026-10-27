package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

private val GroupIcons = listOf("⛰️", "🏖️", "🚗", "🏠", "🍕", "🎉", "✈️", "🍿", "💼", "🏕️")
private const val MAX_SHARED_NAME = 60
private const val DEFAULT_COLOUR = "#FF7A3D"

@Composable
fun CreateGroupSheet(
    isOpen: Boolean,
    allPeople: List<PersonEntity>,
    initialShared: Boolean = false,
    onSaveGroup: (name: String, description: String, emoji: String, colorHex: String, memberIds: List<Long>, shared: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { CreateGroupContent(allPeople, initialShared, onSaveGroup) }
}

/**
 * A group for a trip, a flat or anything split more than once. Local groups take members from the
 * friends on this phone; shared groups get theirs by invite link once created, so that part of the
 * form disappears when Share is on.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateGroupContent(
    allPeople: List<PersonEntity>,
    initialShared: Boolean,
    onSaveGroup: (name: String, description: String, emoji: String, colorHex: String, memberIds: List<Long>, shared: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var about by rememberSaveable { mutableStateOf("") }
    var icon by rememberSaveable { mutableStateOf(GroupIcons.first()) }
    var shared by rememberSaveable { mutableStateOf(initialShared) }
    val memberIds = remember { emptyList<Long>().toMutableStateList() }
    // The server caps shared group names at 60 characters.
    val tooLong = shared && name.trim().length > MAX_SHARED_NAME

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = stringResource(R.string.create_group_save),
                disabledLabel = if (tooLong) stringResource(R.string.create_group_too_long) else stringResource(R.string.create_group_disabled),
                enabled = name.isNotBlank() && !tooLong,
                onClick = { onSaveGroup(name.trim(), about.trim(), icon, DEFAULT_COLOUR, if (shared) emptyList() else memberIds.toList(), shared) },
                buttonTestTag = "save_group_button",
            )
        },
    ) {
        SheetTitle(title = stringResource(R.string.create_group_title), supporting = stringResource(R.string.create_group_supporting))

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetRow(label = stringResource(R.string.create_group_name), position = RowPosition.First) {
                SheetTextInput(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.create_group_placeholder), fieldTestTag = "group_name_input")
            }
            SheetRow(label = stringResource(R.string.create_group_about), position = RowPosition.Last) {
                SheetTextInput(value = about, onValueChange = { about = it }, placeholder = stringResource(R.string.create_group_optional))
            }
        }
        if (tooLong) {
            Text(
                stringResource(R.string.create_group_limit, MAX_SHARED_NAME),
                style = MaterialTheme.typography.bodyMedium,
                color = WarmAmberLight,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }

        Text(stringResource(R.string.create_group_icon), style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            GroupIcons.forEach { emoji ->
                val selected = emoji == icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(IconShape)
                        .background(if (selected) WarmAmberPrimary.copy(alpha = 0.18f) else CharcoalCard)
                        .border(if (selected) 2.dp else 1.dp, if (selected) WarmAmberPrimary else OutlineInteractive, IconShape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { icon = emoji }),
                ) {
                    Text(emoji, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(top = Spacing.xl)
                .fillMaxWidth()
                .clip(KpShapes.large)
                .background(CharcoalCard)
                .toggleable(value = shared, role = Role.Switch, onValueChange = { shared = it })
                .padding(Spacing.l),
        ) {
            Column(Modifier.weight(1f).padding(end = Spacing.m)) {
                Text(stringResource(R.string.create_group_share), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (shared) {
                        stringResource(R.string.create_group_shared)
                    } else {
                        stringResource(R.string.create_group_local)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Switch(
                checked = shared,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = WarmAmberPrimary,
                    checkedThumbColor = CharcoalBg,
                    uncheckedTrackColor = CharcoalElevated,
                    uncheckedThumbColor = MutedWarmGray,
                    uncheckedBorderColor = OutlineInteractive,
                ),
            )
        }

        if (!shared) {
            Text(stringResource(R.string.create_group_members), style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s))
            if (allPeople.isEmpty()) {
                Text(stringResource(R.string.create_group_no_friends), style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    allPeople.forEach { person ->
                        val selected = person.id in memberIds
                        ChoiceChip(
                            label = person.name,
                            selected = selected,
                            icon = if (selected) KpIcons.Check else null,
                            accent = ChartreuseSecondary,
                            role = Role.Checkbox,
                            onClick = { if (selected) memberIds.remove(person.id) else memberIds.add(person.id) },
                        )
                    }
                }
            }
        }
    }
}

private val IconShape = SquircleShape(16.dp)
