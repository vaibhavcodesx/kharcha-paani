package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
 * Liquid Glass, translated to Compose. Only for surfaces that float above content (the dock, sheet
 * chrome, floating messages); content cards stay solid.
 *
 * Layers, bottom to top:
 * 1. a real backdrop blur of what's behind (Android 12+), or a dense scrim below that;
 * 2. a neutral tint that lifts the pane just clear of the background and keeps text on it legible;
 * 3. a soft sheen under the content, and a hairline along the top edge where light would catch it.
 *
 * **The pane is the glass.** Twice now this was drawn as a rim around an opaque core — 5dp, then
 * 12dp — and both read as a border rather than a material, because that is what a ring of one
 * colour around a solid centre is. Widening it only made the border thicker. Real glass is the
 * whole surface with the content sitting on it, so that is what this is now: one pane, text
 * directly on it, no core and no rim.
 *
 * **The tint is neutral on purpose.** It used to carry the brand amber at 7%. Over a `#0E0D0C`
 * background with almost nothing bright behind the dock, a blur of near-black is near-black, so
 * the amber was not tinting a visible material — it *was* the material, and it composited to rust
 * brown. The accent belongs on the things that are meant to be amber (the active tab, the add
 * button), not smeared across the pane behind them.
 */
fun Modifier.liquidGlass(
    shape: Shape,
    hazeState: HazeState?,
    blurRadius: Dp = 32.dp,
): Modifier = this
    .clip(shape)
    .then(
        if (hazeState != null) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = CharcoalBg,
                    tints = listOf(
                        // Enough of the background to keep text readable over anything that
                        // scrolls under the pane...
                        HazeTint(CharcoalBg.copy(alpha = 0.68f)),
                        // ...and a neutral lift, so the pane still sits clearly above it.
                        HazeTint(Color.White.copy(alpha = 0.06f)),
                    ),
                    blurRadius = blurRadius,
                    noiseFactor = 0.04f,
                    fallbackTint = HazeTint(CharcoalSurface.copy(alpha = 0.94f)),
                ),
            )
        } else {
            // No backdrop to sample (another window): the same look over an opaque base.
            Modifier
                .background(CharcoalSurface.copy(alpha = 0.96f))
                .background(Color.White.copy(alpha = 0.04f))
        }
    )
    // Under the content, not over it: a sheen drawn on top would wash across the labels sitting
    // on the pane and lighten their tops.
    .drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.06f),
                0.45f to Color.Transparent,
            ),
        )
    }
    .drawWithContent {
        drawContent()
        val outline = shape.createOutline(size, layoutDirection, this)
        // A rim all the way round, at a fraction of the highlight's strength: just enough to give
        // the pane an edge where it meets the page, which is the "small border" asked for in
        // round 2 §6. This is not the old gradient edge-stroke that made the dock read as a
        // bordered box — that one was the brightest thing on the shape and did the same work all
        // the way round. At 7% it is below the tint's own lift and only resolves as a boundary.
        drawOutline(
            outline = outline,
            color = Color.White.copy(alpha = 0.07f),
            style = Stroke(width = 1.dp.toPx()),
        )
        // On top of it, the specular: a hairline along the top edge only, fading out before the
        // shape's shoulders, the way light catches the lip of a real piece of glass.
        clipRect(bottom = size.height * TOP_HIGHLIGHT_FRACTION) {
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

/** How far down the shape the top highlight is allowed to reach before it is clipped away. */
private const val TOP_HIGHLIGHT_FRACTION = 0.20f

/**
 * A pane of [liquidGlass] with its content laid directly on it.
 *
 * This used to be a glass rim around an opaque core, with [content] on the core so text never
 * touched the blur. That is what made the dock read as a bordered box; the tint now carries enough
 * of the background for labels to sit on the pane itself.
 */
@Composable
fun GlassPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(shape, LocalHazeState.current).padding(contentPadding),
        content = content,
    )
}

/** The app's one gradient: amber through warm gold to lime. See [BrandGold]. */
object BrandGradient {
    val colors = listOf(WarmAmberPrimary, BrandGold, ChartreuseSecondary)

    fun horizontal(): Brush = Brush.horizontalGradient(colors)

    fun diagonal(): Brush = Brush.linearGradient(colors)

    /** Gradient-filled text, for the hero balance only. */
    fun text(style: TextStyle): TextStyle = style.copy(brush = horizontal())
}

/**
 * This account's own colour (design item 11, owner 2026-09-27: "we should see our own colour, not
 * amber"). The avatar, its sync ring, the "You" chip and "You" on the donut use it. Amber until the
 * account has one, and amber stays for warnings such as a failed sync.
 */
data class MyColour(val color: androidx.compose.ui.graphics.Color, val hex: String)

val LocalMyColour = androidx.compose.runtime.compositionLocalOf { MyColour(androidx.compose.ui.graphics.Color(0xFFFF7A3D), "#FF7A3D") }
