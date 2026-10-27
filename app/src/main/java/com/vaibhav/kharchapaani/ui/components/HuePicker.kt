package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.Hues
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.Spacing

/**
 * Picks a colour along the whole spectrum (design item 11). Lightness and chroma are fixed
 * ([Hues]), so only the hue moves and every pick reads the same on the dark background.
 *
 * Arcs that can't be picked are dimmed: within [Hues.MIN_GAP] degrees of someone in [taken], and the
 * amber kept for "You". A tap or drag there lands on the nearest free hue instead, so it's never
 * possible to pick a colour that would then be refused.
 */
@Composable
fun HuePicker(
    hue: Int?,
    taken: List<Int>,
    onHueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    whose: String? = null,
) {
    val colourDescription = stringResource(R.string.colour_picker_description, whose ?: stringResource(R.string.colour_picker_your))
    val nextLabel = stringResource(R.string.colour_picker_next)
    val previousLabel = stringResource(R.string.colour_picker_previous)
    val selectionDescription = hue?.let { stringResource(R.string.colour_picker_hue, it) }
        ?: stringResource(R.string.colour_picker_unchosen)
    val colours = remember { (0 until 360).map { Color(android.graphics.Color.parseColor(Hues.hex(it))) } }
    val free = remember(taken) { BooleanArray(360) { Hues.isFree(it, taken) } }
    val onChange by rememberUpdatedState(onHueChange)
    val latestTaken by rememberUpdatedState(taken)
    fun pick(x: Float, width: Float) {
        val wanted = ((x / width).coerceIn(0f, 0.9999f) * 360).toInt()
        Hues.nearestFree(wanted, latestTaken)?.let(onChange)
    }
    fun step(direction: Int): Boolean {
        val from = hue ?: 0
        val next = (1..359).map { Hues.norm(from + direction * it) }.firstOrNull { free[it] && Hues.distance(it, from) >= 10 } ?: return false
        onChange(next)
        return true
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(KpShapes.medium)
                .testTag("hue_picker")
                .semantics {
                    contentDescription = colourDescription
                    stateDescription = selectionDescription
                    customActions = listOf(
                        CustomAccessibilityAction(nextLabel) { step(1) },
                        CustomAccessibilityAction(previousLabel) { step(-1) },
                    )
                }
                .pointerInput(Unit) { detectTapGestures { pick(it.x, size.width.toFloat()) } }
                .pointerInput(Unit) { detectHorizontalDragGestures { change, _ -> pick(change.position.x, size.width.toFloat()) } },
        ) {
            val slice = size.width / 360f
            for (h in 0 until 360) {
                drawRect(colours[h], topLeft = Offset(h * slice, 0f), size = Size(slice + 1f, size.height))
                if (!free[h]) drawRect(CharcoalBg.copy(alpha = 0.72f), topLeft = Offset(h * slice, 0f), size = Size(slice + 1f, size.height))
            }
            hue?.let {
                val x = (it + 0.5f) * slice
                val w = 10.dp.toPx()
                drawRoundRect(Color.White, topLeft = Offset(x - w / 2, 2.dp.toPx()), size = Size(w, size.height - 4.dp.toPx()), cornerRadius = CornerRadius(w / 2), style = Stroke(3.dp.toPx()))
            }
        }
        Text(
            if (taken.isEmpty()) stringResource(R.string.colour_picker_drag)
            else stringResource(R.string.colour_picker_taken),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

/** The colour a hue draws as, for previews. */
fun hueColour(hue: Int): Color = Color(android.graphics.Color.parseColor(Hues.hex(hue)))
