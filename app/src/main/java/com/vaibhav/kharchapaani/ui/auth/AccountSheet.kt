package com.vaibhav.kharchapaani.ui.auth

import android.text.format.DateUtils
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.components.SheetHandle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.Profile
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberContainer
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.ui.theme.displayFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSheet(
    state: AuthUiState,
    syncStatus: SyncStatus,
    onRetryFailedSync: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CharcoalSurface,
        shape = KpShapes.sheet,
        dragHandle = { SheetHandle() },
    ) {
        AccountSheetContent(
            state = state,
            syncStatus = syncStatus,
            onRetryFailedSync = onRetryFailedSync,
            onRetryProfile = onRetryProfile,
            onSignOut = onSignOut,
        )
    }
}

@Composable
private fun AccountSheetContent(
    state: AuthUiState,
    syncStatus: SyncStatus,
    onRetryFailedSync: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(WarmAmberContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.accountInitial,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = WarmAmberLight,
                        fontFamily = displayFontFamily,
                    ),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.displayName ?: "Your account",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.email?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        CloudStatusRow(
            profile = state.profile,
            isRefreshPending = (state.authState as? AuthState.SignedIn)?.isRefreshPending == true,
            onRetry = onRetryProfile,
        )

        Spacer(Modifier.height(8.dp))

        SyncStatusRow(status = syncStatus, onRetryFailed = onRetryFailedSync)

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Personal expenses sync to your account. Splits and group expenses stay on this phone " +
                "until shared groups arrive. Signing out keeps everything already here.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(14.dp),
            // SubMutedWarmGray, not CharcoalBorder: the boundary needs 3:1 against the sheet (3.17 vs 1.28).
            border = BorderStroke(1.dp, SubMutedWarmGray),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("account_sign_out"),
        ) {
            Text(
                text = "Sign out",
                style = MaterialTheme.typography.titleMedium.copy(color = OffWhiteText),
            )
        }
    }
}

@Composable
private fun CloudStatusRow(
    profile: ProfileState,
    isRefreshPending: Boolean,
    onRetry: () -> Unit,
) {
    val unreachable = isRefreshPending || profile == ProfileState.Unavailable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalCard)
            .padding(start = 14.dp, end = 4.dp)
            .heightIn(min = 52.dp)
            .testTag("account_cloud_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            unreachable -> StatusIcon(Icons.Outlined.CloudOff, MutedWarmGray)
            profile == ProfileState.Loading || profile == ProfileState.Idle -> CircularProgressIndicator(
                color = WarmAmberPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            profile is ProfileState.Loaded -> StatusIcon(Icons.Outlined.CheckCircle, ChartreuseSecondary)
            else -> StatusIcon(Icons.Outlined.ErrorOutline, WarmAmberLight)
        }
        Text(
            text = when {
                isRefreshPending -> "Offline. Your account reconnects on its own."
                profile == ProfileState.Unavailable -> "Can't reach your cloud account right now"
                profile is ProfileState.Loaded -> "Cloud profile ready"
                profile == ProfileState.Missing -> "Cloud profile not found"
                else -> "Checking your cloud profile…"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (profile == ProfileState.Unavailable || profile == ProfileState.Missing) {
            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = WarmAmberPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SyncStatusRow(
    status: SyncStatus,
    onRetryFailed: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CharcoalCard)
            .padding(start = 14.dp, end = 4.dp)
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("account_sync_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            status.failedChanges > 0 -> StatusIcon(Icons.Outlined.ErrorOutline, WarmAmberLight)
            status.isSyncing -> CircularProgressIndicator(
                color = WarmAmberPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            status.unsyncedChanges > 0 -> StatusIcon(Icons.Outlined.CloudUpload, MutedWarmGray)
            status.lastSyncedAt != null -> StatusIcon(Icons.Outlined.CloudDone, ChartreuseSecondary)
            else -> StatusIcon(Icons.Outlined.CloudQueue, MutedWarmGray)
        }
        Text(
            text = syncStatusText(status, now = System.currentTimeMillis()) { lastSyncedAt ->
                DateUtils.getRelativeTimeSpanString(
                    lastSyncedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (status.failedChanges > 0) {
            TextButton(onClick = onRetryFailed) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = WarmAmberPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

internal fun syncStatusText(status: SyncStatus, now: Long, relativeTime: (Long) -> String): String = when {
    status.failedChanges == 1 -> "1 change couldn't sync"
    status.failedChanges > 1 -> "${status.failedChanges} changes couldn't sync"
    status.isSyncing -> "Syncing…"
    status.unsyncedChanges == 1 -> "1 change waiting to sync"
    status.unsyncedChanges > 1 -> "${status.unsyncedChanges} changes waiting to sync"
    status.lastSyncedAt != null && now - status.lastSyncedAt < JUST_NOW_MILLIS -> "All changes synced"
    status.lastSyncedAt != null -> "All changes synced · ${relativeTime(status.lastSyncedAt)}"
    else -> "Not synced yet"
}

private const val JUST_NOW_MILLIS = 60_000L

@Composable
private fun StatusIcon(imageVector: ImageVector, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp),
    )
}

@Preview
@Composable
private fun AccountSheetContentPreview() {
    SplitFlowTheme {
        Box(Modifier.background(CharcoalSurface)) {
            AccountSheetContent(
                syncStatus = SyncStatus(lastSyncedAt = System.currentTimeMillis()),
                onRetryFailedSync = {},
                state = AuthUiState(
                    authState = AuthState.SignedIn(userId = "u1", email = "vaibhav@example.com", isRefreshPending = false),
                    profile = ProfileState.Loaded(Profile(id = "u1", displayName = "Vaibhav")),
                ),
                onRetryProfile = {},
                onSignOut = {},
            )
        }
    }
}
