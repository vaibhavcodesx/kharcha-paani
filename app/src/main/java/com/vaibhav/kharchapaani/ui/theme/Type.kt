package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.R

/**
 * The three families ship inside the APK.
 *
 * They used to be fetched at runtime from the Play Services font provider. When that provider is
 * missing, blocked, or simply hasn't downloaded a family yet, Compose falls back to the system sans
 * without saying a word — so the whole app silently rendered in Roboto on some devices and in its
 * own type on others, and the same screenshot could not be trusted twice. A bundled font is the
 * same font everywhere.
 *
 * Each is the family's variable font (one file, every weight on the `wght` axis), which is how
 * Google Fonts publishes all three; Space Grotesk has no static SemiBold at all. Android applies
 * the axis from API 26. On 24 and 25 the axis is ignored and every weight renders at the file's
 * default instance — still the right typeface, just one weight. The declared [FontWeight] keeps
 * Compose from synthesising a fake bold on top.
 */
@OptIn(ExperimentalTextApi::class)
private fun weightedFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

// Primary Display & Headings Font: Outfit
val outfitFontFamily = FontFamily(
    weightedFont(R.font.outfit_variable, FontWeight.Normal),
    weightedFont(R.font.outfit_variable, FontWeight.Medium),
    weightedFont(R.font.outfit_variable, FontWeight.SemiBold),
    weightedFont(R.font.outfit_variable, FontWeight.Bold),
    weightedFont(R.font.outfit_variable, FontWeight.ExtraBold),
)
val displayFontFamily = outfitFontFamily

// Body & Labels Font: Plus Jakarta Sans
val plusJakartaFontFamily = FontFamily(
    weightedFont(R.font.plus_jakarta_sans_variable, FontWeight.Normal),
    weightedFont(R.font.plus_jakarta_sans_variable, FontWeight.Medium),
    weightedFont(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold),
    weightedFont(R.font.plus_jakarta_sans_variable, FontWeight.Bold),
)

// Tabular Numerals & Finance Font: Space Grotesk
val numericFontFamily = FontFamily(
    weightedFont(R.font.space_grotesk_variable, FontWeight.Normal),
    weightedFont(R.font.space_grotesk_variable, FontWeight.Medium),
    weightedFont(R.font.space_grotesk_variable, FontWeight.SemiBold),
    weightedFont(R.font.space_grotesk_variable, FontWeight.Bold),
)

/**
 * The type scale screens should use as-is (no call-site weight/size overrides):
 * - Outfit carries display and headline steps with tight tracking: screen titles, section heads.
 * - Plus Jakarta Sans carries titles, body and labels; nothing below 12sp.
 * - Numbers use [MoneyText] (Space Grotesk, tabular figures) so digits don't jiggle while they animate.
 * Weights: Outfit SemiBold/Medium, Jakarta Regular/SemiBold (Medium for labels). Bold is not a default.
 */
val Typography = Typography(
    displayLarge = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-1.0).sp, color = OffWhiteText),
    displayMedium = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp, color = OffWhiteText),
    displaySmall = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp, color = OffWhiteText),
    headlineLarge = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = (-0.3).sp, color = OffWhiteText),
    headlineMedium = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp, color = OffWhiteText),
    headlineSmall = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp, color = OffWhiteText),
    titleLarge = TextStyle(fontFamily = outfitFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp, color = OffWhiteText),
    titleMedium = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp, color = OffWhiteText),
    titleSmall = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, color = OffWhiteText),
    bodyLarge = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, color = OffWhiteText),
    bodyMedium = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, color = MutedWarmGray),
    bodySmall = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.1.sp, color = SubMutedWarmGray),
    labelLarge = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp, color = OffWhiteText),
    labelMedium = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp, color = MutedWarmGray),
    labelSmall = TextStyle(fontFamily = plusJakartaFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp, color = SubMutedWarmGray),
)

/** Tabular figures: every digit takes the same width, so amounts don't shift as they change. */
private const val TABULAR = "tnum"

/** Money has its own voice: Space Grotesk, tabular, tighter tracking as sizes grow. */
object MoneyText {
    val hero = TextStyle(fontFamily = numericFontFamily, fontWeight = FontWeight.Medium, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.5).sp, fontFeatureSettings = TABULAR, color = OffWhiteText)
    val large = TextStyle(fontFamily = numericFontFamily, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp, fontFeatureSettings = TABULAR, color = OffWhiteText)
    val title = TextStyle(fontFamily = numericFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp, fontFeatureSettings = TABULAR, color = OffWhiteText)
    val row = TextStyle(fontFamily = numericFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, fontFeatureSettings = TABULAR, color = OffWhiteText)
    val small = TextStyle(fontFamily = numericFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, fontFeatureSettings = TABULAR, color = MutedWarmGray)
}

/** Small uppercase section labels ("THIS MONTH"). Wide tracking is what makes 12sp caps readable. */
val EyebrowText = TextStyle(
    fontFamily = plusJakartaFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.2.sp,
    color = SubMutedWarmGray,
)
