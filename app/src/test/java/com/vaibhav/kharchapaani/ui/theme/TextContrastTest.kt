package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The colours that carry meaning, against the surfaces they are read on.
 *
 * Two of round 2's carried-over items (§10.2 and §10.7) were "worth checking, not yet confirmed
 * either way". This is the check, kept rather than done once, so the answer stays true.
 *
 * WCAG ratio is the right measure here — unlike [SurfaceContrastTest], this is text, which is
 * exactly what the ratio was built for.
 */
class TextContrastTest {

    /** WCAG AA for body text. */
    private val bodyMinimum = 4.5

    /** WCAG AA for large text (18.66sp bold and up) — the hero figures and row amounts. */
    private val largeMinimum = 3.0

    /** Disabled text is exempt from WCAG, but it still has to be legible enough to read a label. */
    private val disabledMinimum = 3.0

    private val surfaces = mapOf(
        "the page" to CharcoalBg,
        "a card" to CharcoalCard,
        "an elevated row" to CharcoalElevated,
    )

    @Test
    fun `money direction reads on every surface it appears on`() {
        // §10.7: "You're owed ₹949" in green and a debt in coral, at row-amount size.
        for ((where, surface) in surfaces) {
            assertAtLeast(MoneyIn, surface, largeMinimum, "MoneyIn on $where")
            assertAtLeast(MoneyOut, surface, largeMinimum, "MoneyOut on $where")
        }
    }

    @Test
    fun `money direction also holds at body size, where the figures are small`() {
        // A settled-up row and the splits summary print these at body size too, so the stricter
        // bar is the honest one to hold them to.
        for ((where, surface) in surfaces) {
            assertAtLeast(MoneyIn, surface, bodyMinimum, "MoneyIn at body size on $where")
            assertAtLeast(MoneyOut, surface, bodyMinimum, "MoneyOut at body size on $where")
        }
    }

    @Test
    fun `a disabled button's label is still readable`() {
        // §10.2: "Add an amount" on a sheet's action bar, which is disabled until one is typed —
        // it is the instruction telling you what to do, so it being hard to read defeats it.
        assertAtLeast(MutedWarmGray, CharcoalElevated, disabledMinimum, "disabled label on a button")
        assertAtLeast(MutedWarmGray, CharcoalCard, disabledMinimum, "disabled label on a card")
    }

    @Test
    fun `secondary and tertiary text clear AA on the surfaces they sit on`() {
        for ((where, surface) in surfaces) {
            assertAtLeast(OffWhiteText, surface, bodyMinimum, "body text on $where")
            assertAtLeast(MutedWarmGray, surface, bodyMinimum, "secondary text on $where")
            assertAtLeast(SubMutedWarmGray, surface, bodyMinimum, "tertiary text on $where")
        }
    }

    @Test
    fun `the amber used for links and accents reads as text`() {
        assertAtLeast(WarmAmberLight, CharcoalBg, bodyMinimum, "amber link on the page")
        assertAtLeast(WarmAmberLight, CharcoalCard, bodyMinimum, "amber link on a card")
    }

    @Test
    fun `every category colour reads against the card its row sits on`() {
        // They are used as bar fills and legend dots, but also as text in places.
        val categories = mapOf(
            "Food" to CatFood, "Bills" to CatBills, "Groceries" to CatGroceries,
            "Income" to CatIncome, "Health" to CatHealth, "Investment" to CatInvestment,
            "Transport" to CatTransport, "Beverages" to CatBeverages,
            "Packaged goods" to CatPackagedGoods, "Self-care" to CatSelfCare,
        )
        for ((name, colour) in categories) {
            assertAtLeast(colour, CharcoalCard, largeMinimum, "$name on a card")
        }
    }

    private fun assertAtLeast(foreground: Color, background: Color, minimum: Double, what: String) {
        val ratio = contrast(foreground, background)
        assertTrue("$what is ${"%.2f".format(ratio)}:1, below $minimum:1", ratio >= minimum)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(v: Float): Double {
            val c = v.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
