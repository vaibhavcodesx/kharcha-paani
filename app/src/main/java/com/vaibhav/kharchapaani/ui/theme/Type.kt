package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.R

// Google Font Provider Setup
val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

// Primary Display & Headings Font: Outfit / Syne
val outfitFont = GoogleFont("Outfit")
val outfitFontFamily = FontFamily(
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.Bold),
    Font(googleFont = outfitFont, fontProvider = fontProvider, weight = FontWeight.ExtraBold)
)
val displayFontFamily = outfitFontFamily


// Body & Labels Font: Plus Jakarta Sans
val plusJakartaFont = GoogleFont("Plus Jakarta Sans")
val plusJakartaFontFamily = FontFamily(
    Font(googleFont = plusJakartaFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = plusJakartaFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = plusJakartaFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = plusJakartaFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

// Tabular Numerals & Finance Font: Space Grotesk / JetBrains Mono
val spaceGroteskFont = GoogleFont("Space Grotesk")
val numericFontFamily = FontFamily(
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = spaceGroteskFont, fontProvider = fontProvider, weight = FontWeight.Bold)
)

val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.75).sp,
        color = OffWhiteText
    ),
    displayMedium = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
        color = OffWhiteText
    ),
    displaySmall = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
        color = OffWhiteText
    ),
    headlineLarge = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
        color = OffWhiteText
    ),
    headlineMedium = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = OffWhiteText
    ),
    headlineSmall = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = OffWhiteText
    ),
    titleLarge = TextStyle(
        fontFamily = outfitFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = OffWhiteText
    ),
    titleMedium = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
        color = OffWhiteText
    ),
    titleSmall = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        color = OffWhiteText
    ),
    bodyLarge = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = OffWhiteText
    ),
    bodyMedium = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
        color = MutedWarmGray
    ),
    bodySmall = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.2.sp,
        color = SubMutedWarmGray
    ),
    labelLarge = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
        color = OffWhiteText
    ),
    labelMedium = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
        color = MutedWarmGray
    ),
    labelSmall = TextStyle(
        fontFamily = plusJakartaFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.4.sp,
        color = SubMutedWarmGray
    )
)
