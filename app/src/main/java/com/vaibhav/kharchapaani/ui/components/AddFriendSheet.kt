package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.domain.Hues
import com.vaibhav.kharchapaani.domain.PersonColours
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape

/**
 * Eight hues spread right around the wheel, so no two people can draw near-neighbours. The old set
 * had Sky next to Ice and Lime next to Pistachio, and Rose next to Coral, which is how a group of
 * three ended up with three avatars and a donut that all read as the same brown.
 *
 * None of them sits near the brand amber, which is reserved for "You".
 */
private val ColourNames = listOf("Sand", "Lime", "Green", "Mint", "Sky", "Violet", "Orchid", "Rose")

/**
 * The picker's swatches: [PersonColours.palette] with names on. One list, so a colour chosen here
 * and a colour assigned by sync can never drift apart.
 */
val FriendColours: List<Pair<String, String>> = PersonColours.palette.zip(ColourNames)

@Composable
fun AddFriendSheet(isOpen: Boolean, onAdd: (name: String, colorHex: String) -> Unit, onDismiss: () -> Unit, takenColours: List<String> = emptyList()) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { AddFriendContent(onAdd, takenColours = takenColours) }
}

/** A friend is just a name and a colour; the avatar previews both as you type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFriendContent(
    onAdd: (name: String, colorHex: String) -> Unit,
    modifier: Modifier = Modifier,
    // The colours of everyone already on this phone, you included: the field keeps clear of them.
    takenColours: List<String> = emptyList(),
) {
    val taken = remember(takenColours) { takenColours.mapNotNull(Hues::slotOf) }
    var name by rememberSaveable { mutableStateOf("") }
    var hue by rememberSaveable { mutableStateOf(-1) }
    var shade by rememberSaveable { mutableStateOf(1) }
    if (hue < 0) (Hues.firstFreeSlot("new-friend", taken) ?: Hues.Slot(243, 1)).let { hue = it.hue; shade = it.shade }
    val slot = Hues.Slot(hue, shade)
    val colour = slot.hex

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = stringResource(R.string.friend_form_add),
                disabledLabel = stringResource(R.string.friend_form_disabled),
                enabled = name.isNotBlank(),
                onClick = { onAdd(name.trim(), colour) },
                buttonTestTag = "confirm_add_friend_button",
            )
        },
    ) {
        SheetTitle(
            title = stringResource(R.string.friend_form_title),
            supporting = stringResource(R.string.friend_form_supporting),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            PersonAvatar(name = name.ifBlank { "?" }, colorHex = colour, size = 56.dp)
            SheetRow(label = stringResource(R.string.friend_form_name), modifier = Modifier.weight(1f)) {
                SheetTextInput(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.friend_form_placeholder), fieldTestTag = "add_friend_name_input")
            }
        }
        Text(stringResource(R.string.friend_form_colour), style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s))
        // Any colour along the spectrum, kept clear of everyone else's (design item 11).
        FriendColourField(
            selected = slot,
            taken = taken,
            onPick = { hue = it.hue; shade = it.shade },
            whose = name.trim().takeIf { it.isNotEmpty() }?.let { stringResource(R.string.friend_colour_possessive, it) } ?: stringResource(R.string.friend_colour_their),
        )
    }
}
