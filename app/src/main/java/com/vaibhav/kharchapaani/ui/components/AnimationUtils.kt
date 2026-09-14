package com.vaibhav.kharchapaani.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.util.Locale

/**
 * Modifier that scales down slightly on press with bouncy spring physics and triggers a subtle haptic feedback.
 */
/**
 * The same spring and haptic as [bounceClick] for a control that already handles its own click
 * (Button, IconButton): pass the control the same [interactionSource]. Adding [bounceClick] with an
 * onClick to such a control nests a second clickable, which screen readers announce as a second
 * button.
 */
fun Modifier.pressBounce(interactionSource: InteractionSource, scaleDown: Float = 0.94f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "press_bounce_scale"
    )
    LaunchedEffect(isPressed) {
        if (isPressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

fun Modifier.bounceClick(
    scaleDown: Float = 0.94f,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val view = LocalView.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bounce_scale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
            } else {
                Modifier
            }
        )
}

/**
 * An odometer: when the amount changes, each digit that differs rolls to its new value (up when the
 * amount grows, down when it shrinks) while unchanged digits stay still. Digits are aligned from the
 * right, so "₹999" → "₹1,000" keeps the units column in place.
 *
 * The text comes from [Money.digits] on the exact value; the old version tweened a Float, which can't
 * hold paisa above ₹10 lakh (12,34,567.89 showed as 12,34,567.88) and rounded to whole rupees when
 * asked. [brush] fills the whole number with one gradient instead of restarting it on each digit.
 */
@Composable
fun AnimatedRollingNumber(
    targetValue: Double,
    prefix: String = "₹",
    style: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    brush: Brush? = null,
) {
    val text = prefix + Money.digits(targetValue)
    val lastValue = remember { mutableDoubleStateOf(targetValue) }
    val goingUp = targetValue >= lastValue.doubleValue
    LaunchedEffect(targetValue) { lastValue.doubleValue = targetValue }

    val digitStyle = style.copy(
        fontFeatureSettings = "tnum",
        fontFamily = style.fontFamily ?: com.vaibhav.kharchapaani.ui.theme.numericFontFamily,
    )
    Row(
        modifier = modifier
            .clearAndSetSemantics { contentDescription = text }
            .then(
                if (brush != null) {
                    Modifier
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            drawRect(brush = brush, blendMode = BlendMode.SrcIn)
                        }
                } else {
                    Modifier
                }
            ),
    ) {
        text.forEachIndexed { index, char ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val spec = spring<IntOffset>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        if (goingUp) {
                            (slideInVertically(spec) { it } + fadeIn()) togetherWith (slideOutVertically(spec) { -it } + fadeOut())
                        } else {
                            (slideInVertically(spec) { -it } + fadeIn()) togetherWith (slideOutVertically(spec) { it } + fadeOut())
                        }.using(SizeTransform(clip = true))
                    },
                    label = "digit",
                ) { c ->
                    Text(text = c.toString(), style = digitStyle, color = if (brush != null) Color.White else color)
                }
            }
        }
    }
}

/**
 * Subtle shimmering skeleton / gradient sweep modifier for loading states.
 */
fun Modifier.shimmerLoading(
    enabled: Boolean = true,
    shimmerColors: List<Color> = listOf(
        Color.White.copy(alpha = 0.04f),
        Color.White.copy(alpha = 0.16f),
        Color.White.copy(alpha = 0.04f)
    ),
    durationMillis: Int = 1200
): Modifier = composed {
    if (!enabled) return@composed this

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnimation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    this.drawWithCache {
        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnimation - 500f, translateAnimation - 500f),
            end = Offset(translateAnimation, translateAnimation)
        )
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }
}

/**
 * Animated voice waveform bars indicating active audio listening.
 */
@Composable
fun PulsingAudioWaveform(
    modifier: Modifier = Modifier,
    barCount: Int = 5,
    maxHeight: Dp = 20.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_transition")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        for (i in 0 until barCount) {
            val duration = 400 + (i * 120)
            val heightFraction by infiniteTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(duration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(maxHeight * heightFraction)
                    .clip(CircleShape)
                    .drawWithCache {
                        val color = if (i % 2 == 0) WarmAmberPrimary else ChartreuseSecondary
                        onDrawBehind { drawRect(color) }
                    }
            )
            if (i < barCount - 1) {
                Spacer(modifier = Modifier.width(3.dp))
            }
        }
    }
}
