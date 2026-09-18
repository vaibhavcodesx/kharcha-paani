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
import androidx.compose.ui.graphics.drawscope.clipRect
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
 * 1. a real backdrop blur of what's behind (Android 12+), or a dense tinted scrim below that;
 * 2. an accent tint, so the glass reads as this app's amber rather than generic frost;
 * 3. a specular highlight along the top edge only, where light would actually catch it.
 *
 * **The blur is the effect.** It used to be drawn behind a 5dp rim, which is not enough glass for
 * a blur to be visible at all, so the only thing carrying the material was a hairline stroke that
 * ran all the way around the shape — white at the top, amber at the bottom. A hairline that fades
 * in and out along its length reads as a dashed rust outline, not as glass, and the owner's word
 * for it was "ugly". The rim is now wide enough to show what is behind it, and the stroke is a
 * short highlight across the top instead of a ring.
 *
 * Text never sits on this directly: put it on [GlassPanel]'s solid core, or a solid chip.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    accent: Color = WarmAmberPrimary,
    blurRadius: Dp = 32.dp,
): Modifier = this
    .clip(shape)
    .then(
        if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = CharcoalBg,
                    // Lighter than it was: with a wider rim the blur itself carries the surface, so
                    // the tint only has to keep it legible over bright content, not stand in for it.
                    tints = listOf(
                        HazeTint(CharcoalBg.copy(alpha = 0.30f)),
                        HazeTint(accent.copy(alpha = 0.07f)),
                    ),
                    blurRadius = blurRadius,
                    noiseFactor = 0.04f,
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
                0f to Color.White.copy(alpha = 0.09f),
                0.30f to Color.White.copy(alpha = 0.02f),
                0.65f to Color.Transparent,
            ),
        )
        // The highlight: a hairline along the top edge only, fading out before the shape's
        // shoulders, the way light catches the lip of a real piece of glass. Never a full ring.
        val outline = shape.createOutline(size, layoutDirection, this)
        clipRect(bottom = size.height * TOP_HIGHLIGHT_FRACTION) {
            drawOutline(
                outline = outline,
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.25f to Color.White.copy(alpha = 0.34f),
                    0.75f to Color.White.copy(alpha = 0.34f),
                    1f to Color.Transparent,
                ),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }

/** How far down the shape the top highlight is allowed to reach before it is clipped away. */
private const val TOP_HIGHLIGHT_FRACTION = 0.22f

/**
 * A glass bezel around a solid core: the glass shows as a rim of [bezel] around opaque content, so
 * everything readable sits on a solid layer while the surface still reads as floating glass.
 *
 * [GlassBezel] is the width that rim needs to be for the blur behind it to be visible. Anything
 * much narrower and the material disappears, which is what went wrong at 5dp.
 */
/**
 * How wide the glass rim is around a panel's solid core. Wide enough that the backdrop blur is
 * legible as a material; the whole point of the 2026-09-18 rework.
 */
val GlassBezel: Dp = 12.dp

@Composable
fun GlassPanel(
    shape: Shape,
    coreShape: Shape,
    modifier: Modifier = Modifier,
    accent: Color = WarmAmberPrimary,
    bezel: Dp = GlassBezel,
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
