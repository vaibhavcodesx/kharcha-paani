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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.icons.KpIcons
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
val FriendColours = listOf(
    "#F2D06B" to "Sand", "#C6F24C" to "Lime", "#86E37F" to "Green", "#5BD6B0" to "Mint",
    "#70C1FF" to "Sky", "#A79BF5" to "Violet", "#E092E8" to "Orchid", "#FF8FB0" to "Rose",
)

@Composable
fun AddFriendSheet(isOpen: Boolean, onAdd: (name: String, colorHex: String) -> Unit, onDismiss: () -> Unit) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { AddFriendContent(onAdd) }
}

/** A friend is just a name and a colour; the avatar previews both as you type. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFriendContent(onAdd: (name: String, colorHex: String) -> Unit, modifier: Modifier = Modifier) {
    var name by rememberSaveable { mutableStateOf("") }
    var colour by rememberSaveable { mutableStateOf(FriendColours.first().first) }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = "Add friend",
                disabledLabel = "Type their name",
                enabled = name.isNotBlank(),
                onClick = { onAdd(name.trim(), colour) },
                buttonTestTag = "confirm_add_friend_button",
            )
        },
    ) {
        SheetTitle(
            title = "Add a friend",
            supporting = "They don't need the app. To split with them across phones, share a group with them later.",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            PersonAvatar(name = name.ifBlank { "?" }, colorHex = colour, size = 56.dp)
            SheetRow(label = "Name", modifier = Modifier.weight(1f)) {
                SheetTextInput(value = name, onValueChange = { name = it }, placeholder = "Rohan", fieldTestTag = "add_friend_name_input")
            }
        }
        Text("COLOUR", style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), modifier = Modifier.fillMaxWidth().selectableGroup()) {
            FriendColours.forEach { (hex, label) ->
                val selected = hex == colour
                val swatch = parseColor(hex)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SwatchShape)
                        .background(swatch)
                        .then(if (selected) Modifier.border(3.dp, OffWhiteText, SwatchShape) else Modifier)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { colour = hex })
                        .semantics { contentDescription = label },
                ) {
                    if (selected) Icon(KpIcons.Check, contentDescription = null, tint = CharcoalBg, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private val SwatchShape = SquircleShape(16.dp)
