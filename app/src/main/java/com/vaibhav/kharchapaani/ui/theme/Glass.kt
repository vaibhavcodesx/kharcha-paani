package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * The screen content that floating glass samples (marked `hazeSource`). Provide it only around
 * chrome drawn in the same window as that content: composition locals also reach dialogs and modal
 * sheets, which are separate windows and can't sample it, and there glass must get null and fall
 * back to its tinted scrim.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Liquid Glass, translated to Compose. Only for surfaces that float above content (dock, floating
 * actions, sheet chrome); content cards stay solid.
 *
 * Layers, bottom to top:
 * 1. real backdrop blur of what's behind (Android 12+), or a dense tinted scrim below that;
 * 2. an accent tint, so the glass reads as this app's amber/lime rather than generic frost;
 * 3. a specular sheen: light catching the top edge, fading down;
 * 4. an edge light: a hairline that is bright at the top and picks up the accent at the bottom.
 * Text never sits on this directly: put it on [GlassPanel]'s solid core, or a solid chip.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    accent: Color = WarmAmberPrimary,
    blurRadius: Dp = 28.dp,
): Modifier = this
    .clip(shape)
    .then(
        if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = CharcoalBg,
                    tints = listOf(
                        HazeTint(CharcoalBg.copy(alpha = 0.42f)),
                        HazeTint(accent.copy(alpha = 0.09f)),
                    ),
                    blurRadius = blurRadius,
                    noiseFactor = 0.05f,
                    fallbackTint = HazeTint(CharcoalSurface.copy(alpha = 0.94f)),
                ),
            )
        } else {
            // No backdrop to sample (another window): the same tint over an opaque-enough base.
            Modifier
                .background(CharcoalSurface.copy(alpha = 0.96f))
                .background(accent.copy(alpha = 0.07f))
        }
    )
    .drawWithContent {
        drawContent()
        // Specular sheen: a soft band of light across the top, as if lit from above.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.35f to Color.White.copy(alpha = 0.02f),
                1f to Color.Transparent,
            ),
        )
        // Edge light: bright where light hits, tinted where it reflects the accent.
        val outline = shape.createOutline(size, layoutDirection, this)
        drawOutline(
            outline = outline,
            brush = Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.30f),
                0.45f to Color.White.copy(alpha = 0.06f),
                1f to accent.copy(alpha = 0.22f),
                start = Offset.Zero,
                end = Offset(0f, size.height),
            ),
            style = Stroke(width = 1.dp.toPx()),
        )
    }

/**
 * A glass bezel around a solid core: the glass shows as a rim of [bezel] around opaque content, so
 * everything readable sits on a solid layer while the surface still reads as floating glass.
 */
@Composable
fun GlassPanel(
    shape: Shape,
    coreShape: Shape,
    modifier: Modifier = Modifier,
    accent: Color = WarmAmberPrimary,
    bezel: Dp = 6.dp,
    coreColor: Color = CharcoalSurface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.liquidGlass(shape, LocalHazeState.current, accent)) {
        Box(
            modifier = Modifier
                .padding(bezel)
                .clip(coreShape)
                .background(coreColor),
            content = content,
        )
    }
}

/** The app's one gradient: amber through warm gold to lime. See [BrandGold]. */
object BrandGradient {
    val colors = listOf(WarmAmberPrimary, BrandGold, ChartreuseSecondary)

    fun horizontal(): Brush = Brush.horizontalGradient(colors)

    fun diagonal(): Brush = Brush.linearGradient(colors)

    /** Gradient-filled text, for the hero balance only. */
    fun text(style: TextStyle): TextStyle = style.copy(brush = horizontal())
}
