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
import com.vaibhav.kharchapaani.ui.theme.Spacing

/**
 * A friend's colour, for friends on this phone who aren't on the app: the whole spectrum across and
 * three shades down, so about 90 friends can each have a colour of their own (owner, 2026-09-27: at
 * least 50; the one-row bar ran out after ten). Spots too close to someone else's are dimmed; a tap
 * lands on the nearest free one, in the row that was tapped when it can. It never blocks adding a
 * friend: if every spot were taken, the tap keeps the colour it hit.
 */
@Composable
fun FriendColourField(
    selected: Hues.Slot?,
    taken: List<Hues.Slot>,
    onPick: (Hues.Slot) -> Unit,
    modifier: Modifier = Modifier,
    whose: String = "Their",
) {
    val rows = Hues.SHADES.size
    val colours = remember { List(rows) { row -> (0 until 360).map { Color(android.graphics.Color.parseColor(Hues.hex(it, Hues.SHADES[row]))) } } }
    val free = remember(taken) { List(rows) { row -> BooleanArray(360) { Hues.isFreeSlot(Hues.Slot(it, row), taken) } } }
    val onChange by rememberUpdatedState(onPick)
    val latestTaken by rememberUpdatedState(taken)
    val rowHeight = 48.dp
    val gap = 6.dp

    fun pick(x: Float, y: Float, width: Float, rowPx: Float, gapPx: Float) {
        val hue = ((x / width).coerceIn(0f, 0.9999f) * 360).toInt()
        val row = (y / (rowPx + gapPx)).toInt().coerceIn(0, rows - 1)
        val wanted = Hues.Slot(hue, row)
        onChange(Hues.nearestFreeSlot(wanted, latestTaken) ?: wanted)
    }

    fun step(direction: Int): Boolean {
        val from = selected ?: Hues.Slot(0, 1)
        val next = (1..359).map { Hues.Slot(Hues.norm(from.hue + direction * it), from.shade) }
            .firstOrNull { free[it.shade][it.hue] && Hues.distance(it.hue, from.hue) >= 6 } ?: return false
        onChange(next)
        return true
    }

    fun shadeStep(direction: Int): Boolean {
        val from = selected ?: return false
        val row = (from.shade + direction).coerceIn(0, rows - 1)
        Hues.nearestFreeSlot(from.copy(shade = row), latestTaken)?.let(onChange) ?: return false
        return true
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(rowHeight * rows + gap * (rows - 1))
                .testTag("friend_colour_field")
                .semantics {
                    contentDescription = "$whose colour"
                    stateDescription = selected?.let { "Hue ${it.hue}, ${listOf("light", "medium", "deep")[it.shade]}" } ?: "Not chosen"
                    customActions = listOf(
                        CustomAccessibilityAction("Next colour") { step(1) },
                        CustomAccessibilityAction("Previous colour") { step(-1) },
                        CustomAccessibilityAction("Lighter") { shadeStep(-1) },
                        CustomAccessibilityAction("Deeper") { shadeStep(1) },
                    )
                }
                .pointerInput(Unit) { detectTapGestures { pick(it.x, it.y, size.width.toFloat(), rowHeight.toPx(), gap.toPx()) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ -> pick(change.position.x, change.position.y, size.width.toFloat(), rowHeight.toPx(), gap.toPx()) }
                },
        ) {
            val slice = size.width / 360f
            val rowPx = rowHeight.toPx()
            val gapPx = gap.toPx()
            val corner = CornerRadius(12.dp.toPx())
            for (row in 0 until rows) {
                val top = row * (rowPx + gapPx)
                for (h in 0 until 360) {
                    drawRect(colours[row][h], topLeft = Offset(h * slice, top), size = Size(slice + 1f, rowPx))
                    if (!free[row][h]) drawRect(CharcoalBg.copy(alpha = 0.78f), topLeft = Offset(h * slice, top), size = Size(slice + 1f, rowPx))
                }
                // Softens the row's corners against the sheet.
                drawRoundRect(CharcoalBg, topLeft = Offset(-1f, top - 1f), size = Size(size.width + 2f, rowPx + 2f), cornerRadius = corner, style = Stroke(3.dp.toPx()))
            }
            selected?.let {
                val x = (it.hue + 0.5f) * slice
                val top = it.shade * (rowPx + gapPx)
                val w = 12.dp.toPx()
                drawRoundRect(
                    Color.White,
                    topLeft = Offset(x - w / 2, top + 3.dp.toPx()),
                    size = Size(w, rowPx - 6.dp.toPx()),
                    cornerRadius = CornerRadius(w / 2),
                    style = Stroke(3.dp.toPx()),
                )
            }
        }
        val room = remember(taken) { Hues.roomFor(taken) }
        Text(
            "Across for the colour, down for lighter or deeper. Dimmed spots are too close to someone else's. Room for about $room more.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}
