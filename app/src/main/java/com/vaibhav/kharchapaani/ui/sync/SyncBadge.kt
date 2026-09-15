package com.vaibhav.kharchapaani.ui.sync

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/** The one sync state worth showing at a glance, most urgent first (the same order as the account sheet). */
enum class SyncBadge {
    Failed, Syncing, Waiting, None;

    companion object {
        fun of(status: SyncStatus): SyncBadge = when {
            status.failedChanges > 0 -> Failed
            status.isSyncing -> Syncing
            status.unsyncedChanges > 0 -> Waiting
            else -> None
        }

        /** Spoken with the account button; null when everything's synced. */
        fun describe(status: SyncStatus): String? = when (of(status)) {
            Failed -> if (status.failedChanges == 1) "1 change couldn't sync" else "${status.failedChanges} changes couldn't sync"
            Syncing -> "Syncing"
            Waiting -> if (status.unsyncedChanges == 1) "1 change waiting to sync" else "${status.unsyncedChanges} changes waiting to sync"
            None -> null
        }
    }
}

/**
 * Sync at a glance, drawn around whatever it decorates (the account avatar): a slowly turning ring
 * while syncing, an amber dot when something failed, a quiet dot while changes wait. Nothing when
 * all is synced. Details and retry live in the account sheet.
 */
@Composable
fun BoxScope.SyncBadgeOverlay(status: SyncStatus, avatarSize: Dp) {
    when (SyncBadge.of(status)) {
        SyncBadge.Syncing -> {
            val turn by rememberInfiniteTransition(label = "sync_ring").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
                label = "sync_ring_turn",
            )
            Canvas(Modifier.size(avatarSize + 8.dp).align(Alignment.Center)) {
                rotate(turn) {
                    drawArc(
                        color = WarmAmberPrimary,
                        startAngle = 0f,
                        sweepAngle = 110f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        SyncBadge.Failed -> Dot(WarmAmberPrimary, 12.dp)
        SyncBadge.Waiting -> Dot(MutedWarmGray, 10.dp)
        SyncBadge.None -> Unit
    }
}

@Composable
private fun BoxScope.Dot(color: androidx.compose.ui.graphics.Color, size: Dp) {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .size(size)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, CharcoalBg, CircleShape),
    )
}
