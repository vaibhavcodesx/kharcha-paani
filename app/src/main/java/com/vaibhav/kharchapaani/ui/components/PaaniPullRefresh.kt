package com.vaibhav.kharchapaani.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pull to sync, as a small glass of paani.
 *
 * The list slides down to make room, and a round glass fills with the brand gradient as you pull.
 * It's full, with one haptic tick, exactly when letting go would sync, and the line under it
 * reads "Let go to sync". While the sync runs, the surface ripples. When it's done the water
 * calms flat, the glass gives a small settle, and one line says what actually happened in the
 * app's voice ("All caught up", "Saved on this phone…", "2 changes couldn't sync", where the
 * water drains to half and turns amber) before the list slides back.
 *
 * Deliberately not the app's logo or ₹ mark: the logo isn't final (owner, 2026-11-06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaaniPullRefresh(
    syncStatus: SyncStatus,
    onRequestSync: () -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String,
    /** No account: there's nothing to sync, so the pull says so instead of waiting for a sync. */
    localOnly: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    val haptics = LocalHapticFeedback.current
    val currentStatus by rememberUpdatedState(syncStatus)
    var phase by remember { mutableStateOf<PullPhase>(PullPhase.Resting) }

    LaunchedEffect(phase) {
        when (val now = phase) {
            PullPhase.Resting -> Unit
            PullPhase.Syncing -> if (localOnly) {
                delay(LOCAL_BEAT_MS)
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                phase = PullPhase.Done(PullResult.LocalOnly, holding = true)
            } else {
                val seen = awaitRequestedSync { currentStatus.isSyncing }
                val failed = currentStatus.failedChanges
                val result = when {
                    seen == RequestedSync.NeverStarted -> PullResult.OnThisPhone
                    seen == RequestedSync.StillRunning -> PullResult.StillGoing
                    failed > 0 -> PullResult.CouldNotSync(failed)
                    else -> PullResult.CaughtUp
                }
                haptics.performHapticFeedback(
                    if (result is PullResult.CouldNotSync) HapticFeedbackType.Reject else HapticFeedbackType.Confirm,
                )
                phase = PullPhase.Done(result, holding = true)
            }
            is PullPhase.Done -> if (now.holding) {
                delay(RESULT_HOLD_MS)
                phase = now.copy(holding = false)
            } else {
                // Keep the result on screen while the list slides back, then rest.
                snapshotFlow { state.distanceFraction }.first { it <= 0f }
                phase = PullPhase.Resting
            }
        }
    }

    // One tick as the glass fills under the finger, never while a result is showing.
    val armed by remember { derivedStateOf { state.distanceFraction >= 1f } }
    LaunchedEffect(armed) {
        if (armed && phase == PullPhase.Resting) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
    }

    val revealPx = with(LocalDensity.current) { REVEAL.toPx() }
    PullToRefreshBox(
        isRefreshing = phase == PullPhase.Syncing || (phase as? PullPhase.Done)?.holding == true,
        onRefresh = {
            phase = PullPhase.Syncing
            if (!localOnly) onRequestSync()
        },
        state = state,
        modifier = modifier.testTag("${tagPrefix}_pull_to_refresh"),
        indicator = {
            val fraction = state.distanceFraction
            if (fraction > 0f || phase != PullPhase.Resting) {
                GlassIndicator(
                    fraction = fraction,
                    phase = phase,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .testTag("${tagPrefix}_pull_refresh_indicator"),
                )
            }
        },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                translationY = revealPx * pullResistance(state.distanceFraction)
            },
            content = content,
        )
    }
}

/** Where a pull is: at rest, syncing, or showing how it went. */
private sealed interface PullPhase {
    data object Resting : PullPhase
    data object Syncing : PullPhase
    data class Done(val result: PullResult, val holding: Boolean) : PullPhase
}

private sealed interface PullResult {
    data object CaughtUp : PullResult
    data object OnThisPhone : PullResult
    data object StillGoing : PullResult
    data object LocalOnly : PullResult
    data class CouldNotSync(val count: Int) : PullResult
}

/** Space the pull opens above the list for the glass and its line. */
private val REVEAL: Dp = 78.dp
private val GLASS: Dp = 28.dp
private const val TOP_PAD_DP = 10f
private const val RESULT_HOLD_MS = 1_100L

/** In local mode the glass still fills and ripples for a beat, so the pull feels answered. */
private const val LOCAL_BEAT_MS = 450L

/** A full glass still shows its surface, so the ripple has somewhere to be. */
private const val FULL = 0.82f

/** Past the threshold the list keeps following the finger, more and more reluctantly. */
internal fun pullResistance(fraction: Float): Float =
    if (fraction <= 1f) fraction.coerceAtLeast(0f) else 1f + (1f - 1f / (1f + (fraction - 1f) * 0.6f)) * 0.5f

