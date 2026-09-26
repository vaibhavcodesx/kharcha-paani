package com.vaibhav.kharchapaani.ui.shell

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Swiping sideways between Activity, Splits and Insights, without taking anything from the
 * gestures already on those screens.
 *
 * The conflict is real: activity rows swipe right to edit and left to delete, the filter chips and
 * the day strip scroll sideways, and the lists scroll up and down. A screen-wide gesture layered on
 * top would either steal those or fire alongside them — a delete that also changes tab.
 *
 * So the page listens *after* its children (the Final pass) and gives up the moment any child
 * consumes movement. A drag that starts on a swipeable row is claimed by the row once it passes
 * touch slop, exactly as before; one on a chip row or the day strip is claimed by that scroller;
 * one that turns vertical is claimed by the list. Only a drag nothing below wanted — on a header,
 * the hero figures, a card, empty space — reaches [TabSwipe.decide]. Edge zones were ruled out:
 * Android's own back gesture lives at the screen edges.
 */
object TabSwipe {

    /** Far enough that a sideways wobble while reading doesn't change tab. */
    const val MIN_DISTANCE_DP = 72f

    /** Clearly sideways: horizontal travel at least twice the vertical. */
    const val DOMINANCE = 2f

    /**
     * The tab to move to, or null to stay. [dx] positive is a swipe to the right, which goes back
     * a tab, the way a page turns.
     */
    fun decide(current: Int, count: Int, dx: Float, dy: Float, minDistancePx: Float): Int? {
        if (abs(dx) < minDistancePx) return null
        if (abs(dx) < DOMINANCE * abs(dy)) return null
        val target = if (dx < 0) current + 1 else current - 1
        return target.takeIf { it in 0 until count }
    }
}

/** See [TabSwipe]. */
fun Modifier.tabSwipe(current: Int, count: Int, onSelect: (Int) -> Unit): Modifier = composed {
    val minDistancePx = with(LocalDensity.current) { TabSwipe.MIN_DISTANCE_DP.dp.toPx() }
    pointerInput(current, count) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            var total = Offset.Zero
            var claimedBelow = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                // A second finger is a pinch or a mistake, not a page turn.
                if (event.changes.size > 1) claimedBelow = true
                if (!change.pressed) break
                if (change.isConsumed) claimedBelow = true
                total += change.positionChange()
            }
            if (!claimedBelow) {
                TabSwipe.decide(current, count, total.x, total.y, minDistancePx)?.let(onSelect)
            }
        }
    }
}
