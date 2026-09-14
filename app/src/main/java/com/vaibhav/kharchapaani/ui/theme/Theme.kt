package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SplitFlowDarkColorScheme = darkColorScheme(
    primary = WarmAmberPrimary,
    onPrimary = CharcoalBg,
    primaryContainer = WarmAmberContainer,
    onPrimaryContainer = WarmAmberLight,
    secondary = ChartreuseSecondary,
    onSecondary = CharcoalBg,
    secondaryContainer = ChartreuseContainer,
    onSecondaryContainer = ChartreuseSecondary,
    tertiary = WarmAmberLight,
    onTertiary = CharcoalBg,
    background = CharcoalBg,
    onBackground = OffWhiteText,
    surface = CharcoalSurface,
    onSurface = OffWhiteText,
    surfaceVariant = CharcoalElevated,
    onSurfaceVariant = MutedWarmGray,
    outline = CharcoalBorder,
    outlineVariant = CharcoalElevated
)

@Composable
fun KharchaPaaniTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Dark only, by the owner's decision (Part B): there is no light scheme to fall back to.
    MaterialTheme(
        colorScheme = SplitFlowDarkColorScheme,
        typography = Typography,
        shapes = KpMaterialShapes,
        content = content
    )
}

@Composable
fun SplitFlowTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    KharchaPaaniTheme(darkTheme, dynamicColor, content)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    KharchaPaaniTheme(darkTheme, dynamicColor, content)
}
