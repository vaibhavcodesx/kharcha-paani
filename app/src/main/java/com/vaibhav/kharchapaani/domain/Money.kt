package com.vaibhav.kharchapaani.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounds a rupee amount to the nearest paisa (half up). Sums of doubles land a hair either side of
 * a paisa (33.33 can be 33.329999…), so truncating with `(x * 100).toLong() / 100.0` loses a paisa
 * on one side and not the other; negative amounts were rounded toward zero as well.
 */
fun roundPaisa(rupees: Double): Double = BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP).toDouble()

/** The largest amount the server's `numeric(12,2)` columns hold: ₹9,99,99,99,999.99. */
const val MAX_AMOUNT_RUPEES = 9_999_999_999.99

/**
 * Whether [amount] can be saved and synced: a real number above zero that the server can store. A
 * pasted "Infinity" used to be saved, and its upload threw on every sync, so nothing else synced.
 */
fun isSavableAmount(amount: Double): Boolean = amount.isFinite() && amount > 0 && amount <= MAX_AMOUNT_RUPEES
