package com.vaibhav.kharchapaani.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rounds a rupee amount to the nearest paisa (half up). Sums of doubles land a hair either side of
 * a paisa (33.33 can be 33.329999…), so truncating with `(x * 100).toLong() / 100.0` loses a paisa
 * on one side and not the other; negative amounts were rounded toward zero as well.
 */
fun roundPaisa(rupees: Double): Double = BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP).toDouble()
