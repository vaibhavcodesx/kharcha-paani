package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import com.vaibhav.kharchapaani.ui.components.GlassActionBar
import com.vaibhav.kharchapaani.ui.components.KpModalSheet
import com.vaibhav.kharchapaani.ui.components.SheetScaffold
import com.vaibhav.kharchapaani.ui.components.SheetTitle
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import androidx.compose.foundation.border

@Composable
fun JoinGroupSheet(
    state: JoinGroupState?,
    onInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == null) return
    KpModalSheet(onDismiss = onDismiss) { JoinGroupContent(state, onInputChange, onJoin) }
}

/** Paste a link or type a code; the group previews before anything is joined. */
@Composable
fun JoinGroupContent(
    state: JoinGroupState,
    onInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = state.preview?.let { stringResource(R.string.shared_group_copy_join_named, it.groupName) } ?: stringResource(R.string.shared_group_copy_join),
                disabledLabel = if (state.isJoining) stringResource(R.string.shared_group_copy_joining) else state.preview?.let { stringResource(R.string.shared_group_copy_join_named, it.groupName) } ?: stringResource(R.string.shared_group_copy_join),
                enabled = state.canJoin,
                onClick = onJoin,
            )
        },
    ) {
        SheetTitle(title = stringResource(R.string.shared_group_copy_join_title), supporting = stringResource(R.string.shared_group_copy_join_support))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clip(KpShapes.medium)
                .background(CharcoalCard)
                .border(1.dp, if (state.error != null) WarmAmberLight else OutlineInteractive, KpShapes.medium)
                .padding(horizontal = Spacing.l),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (state.input.isEmpty()) Text(stringResource(R.string.shared_group_copy_input), style = MaterialTheme.typography.bodyLarge, color = SubMutedWarmGray)
            BasicTextField(
                value = state.input,
                onValueChange = onInputChange,
                singleLine = true,
                textStyle = MoneyText.row.copy(color = OffWhiteText),
                cursorBrush = SolidColor(WarmAmberPrimary),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = WarmAmberLight,
                modifier = Modifier.padding(top = Spacing.s).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        when {
            state.isLoadingPreview -> Box(Modifier.fillMaxWidth().padding(Spacing.xl), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
            state.preview != null -> InvitePreviewCard(state.preview, Modifier.padding(top = Spacing.l))
        }
    }
}

@Composable
private fun InvitePreviewCard(preview: InvitePreview, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(Spacing.l),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clip(SquircleShape(15.dp)).background(WarmAmberPrimary.copy(alpha = 0.16f)),
        ) {
            Text(preview.groupEmoji ?: "👥", style = MaterialTheme.typography.headlineMedium)
        }
        Column(Modifier.padding(start = Spacing.m)) {
            Text(preview.groupName, style = MaterialTheme.typography.titleMedium)
            val members = pluralStringResource(R.plurals.shared_group_copy_members, preview.memberCount, preview.memberCount)
            val inviter = preview.inviterName?.let { stringResource(R.string.shared_group_copy_inviter, it) }.orEmpty()
            Text(stringResource(R.string.shared_group_copy_preview_summary, inviter, members), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Preview
@Composable
private fun JoinGroupContentPreview() {
    SplitFlowTheme {
        Box(Modifier.background(CharcoalSurface)) {
            JoinGroupContent(
                state = JoinGroupState(input = "AB2CD3EF", preview = InvitePreview("g", "Goa trip", "🏖️", "Bob", 3)),
                onInputChange = {},
                onJoin = {},
            )
        }
    }
}
