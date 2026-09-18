package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.ui.graphics.Color

// Kharcha Paani warm dark palette (dark only, by design)
val CharcoalBg = Color(0xFF0E0D0C)
val CharcoalSurface = Color(0xFF181615)
val CharcoalElevated = Color(0xFF22201D)
val CharcoalBorder = Color(0xFF2E2B27)
val CharcoalCard = Color(0xFF1E1C1A)

// Boundary for interactive controls (unselected chips, inputs, toggles): 3:1 on cards, as WCAG
// requires for UI components. CharcoalBorder stays for purely decorative dividers.
val OutlineInteractive = Color(0xFF726D65)

/**
 * The unfilled part of a progress bar. A neutral lift off the card, not the page background:
 * CharcoalBg on a CharcoalCard row measured 1.2:1, so the empty part of a bar was not there at all
 * and a small share read as a short line floating in space rather than as a small share.
 */
val BarTrack = Color(0xFF423E37)

// Functional Accent Colors
val WarmAmberPrimary = Color(0xFFFF7A3D) // Expenses, primary CTAs, streak flame
val WarmAmberLight = Color(0xFFFF9B6A)
val WarmAmberDark = Color(0xFFD45618)
val WarmAmberContainer = Color(0x33FF7A3D)

val ChartreuseSecondary = Color(0xFFC6F24C) // Income, owed to you, positive balance
val ChartreuseDark = Color(0xFF88B312)
val ChartreuseContainer = Color(0x33C6F24C)

// Which way the money goes. One pair, used on every screen that shows a direction, so "green means
// it is coming to you" is learned once. Half-applied before this: green appeared on exactly one
// group card, and "you owe" was plain white everywhere, which is the same colour as a neutral
// figure and so carried no meaning at all.
//
// MoneyOut is deliberately not WarmAmberPrimary: amber is the brand and the call to action, and a
// debt is neither. A red-shifted coral reads as a direction without reading as an error.
val MoneyIn = ChartreuseSecondary
val MoneyOut = Color(0xFFFF6F61)

// Typography & Neutrals
val OffWhiteText = Color(0xFFF4F0E8)
val MutedWarmGray = Color(0xFFA09A8F)
// Tertiary text (timestamps, captions). Was #6B665E, which measured 2.85–3.41:1 on the charcoal
// surfaces and failed WCAG AA; #8D887E is 4.6:1 on the lightest surface it sits on.
val SubMutedWarmGray = Color(0xFF8D887E)

// Category accent colours, placed deliberately around the hue wheel rather than picked one at a
// time. The category ring in Insights puts all of them in one circle, where "three shades of blue"
// stops being a nicety and starts being unreadable. Transport, Entertainment and Investment were
// all blue; Rent and Health were both pale green.
val CatFood = Color(0xFFFF9E79) // coral, 17
val CatBills = Color(0xFFFFD08A) // gold, 36
val CatGroceries = Color(0xFFF7E48A) // yellow, 50
val CatIncome = Color(0xFFC6F24C) // lime, 76
val CatHealth = Color(0xFF8BE3B4) // mint, 148
val CatInvestment = Color(0xFF5BD6C8) // teal, 173
val CatTransport = Color(0xFF70C1FF) // sky, 206
val CatRent = Color(0xFF8FA8F0) // periwinkle, 235
val CatEntertainment = Color(0xFFBB93F2) // violet, 272
val CatTeamPayment = Color(0xFFE48CEC) // orchid, 305
val CatShopping = Color(0xFFFF8FB8) // pink, 338
val CatOther = Color(0xFFD5D8DC) // grey: no hue on purpose, for anything unrecognised
val ElectricIndigo = Color(0xFF818CF8)
val SkyBlue = Color(0xFF38BDF8)

// Signature gradient: amber through a saturated warm gold to lime, so the middle never goes brown.
// Reserved for three places: the hero balance, the primary add/save action, the dock's active mark.
val BrandGold = Color(0xFFFFB547)
