package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * A container has to be visible against what it sits on, or it is not a container.
 *
 * The Ghost button tier filled itself with `CharcoalElevated` and lived mostly on `CharcoalCard`.
 * Those two differ by four points per channel, which is nothing: "Record" on a friend row and
 * "Read this trip" in its own card both drew as plain labels, and the second was reported as not
 * being clickable at all. The general lesson is that a tier used on more than one surface cannot
 * be a fixed opaque colour — which is why Ghost is now a wash over whatever is behind it.
 *
 * **Measured in L\*, not in WCAG contrast ratio.** That ratio has a +0.05 term that dominates at
 * near-black, so it reports this app's card on this app's background as 1.14:1 — a number that
 * would condemn a pairing anyone can see perfectly well on a phone. L\* is near-linear in perceived
 * lightness all the way down, so it can tell 1.9 points apart (invisible) from 7 (a clear step),
 * which is the judgement actually being made here.
 */
class SurfaceContrastTest {

    /**
     * The smallest step that still reads as an edge, in L\* points out of 100.
     *
     * Deliberately low: these are quiet containers, not text. The pairs this app relies on measure
     * 7 to 11; the fill that failed measured 1.9.
     */
    private val minimumStep = 4.0

    /** Ghost's fill, as `KpButtons` defines it. */
    private val ghostWash = Color.White.copy(alpha = 0.10f)

    @Test
    fun `a ghost button is visible on a card`() {
        // The case that was broken: friend rows, the trip-review card, sheet rows.
        assertSeparated(ghostWash.over(CharcoalCard), CharcoalCard, "ghost on card")
    }

    @Test
    fun `a ghost button is visible on the page background too`() {
        // The same tier, one surface further down. A fixed colour could satisfy one of these two
        // and never both, which is the whole argument for a wash.
        assertSeparated(ghostWash.over(CharcoalBg), CharcoalBg, "ghost on background")
    }

    @Test
    fun `the old fill really was invisible on a card`() {
        // Kept as the record of what this guards against.
        val step = step(CharcoalElevated, CharcoalCard)

        assertTrue("CharcoalElevated on CharcoalCard measured $step, which should be under the bar", step < minimumStep)
    }

    @Test
    fun `a card is visible on the page behind it`() {
        assertSeparated(CharcoalCard, CharcoalBg, "card on background")
    }

    @Test
    fun `an elevated surface is visible on a card`() {
        // A selected category row, which uses CharcoalElevated as a *surface* rather than as a
        // button fill. It is on the edge, and named here so a change to either is deliberate.
        assertTrue(
            "elevated on card measured ${step(CharcoalElevated, CharcoalCard)}",
            step(CharcoalElevated, CharcoalCard) > 1.0,
        )
    }

    private fun assertSeparated(surface: Color, behind: Color, what: String) {
        val step = step(surface, behind)
        assertTrue("$what is $step L* points apart, below the $minimumStep a container needs", step >= minimumStep)
    }

    private fun step(a: Color, b: Color): Double = abs(lightness(a) - lightness(b))

    /** [ghostWash] composited over an opaque colour, which is what the screen actually shows. */
    private fun Color.over(behind: Color): Color = Color(
        red = red * alpha + behind.red * (1 - alpha),
        green = green * alpha + behind.green * (1 - alpha),
        blue = blue * alpha + behind.blue * (1 - alpha),
    )

    /** CIE L\*, 0 (black) to 100 (white). */
    private fun lightness(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val y = 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
        return if (y > 0.008856) 116.0 * y.pow(1.0 / 3.0) - 16.0 else 903.3 * y
    }
}
