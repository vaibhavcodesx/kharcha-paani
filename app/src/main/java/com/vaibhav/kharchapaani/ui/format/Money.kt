package com.vaibhav.kharchapaani.ui.format

import com.vaibhav.kharchapaani.domain.roundPaisa
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

/**
 * The one way amounts are written on screen: Indian digit grouping (1,00,000), a ₹ sign, and paisa
 * only when there are any ("₹34.50", "₹35"). Rounding whole rupees (`%.0f`) showed ₹34.50 as ₹35,
 * and locale-less `String.format` wrote "34,50" on some phones.
 */
object Money {

    private val symbols = DecimalFormatSymbols(Locale.Builder().setLanguage("en").setRegion("IN").build())

    /** "1,23,456" and "1,23,456.78" in the Indian system: the first group of 3, then groups of 2. */
    fun digits(amount: Double): String {
        val paisa = BigDecimal.valueOf(abs(roundPaisa(amount))).movePointRight(2).longValueExact()
        val rupees = paisa / 100
        val fraction = paisa % 100
        val grouped = groupIndian(rupees)
        return if (fraction == 0L) grouped else "$grouped.${fraction.toString().padStart(2, '0')}"
    }

    /**
     * "₹35", "₹34.50", "-₹1,200"; [showPlus] prefixes positive amounts with "+" (money coming in).
     * Zero is always "₹0".
     */
    fun format(amount: Double, showPlus: Boolean = false): String {
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
        BigDecimal.valueOf(roundPaisa(amount)).stripTrailingZeros().toPlainString()

    /** Money prefilled into an amount field: whole rupees bare, paisa to two places ("1250", "1250.50"). */
    fun editable(amount: Double): String {
        val paisa = BigDecimal.valueOf(roundPaisa(amount))
        return if (paisa.stripTrailingZeros().scale() <= 0) paisa.setScale(0).toPlainString() else paisa.setScale(2).toPlainString()
    }

    private fun groupIndian(value: Long): String {
        val s = value.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        val rest = s.dropLast(3)
        val pairs = rest.reversed().chunked(2).joinToString(symbols.groupingSeparator.toString()) { it }.reversed()
        return pairs + symbols.groupingSeparator + last3
    }
}
