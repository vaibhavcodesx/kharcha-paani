package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.SquircleShape

/**
 * Where a row sits in a group of rows drawn as one continuous panel: the outer corners are round,
 * the corners between rows stay tight, so a day of entries reads as one unit rather than a stack of
 * separate cards.
 */
enum class RowPosition {
    Single, First, Middle, Last;

    val shape: Shape
        get() = when (this) {
            Single -> SquircleShape(Outer)
            First -> SquircleShape(topStart = Outer, topEnd = Outer, bottomEnd = Inner, bottomStart = Inner)
            Middle -> SquircleShape(Inner)
            Last -> SquircleShape(topStart = Inner, topEnd = Inner, bottomEnd = Outer, bottomStart = Outer)
        }

    companion object {
        fun of(index: Int, count: Int): RowPosition = when {
            count == 1 -> Single
            index == 0 -> First
            index == count - 1 -> Last
            else -> Middle
        }

        private val Outer = 20.dp
        private val Inner = 6.dp
    }
}
