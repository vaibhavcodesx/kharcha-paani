package com.vaibhav.kharchapaani.ui.auth

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.components.CategoryBadge
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.BrandGold
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.KpMotion
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/*
 * The sign-in screen's motion and set dressing, kept apart from its layout.
 *
 * Everything here that moves does so in the draw phase: the glow reads its animated values inside
 * drawBehind, and the entrance reads its progress inside graphicsLayer. Nothing recomposes per
 * frame, so the screen costs a couple of gradient fills a frame while it sits there.
 */

/** The whole entrance, first element in to last element settled. */
internal const val ENTRANCE_TOTAL_MS = 1_100

/** How long each element takes to arrive, from its own start. */
private const val ENTRANCE_ITEM_MS = 520

/** One full sweep of each glow, there and back. Slow enough to notice only if you watch for it. */
private const val AMBER_DRIFT_MS = 16_000
private const val LIME_DRIFT_MS = 22_000

/** When each element starts arriving: logo, then words, then the preview, then the action. */
internal object EntranceDelay {
    const val LOGO = 0
    const val TITLE = 80
    const val LINE = 160
    const val PREVIEW = 300
    const val PREVIEW_FIRST_ROW = 380
    const val PREVIEW_SECOND_ROW = 480
    const val ACTIONS = 560
}

/** Android's "Remove animations" sets the animator scale to 0. */
internal fun isReducedMotion(animatorDurationScale: Float): Boolean = animatorDurationScale == 0f

@Composable
internal fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        isReducedMotion(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f))
    }
}

/**
 * 0 to 1 across the whole entrance. Plays once: the flag is saved, so rotating the phone or
 * coming back from the account picker doesn't replay it. Arrives settled with animations off.
 */
@Composable
internal fun rememberEntranceProgress(reducedMotion: Boolean): () -> Float {
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played || reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (progress.value < 1f) progress.animateTo(1f, tween(ENTRANCE_TOTAL_MS, easing = LinearEasing))
        played = true
    }
    return remember(progress) { { progress.value } }
}

/**
 * Rises 20dp and fades in, starting [delayMs] into the entrance, on the app's emphasized
 * decelerate curve (the one the tab change uses for things arriving).
 */
internal fun Modifier.entrance(progress: () -> Float, delayMs: Int): Modifier = graphicsLayer {
    val t = ((progress() * ENTRANCE_TOTAL_MS - delayMs) / ENTRANCE_ITEM_MS).coerceIn(0f, 1f)
    alpha = LinearOutSlowInEasing.transform(t)
    translationY = (1f - KpMotion.EmphasizedDecelerate.transform(t)) * 20.dp.toPx()
}

/**
 * Two soft pools of the brand's end colours: amber up by the logo, lime low on the far side, each
 * drifting on its own slow cycle so they never line up the same way twice. The same glow the screen
 * always had in its corner, now alive — and still at rest with animations turned off.
 */
@Composable
internal fun SignInBackdrop(reducedMotion: Boolean, modifier: Modifier = Modifier) {
    if (reducedMotion) {
        Box(modifier.clearAndSetSemantics {}.drawBehind { drawGlows(amber = 0.5f, lime = 0.5f) })
        return
    }
    val transition = rememberInfiniteTransition(label = "sign_in_glow")
    val amber = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(AMBER_DRIFT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "amber_drift",
    )
    val lime = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LIME_DRIFT_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lime_drift",
    )
    // The values are read inside the draw lambda, so only the draw phase runs each frame.
    Box(modifier.clearAndSetSemantics {}.drawBehind { drawGlows(amber = amber.value, lime = lime.value) })
}

private fun mix(from: Float, to: Float, t: Float) = from + (to - from) * t

/**
 * Kept apart on purpose: amber over lime on near-black composites to olive, and amber alone at any
 * strength goes rust (see Glass.kt). Gold sits inside the amber pool's falloff so its edge reads as
 * warm light rather than brown.
 */
