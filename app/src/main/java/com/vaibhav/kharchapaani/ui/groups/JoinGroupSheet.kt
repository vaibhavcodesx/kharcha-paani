package com.vaibhav.kharchapaani.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinGroupSheet(
    state: JoinGroupState?,
    onInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (state == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CharcoalSurface,
        contentColor = OffWhiteText,
        modifier = Modifier.imePadding(),
    ) {
        JoinGroupContent(state, onInputChange, onJoin)
    }
}

@Composable
fun JoinGroupContent(
    state: JoinGroupState,
    onInputChange: (String) -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .navigationBarsPadding()
    ) {
        Text(
            text = "Join a group",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = OffWhiteText),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Paste the invite link or type the 8-character code someone shared with you.",
            style = MaterialTheme.typography.bodyMedium.copy(color = MutedWarmGray),
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            label = { Text("Invite link or code") },
            singleLine = true,
            isError = state.error != null,
            supportingText = state.error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WarmAmberPrimary,
                unfocusedBorderColor = CharcoalBorder,
                focusedLabelColor = WarmAmberPrimary,
                cursorColor = WarmAmberPrimary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        when {
            state.isLoadingPreview -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WarmAmberPrimary, modifier = Modifier.size(28.dp))
            }
            state.preview != null -> InvitePreviewCard(state.preview)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onJoin,
            enabled = state.canJoin,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WarmAmberPrimary, contentColor = CharcoalBg),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (state.isJoining) {
                CircularProgressIndicator(color = CharcoalBg, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            } else {
                Text(state.preview?.let { "Join ${it.groupName}" } ?: "Join group", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InvitePreviewCard(preview: InvitePreview) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalCard)
            .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(WarmAmberPrimary.copy(alpha = 0.15f)),
        ) {
            Text(preview.groupEmoji ?: "👥", fontSize = 22.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(preview.groupName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = OffWhiteText))
            val members = if (preview.memberCount == 1) "1 member" else "${preview.memberCount} members"
            val inviter = preview.inviterName?.let { "Invited by $it • " }.orEmpty()
            Text("$inviter$members", style = MaterialTheme.typography.bodySmall.copy(color = MutedWarmGray))
        }
    }
}

@Preview
@Composable
private fun JoinGroupContentPreview() {
    SplitFlowTheme {
        Box(Modifier.background(CharcoalSurface)) {
            JoinGroupContent(
                state = JoinGroupState(
                    input = "AB2CD3EF",
                    preview = InvitePreview("g", "Goa trip", "🏖️", "Bob", 3),
                ),
                onInputChange = {},
                onJoin = {},
            )
        }
    }
}
