package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * A person's emoji if they picked one, else their initials, on their colour. Decorative: the name
 * always appears next to it.
 */
@Composable
fun PersonAvatar(
    name: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ringColor: Color? = null,
    emoji: String? = null,
) {
    val color = remember(colorHex) { parseColor(colorHex) }
    val shape = SquircleShape(size * 0.32f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(shape)
            // In an overlapping stack the tint must sit on something opaque, or initials show through.
            .then(if (ringColor != null) Modifier.background(ringColor).border(2.dp, ringColor, shape) else Modifier)
            .background(color.copy(alpha = 0.2f))
            .clearAndSetSemantics {},
    ) {
        if (emoji != null) {
            Text(text = emoji, fontSize = (size.value * 0.5f).sp, lineHeight = (size.value * 0.6f).sp)
        } else {
            Text(
                text = initials(name),
                style = if (size >= 48.dp) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                color = color,
            )
        }
    }
}

fun parseColor(hex: String, fallback: Color = WarmAmberPrimary): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

/** "Rohan Mehta" → "RM", "rohan" → "R", blank → "?". */
fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }
        .ifEmpty { "?" }