private fun DrawScope.drawGlows(amber: Float, lime: Float) {
    val w = size.width
    val h = size.height
    val reach = size.maxDimension
    drawRect(
        Brush.radialGradient(
            0f to WarmAmberPrimary.copy(alpha = 0.22f),
            0.5f to BrandGold.copy(alpha = 0.06f),
            1f to Color.Transparent,
            center = Offset(w * mix(-0.15f, 0.30f, amber), h * mix(-0.08f, 0.14f, amber)),
            radius = reach * 0.8f,
        )
    )
    drawRect(
        Brush.radialGradient(
            0f to ChartreuseSecondary.copy(alpha = 0.10f),
            1f to Color.Transparent,
            center = Offset(w * mix(1.15f, 0.72f, lime), h * mix(0.62f, 0.86f, lime)),
            radius = reach * 0.55f,
        )
    )
}

/**
 * The pane the sign-in action sits on: the same glass as the dock and sheets (Glass.kt), but
 * translucent rather than blurred. What's behind it is only these two soft gradients, which a blur
 * would leave exactly as they are, so a real backdrop blur would cost a render effect every frame of
 * the drift for no visible difference. Same neutral lift, same sheen under the content, same 7% rim
 * and top-edge specular.
 */
internal fun Modifier.signInGlass(shape: Shape): Modifier = this
    .clip(shape)
    .background(CharcoalSurface.copy(alpha = 0.70f))
    .background(Color.White.copy(alpha = 0.04f))
    .drawBehind {
        drawRect(brush = Brush.verticalGradient(0f to Color.White.copy(alpha = 0.06f), 0.45f to Color.Transparent))
    }
    .drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(outline = outline, color = Color.White.copy(alpha = 0.07f), style = Stroke(width = 1.dp.toPx()))
        clipRect(bottom = size.height * 0.2f) {
            drawOutline(
                outline = outline,
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.25f to Color.White.copy(alpha = 0.22f),
                    0.75f to Color.White.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }

/**
 * What the app does, shown rather than described: an expense split three ways, then the friend
 * paying back their share. Drawn with the same badge, type and money colours as the real activity
 * rows, so it previews the app instead of a marketing illustration of it.
 *
 * Decorative: the sentence above already says all of this, so it's hidden from screen readers
 * (tagged only so layout tests can find it).
 */
@Composable
internal fun LedgerPreview(progress: () -> Float, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics { testTag = "sign_in_preview" }
            .entrance(progress, EntranceDelay.PREVIEW)
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(vertical = Spacing.s),
    ) {
        PreviewRow(
            badge = { CategoryBadge("Food") },
            title = "Friday dinner",
            detail = "You paid · split 3 ways",
            amount = "-₹640",
            amountColor = OffWhiteText,
            below = "of ₹1,920",
            modifier = Modifier.entrance(progress, EntranceDelay.PREVIEW_FIRST_ROW),
        )
        Box(
            Modifier
                .padding(horizontal = Spacing.l)
                .fillMaxWidth()
                .height(1.dp)
                .background(CharcoalBorder)
        )
        PreviewRow(
            badge = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(KpShapes.small)
                        .background(MoneyIn.copy(alpha = 0.14f)),
                ) {
                    Icon(KpIcons.Settle, contentDescription = null, tint = MoneyIn, modifier = Modifier.size(20.dp))
                }
            },
            title = "Rohan paid you",
            detail = "UPI · settled up",
            amount = "+₹640",
            amountColor = MoneyIn,
            below = null,
            modifier = Modifier.entrance(progress, EntranceDelay.PREVIEW_SECOND_ROW),
        )
    }
}

@Composable
private fun PreviewRow(
    badge: @Composable () -> Unit,
    title: String,
    detail: String,
    amount: String,
    amountColor: Color,
    below: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        badge()
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(amount, style = MoneyText.row, color = amountColor, maxLines = 1)
            if (below != null) Text(below, style = MoneyText.small, maxLines = 1)
        }
    }
}
