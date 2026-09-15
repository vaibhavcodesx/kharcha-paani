package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.text.TextStyle
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.MoneyText

/**
 * The size for amounts shown side by side in half-width columns. They step down together: sized
 * one at a time, "₹18,420.25" dropped a size while "₹42,000" beside it didn't, which made the
 * smaller number look less important. Half a phone's width holds about nine characters large.
 */
fun pairedFigureStyle(vararg amounts: Double): TextStyle =
    if (amounts.any { Money.format(it).length > 9 }) MoneyText.title else MoneyText.large
