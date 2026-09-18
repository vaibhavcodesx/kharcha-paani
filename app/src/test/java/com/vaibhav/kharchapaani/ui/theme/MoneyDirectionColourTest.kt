package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.vaibhav.kharchapaani.ui.components.FriendColours
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The colour rules the screens are supposed to share. Half-applied before this: green appeared on
 * exactly one group card, "you owe" was plain white everywhere, and the per-person palette had
 * near-duplicate hues that a 20% tint then flattened the rest of the way.
 */
class MoneyDirectionColourTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return max(la, lb) / min(la, lb).toDouble()
    }

    /** Hue in degrees, 0..360. */
    private fun hue(c: Color): Float {
        val r = c.red
        val g = c.green
        val b = c.blue
        val maxC = maxOf(r, g, b)
        val minC = minOf(r, g, b)
        val d = maxC - minC
        if (d == 0f) return 0f
        val h = when (maxC) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        return (h + 360f) % 360f
    }

    private fun hueGap(a: Float, b: Float): Float = abs(a - b).let { min(it, 360f - it) }

    @Test
    fun `the two money directions are different colours, not one colour and plain text`() {
        assertNotEquals(MoneyIn, MoneyOut)
    }

    @Test
    fun `a debt is not the brand amber`() {
        // Amber is the call to action. If a debt wears it, "tap me" and "you owe" look the same.
        assertTrue(hueGap(hue(MoneyOut), hue(WarmAmberPrimary)) > 8f)
    }

    @Test
    fun `both directions clear WCAG AA against every surface they sit on`() {
        for (surface in listOf(CharcoalBg, CharcoalSurface, CharcoalCard, CharcoalElevated)) {
            assertTrue("MoneyIn on $surface", contrast(MoneyIn, surface) >= 4.5)
            assertTrue("MoneyOut on $surface", contrast(MoneyOut, surface) >= 4.5)
        }
    }

    @Test
    fun `money in is the same green the income figures already used`() {
        assertEquals(ChartreuseSecondary, MoneyIn)
    }

    @Test
    fun `no two people can draw near-identical colours`() {
        val hues = FriendColours.map { hue(parseHex(it.first)) }
        for (i in hues.indices) {
            for (j in i + 1 until hues.size) {
                val gap = hueGap(hues[i], hues[j])
                assertTrue(
                    "${FriendColours[i].second} and ${FriendColours[j].second} are ${gap.toInt()}° apart",
                    gap >= 20f,
                )
            }
        }
    }

    @Test
    fun `nobody in the palette can be mistaken for You`() {
        // "You" is the brand amber (GroupInsights.MY_COLOR), and it is not in the palette, so it
        // has to stay clear of every colour that is.
        val mine = hue(WarmAmberPrimary)
        for ((hex, name) in FriendColours) {
            assertTrue("$name collides with You", hueGap(hue(parseHex(hex)), mine) >= 20f)
        }
    }

    @Test
    fun `an avatar's initials stay readable on every colour in the palette`() {
        // The initials are drawn in CharcoalBg on the solid colour, so every colour has to be
        // light enough to carry dark text.
        for ((hex, name) in FriendColours) {
            assertTrue("$name", contrast(parseHex(hex), CharcoalBg) >= 4.5)
        }
    }

    @Test
    fun `an empty bar track is visible against the card it sits on`() {
        // Was CharcoalBg on CharcoalCard: 1.2:1, i.e. not there at all.
        assertTrue(contrast(BarTrack, CharcoalCard) >= 1.5)
    }

    private fun parseHex(hex: String): Color {
        val v = hex.removePrefix("#").toLong(16)
        return Color(0xFF000000L or v)
    }
}
