package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.sync.SyncBadge
import com.vaibhav.kharchapaani.ui.sync.SyncBadgeOverlay
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.LocalMyColour

/**
 * The way into the account, and where sync says how it is doing.
 *
 * It lived inside Home's header and appeared on Home only, so Splits and Insights had no way to
 * reach the account and no sync state at all — you had to go back to Home to find out whether your
 * expenses were saved. Every top-level tab now carries the same chrome.
 */
@Composable
fun AccountAvatar(
    avatar: String,
    syncStatus: SyncStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accountDescription = stringResource(R.string.shared_chrome_account)
    val syncDescription = SyncBadge.describe(LocalContext.current, syncStatus)
    val mine = LocalMyColour.current.color
    // One clickable rather than IconButton + bounceClick, so TalkBack sees a single "Account"
    // button. Compose expands the touch target to 48dp.
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(48.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(mine.copy(alpha = 0.18f))
                .bounceClick(onClick = onClick)
                .semantics {
                    contentDescription = accountDescription
                    role = Role.Button
                    if (syncDescription != null) stateDescription = syncDescription
                }
                .testTag("home_account_button"),
        ) {
            Text(
                text = avatar,
                style = MaterialTheme.typography.titleLarge,
                color = mine,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        SyncBadgeOverlay(syncStatus, avatarSize = 40.dp)
    }
}
