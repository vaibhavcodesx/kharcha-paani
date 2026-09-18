package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.outfitFontFamily

/**
 * The app's name, set once.
 *
 * The design audit's observation was that no brand mark appears anywhere across any screen, which
 * is a real part of why a settings page reads as generic. This is a wordmark rather than a logo:
 * the type system is the identity this app actually has, and inventing a glyph would be inventing
 * a second one.
 *
 * "Kharcha" takes the gradient, "Paani" stays quiet — the app is about the spending.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.clearAndSetSemantics {}) {
        Text(text = "Kharcha", style = Wordmark.copy(brush = BrandGradient.horizontal()))
        Text(
            text = "Paani",
            style = Wordmark.copy(color = MutedWarmGray, fontWeight = FontWeight.Normal),
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

private val Wordmark = TextStyle(
    fontFamily = outfitFontFamily,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = (-0.2).sp,
)