@Composable
private fun GlassIndicator(fraction: Float, phase: PullPhase, modifier: Modifier) {
    val revealPx = with(LocalDensity.current) { REVEAL.toPx() }
    val result = (phase as? PullPhase.Done)?.result
    val failed = result is PullResult.CouldNotSync

    val ripple = rememberInfiniteTransition(label = "paani")
    val wave by ripple.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(durationMillis = if (phase == PullPhase.Syncing) 900 else 2_400, easing = LinearEasing),
            RepeatMode.Restart,
        ),
        label = "paani-wave",
    )
    val bob by ripple.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1_300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "paani-bob",
    )
    // The surface: a slosh while pulling, a ripple while syncing, flat calm once it's done.
    val amplitude by animateFloatAsState(
        targetValue = when (phase) {
            PullPhase.Resting -> 0.07f * (1f - kotlin.math.abs(fraction.coerceIn(0f, 1f) * 2f - 1f)) + 0.02f
            PullPhase.Syncing -> 0.075f
            is PullPhase.Done -> 0f
        },
        animationSpec = tween(420),
        label = "paani-amplitude",
    )
    val level by animateFloatAsState(
        targetValue = when {
            phase == PullPhase.Resting -> FULL * easeOut(fraction.coerceIn(0f, 1f))
            failed -> FULL * 0.5f
            else -> FULL
        },
        animationSpec = if (phase == PullPhase.Resting) snapSpec else tween(520, easing = FastOutSlowInEasing),
        label = "paani-level",
    )
    val settle = remember { Animatable(1f) }
    LaunchedEffect(result != null) {
        if (result != null) {
            settle.snapTo(0.86f)
            settle.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
    }

    val line = indicatorLine(phase, armed = fraction >= 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // Rides in with the list's own movement and fades up with the pull.
                translationY = revealPx * pullResistance(fraction) - revealPx + TOP_PAD_DP * density
                alpha = (fraction * 1.8f).coerceIn(0f, 1f)
            }
            .semantics { liveRegion = LiveRegionMode.Polite; contentDescription = line },
    ) {
        Canvas(
            Modifier
                .size(GLASS)
                .graphicsLayer {
                    scaleX = settle.value
                    scaleY = settle.value
                },
        ) {
            val surface = level + if (phase == PullPhase.Syncing) bob * 0.03f else 0f
            drawGlass(
                level = surface.coerceIn(0f, 1f),
                amplitude = amplitude,
                wave = wave,
                water = if (failed) Brush.verticalGradient(listOf(WarmAmberPrimary, WarmAmberPrimary.copy(alpha = 0.7f)))
                else Brush.verticalGradient(BrandGradient.colors.reversed()),
                rim = if (fraction >= 1f || result != null) MutedWarmGray else SubMutedWarmGray,
            )
        }
        AnimatedContent(
            targetState = line,
            transitionSpec = {
                (fadeIn(tween(180)) + slideInVertically(tween(220)) { it / 3 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(160)) { -it / 3 })
            },
            label = "paani-line",
            modifier = Modifier.clearAndSetSemantics { },
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    failed -> WarmAmberPrimary
                    result != null -> MutedWarmGray
                    else -> SubMutedWarmGray
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private val snapSpec = androidx.compose.animation.core.snap<Float>()

private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)

@Composable
private fun indicatorLine(phase: PullPhase, armed: Boolean): String = when (phase) {
    PullPhase.Resting -> stringResource(if (armed) R.string.pull_sync_release else R.string.pull_sync_pull)
    PullPhase.Syncing -> stringResource(R.string.pull_sync_syncing)
    is PullPhase.Done -> when (val result = phase.result) {
        PullResult.CaughtUp -> stringResource(R.string.pull_sync_caught_up)
        PullResult.OnThisPhone -> stringResource(R.string.pull_sync_on_this_phone)
        PullResult.StillGoing -> stringResource(R.string.pull_sync_still_going)
        PullResult.LocalOnly -> stringResource(R.string.pull_sync_local_only)
        is PullResult.CouldNotSync -> pluralStringResource(R.plurals.pull_sync_could_not, result.count, result.count)
    }
}

/**
 * A round glass seen from the side: water up to [level] of its height, its surface two offset
 * sine waves (the back one fainter, for depth) of [amplitude] × the glass height, a thin rim and
 * a small glint so it reads as glass and not as a filled dot.
 */
private fun DrawScope.drawGlass(level: Float, amplitude: Float, wave: Float, water: Brush, rim: Color) {
    val rimWidth = size.minDimension * 0.06f
    val radius = size.minDimension / 2f - rimWidth / 2f
    val centre = Offset(size.width / 2f, size.height / 2f)
    val inside = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(centre, radius - rimWidth * 0.9f))
    }
    if (level > 0f) {
        clipPath(inside) {
            drawSurface(level, amplitude * 0.8f, wave + 1.9f, water, alpha = 0.35f)
            drawSurface(level, amplitude, wave, water, alpha = 1f)
        }
    }
    drawCircle(rim, radius = radius, center = centre, style = Stroke(width = rimWidth))
    drawArc(
        color = Color.White.copy(alpha = 0.22f),
        startAngle = 200f,
        sweepAngle = 50f,
        useCenter = false,
        topLeft = Offset(centre.x - radius * 0.62f, centre.y - radius * 0.62f),
        size = Size(radius * 1.24f, radius * 1.24f),
        style = Stroke(width = rimWidth * 0.9f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawSurface(level: Float, amplitude: Float, wave: Float, water: Brush, alpha: Float) {
    val h = size.height
    val w = size.width
    val top = h * (1f - level)
    val amp = h * amplitude
    val path = Path().apply {
        moveTo(0f, h)
        val steps = 24
        for (i in 0..steps) {
            val x = w * i / steps
            val y = top + amp * sin(wave + (x / w) * 2f * PI.toFloat() * 1.15f)
            lineTo(x, y)
        }
        lineTo(w, h)
        close()
    }
    drawPath(path, water, alpha = alpha)
}
