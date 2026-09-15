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

// Functional Accent Colors
val WarmAmberPrimary = Color(0xFFFF7A3D) // Expenses, primary CTAs, streak flame
val WarmAmberLight = Color(0xFFFF9B6A)
val WarmAmberDark = Color(0xFFD45618)
val WarmAmberContainer = Color(0x33FF7A3D)

val ChartreuseSecondary = Color(0xFFC6F24C) // Income, owed to you, positive balance
val ChartreuseDark = Color(0xFF88B312)
val ChartreuseContainer = Color(0x33C6F24C)

// Typography & Neutrals
val OffWhiteText = Color(0xFFF4F0E8)
val MutedWarmGray = Color(0xFFA09A8F)
// Tertiary text (timestamps, captions). Was #6B665E, which measured 2.85–3.41:1 on the charcoal
// surfaces and failed WCAG AA; #8D887E is 4.6:1 on the lightest surface it sits on.
val SubMutedWarmGray = Color(0xFF8D887E)

// Category Accent Colors (Curated Muted Palette)
val CatFood = Color(0xFFFF9E79)
val CatTransport = Color(0xFF70C1FF)
val CatShopping = Color(0xFFFFB3BA)
val CatBills = Color(0xFFFFDFBA)
val CatEntertainment = Color(0xFFBAE1FF)
val CatHealth = Color(0xFFB5EAD7)
val CatRent = Color(0xFFE2F0CB)
val CatTeamPayment = Color(0xFFD7BDE2)
val CatGroceries = Color(0xFFF7E48A)
val CatInvestment = Color(0xFF9FB8FF)
val CatIncome = Color(0xFFC6F24C)
val CatOther = Color(0xFFD5D8DC)
val ElectricIndigo = Color(0xFF818CF8)
val SkyBlue = Color(0xFF38BDF8)

// Signature gradient: amber through a saturated warm gold to lime, so the middle never goes brown.
// Reserved for three places: the hero balance, the primary add/save action, the dock's active mark.
val BrandGold = Color(0xFFFFB547)
