package com.vaibhav.kharchapaani.ui.format

import com.vaibhav.kharchapaani.domain.MAX_AMOUNT_RUPEES
import com.vaibhav.kharchapaani.domain.roundPaisa
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The one way amounts are written on screen: Indian digit grouping (1,00,000), a ₹ sign, and paisa
 * only when there are any ("₹34.50", "₹35"). Rounding whole rupees (`%.0f`) showed ₹34.50 as ₹35,
 * and locale-less `String.format` wrote "34,50" on some phones.
 */
object Money {

    private val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("en").setRegion("IN").build())
    private val hundred = BigInteger.valueOf(100)

    /** Above this, saving asks "That's ₹10,00,000. Right?" first: one zero too many is easy to type. */
    const val LARGE_AMOUNT = 10_00_000.0

    /**
     * "1,23,456" and "1,23,456.78" in the Indian system: the first group of 3, then groups of 2.
     * A value that isn't a number draws as "0": this runs inside composition, where throwing
     * crashed the app (a pasted "1e999" did).
     */
    fun digits(amount: Double): String {
        if (!amount.isFinite()) return "0"
        val paisa = BigDecimal.valueOf(amount).abs().setScale(2, RoundingMode.HALF_UP).unscaledValue()
        val (rupees, fraction) = paisa.divideAndRemainder(hundred)
        val grouped = groupIndian(rupees.toString())
        return if (fraction.signum() == 0) grouped else "$grouped.${fraction.toString().padStart(2, '0')}"
    }

    /**
     * "₹35", "₹34.50", "-₹1,200"; [showPlus] prefixes positive amounts with "+" (money coming in).
     * Zero, and anything that isn't a number, is always "₹0".
     */
    fun format(amount: Double, showPlus: Boolean = false): String {
        if (!amount.isFinite()) return "₹0"
        val rounded = roundPaisa(amount)
        val sign = when {
            rounded < 0 -> "-"
            rounded > 0 && showPlus -> "+"
            else -> ""
        }
        return "$sign₹${digits(rounded)}"
    }

    /** A number for an editable text field: no ₹, no grouping, no trailing zeros ("33.33", "35", "12.5"). */
    fun input(amount: Double): String =
        if (!amount.isFinite()) "" else BigDecimal.valueOf(roundPaisa(amount)).stripTrailingZeros().toPlainString()

    /** Money prefilled into an amount field: whole rupees bare, paisa to two places ("1250", "1250.50"). */
    fun editable(amount: Double): String {
        if (!amount.isFinite()) return ""
        val paisa = BigDecimal.valueOf(roundPaisa(amount))
        return if (paisa.stripTrailingZeros().scale() <= 0) paisa.setScale(0).toPlainString() else paisa.setScale(2).toPlainString()
    }

    /**
     * The amount in what someone typed or pasted, or null if it isn't one. Grouping commas, spaces
     * and a ₹ sign are ignored ("₹1,200.50" is 1200.5); otherwise only digits with at most one "."
     * and two decimals, up to what the server stores. `toDoubleOrNull` read "NaN", "Infinity" and
     * "1e999" as numbers, and one such row stopped the account's sync for good.
     */
    fun parseInput(text: String): Double? {
        val clean = text.replace(noise, "")
        if (clean.isEmpty() || clean == "." || !plainNumber.matches(clean)) return null
        return clean.toDouble().takeIf { it <= MAX_AMOUNT_RUPEES }
    }

    /**
     * What an amount field keeps of an edit: the text without grouping commas, spaces or ₹, or null
     * to refuse the keystroke (a letter, a second ".", a third decimal, or more than the server
     * stores), so the field keeps what it had.
     */
    fun acceptInput(text: String): String? {
        val clean = text.replace(noise, "")
        if (!plainNumber.matches(clean)) return null
        if (clean.isNotEmpty() && clean != "." && clean.toDouble() > MAX_AMOUNT_RUPEES) return null
        return clean
    }

    private val noise = Regex("[,\\s₹]")
    private val plainNumber = Regex("""\d{0,10}(\.\d{0,2})?""")

    private fun groupIndian(s: String): String {
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val pairs = rest.reversed().chunked(2).joinToString(symbols.groupingSeparator.toString()) { it }.reversed()
        return pairs + symbols.groupingSeparator + last3
    }
}
