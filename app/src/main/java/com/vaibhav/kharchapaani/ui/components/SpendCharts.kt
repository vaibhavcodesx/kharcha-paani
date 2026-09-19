package com.vaibhav.kharchapaani.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.domain.DaySpend
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.BarTrack
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One day's cell in the strip under the flow chart. */
private val DayCellWidth = 52.dp

/** Big enough that a month total fits inside the hole without touching the arcs around it. */
private val RingSize = 148.dp
private val RingStroke = 20.dp

/**
 * Where the month went, as a ring.
 *
 * Insights had no chart at all — only flat bars, while the one donut in the app sat on the group
 * sheet. Each arc is its category's own colour, so the ring is readable without the legend, which
 * is the whole point of drawing one.
 *
 * Tapping an arc selects that category, the same selection the list below already uses, so the
 * chart and the list are two views of one state rather than two controls.
 */
@Composable
fun CategoryRing(
    categories: List<CategorySummary>,
    total: Double,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (categories.isEmpty()) return
    val slices = remember(categories) { categories.filter { it.amount > 0.0 } }
    if (slices.isEmpty()) return

    val sweeps = remember(slices) {
        val sum = slices.sumOf { it.amount }
        slices.map { (it.amount / sum).toFloat() }
    }
    val grown by animateFloatAsState(1f, animationSpec = tween(600), label = "ring_grow")
    val focused = slices.indexOfFirst { it.category == selected }.takeIf { it >= 0 }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l)
            .testTag("category_ring"),
    ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(RingSize)) {
            Canvas(
                Modifier
                    .size(RingSize)
                    .pointerInput(slices) {
                        detectTapGestures { offset ->
                            val index = sliceAt(offset, size.width.toFloat(), size.height.toFloat(), sweeps)
                            if (index != null) {
                                val category = slices[index].category
                                onSelect(if (category == selected) null else category)
                            }
                        }
                    }
                    .semantics {
                        contentDescription = slices.joinToString(", ") {
                            "${it.category} ${it.percentage.roundToInt()} percent"
                        }
                    },
            ) {
                val stroke = RingStroke.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                var start = -90f
                slices.forEachIndexed { index, slice ->
                    val sweep = sweeps[index] * 360f * grown
                    val colour = CategoryHelper.getCategoryColor(slice.category)
                    // A slice that isn't the focused one steps back rather than disappearing.
                    val dimmed = focused != null && focused != index
                    val inset = stroke / 2
                    drawArc(
                        color = if (dimmed) colour.copy(alpha = 0.28f) else colour,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                    // A hairline of the card between slices, so neighbouring colours stay apart.
                    drawArc(
                        color = CharcoalCard,
                        startAngle = start,
                        sweepAngle = 1.2f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics {}) {
                val slice = focused?.let { slices[it] }
                Text(
                    text = (slice?.category ?: "Spent").uppercase(),
                    style = EyebrowText,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(RingSize - RingStroke * 2 - Spacing.s),
                )
                // The hole is what it is: a month total in MoneyText.title spilled straight over
                // the arcs around it.
                Text(
                    text = Money.format(slice?.amount ?: total),
                    style = MoneyText.row,
                    color = OffWhiteText,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(RingSize - RingStroke * 2 - Spacing.s),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f).padding(start = Spacing.l),
        ) {
            slices.take(LEGEND_BESIDE_RING).forEach { slice ->
                RingLegendRow(
                    slice = slice,
                    selected = slice.category == selected,
                    onClick = { onSelect(if (slice.category == selected) null else slice.category) },
                )
            }
        }
    }
        // Anything that doesn't fit beside the ring continues underneath at full width, rather
        // than being replaced by "+2 more below" — which named a number and then made you go and
        // find it. The count is whatever this person's categories come to, custom ones included,
        // so it is never capped and never needs its own scroller inside a page that already
        // scrolls.
        val overflow = slices.drop(LEGEND_BESIDE_RING)
        if (overflow.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.m).testTag("ring_legend_overflow"),
            ) {
                overflow.forEach { slice ->
                    RingLegendRow(
                        slice = slice,
                        selected = slice.category == selected,
                        onClick = { onSelect(if (slice.category == selected) null else slice.category) },
                    )
                }
            }
        }
    }
}

/** How many legend rows stand beside the ring before the rest continue below it. */
private const val LEGEND_BESIDE_RING = 6

@Composable
private fun RingLegendRow(slice: CategorySummary, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(KpShapes.small)
            .clickable(onClickLabel = "Show ${slice.category}", onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
            }
            .padding(vertical = 2.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(KpShapes.small)
                .background(CategoryHelper.getCategoryColor(slice.category))
        )
        Text(
            text = slice.category,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) OffWhiteText else MutedWarmGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.s),
        )
        Text(
            text = "${slice.percentage.roundToInt()}%",
            style = MoneyText.small,
            color = if (selected) OffWhiteText else MutedWarmGray,
        )
    }
}

/** Which slice a tap landed on, or null for a tap in the hole or outside the ring. */
internal fun sliceAt(offset: Offset, width: Float, height: Float, sweeps: List<Float>): Int? {
    val cx = width / 2f
    val cy = height / 2f
    val dx = offset.x - cx
    val dy = offset.y - cy
    val radius = kotlin.math.sqrt(dx * dx + dy * dy)
    val outer = minOf(width, height) / 2f
    // The ring is the outer ~33% of the radius; the hole holds the total and is not a target.
    if (radius > outer || radius < outer * 0.55f) return null

    val degrees = ((Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())) + 90.0 + 360.0) % 360.0).toFloat()
    var start = 0f
    sweeps.forEachIndexed { index, fraction ->
        val end = start + fraction * 360f
        if (degrees >= start && degrees < end) return index
        start = end
    }
    return sweeps.lastIndex.takeIf { it >= 0 }
}

/**
 * Spending day by day across the month: an area chart with a tap-to-pin callout, and the exact
 * numbers in a strip underneath.
 *
 * Follows the pattern the owner picked out (backlog §5, Image 12) closely — chart above, figures
 * below, tooltip on the day you tap — because the chart alone answers "was that a big week" and
 * only the figures answer "how much exactly".
 */
@Composable
fun DailyFlowChart(
    days: List<DaySpend>,
    selectedDay: Int?,
    onSelectDay: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (days.isEmpty()) return
    val elapsed = remember(days) { days.filter { it.hasHappened } }
    if (elapsed.isEmpty() || elapsed.all { it.amount <= 0.0 }) return

    val peak = remember(elapsed) { elapsed.maxOf { it.amount }.coerceAtLeast(1.0) }
    val density = LocalDensity.current
    val grown by animateFloatAsState(1f, animationSpec = tween(700), label = "flow_grow")
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val pinned = selectedDay?.let { index -> elapsed.getOrNull(index) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l)
            .testTag("daily_flow_chart"),
    ) {
        // The callout keeps its row whether or not a day is pinned, so the chart doesn't jump.
        Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.CenterStart) {
            if (pinned == null) {
                Text(
                    "Tap a day to see what it cost",
                    style = MaterialTheme.typography.bodySmall,
                    color = SubMutedWarmGray,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(KpShapes.small)
                        .background(CharcoalElevated)
                        .padding(horizontal = Spacing.m, vertical = Spacing.xs)
                        .testTag("flow_tooltip"),
                ) {
                    Text(
                        text = pinned.date.format(dayFormat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedWarmGray,
                    )
                    Text(
                        text = Money.format(pinned.amount),
                        style = MoneyText.small,
                        color = OffWhiteText,
                        modifier = Modifier.padding(start = Spacing.s),
                    )
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = Spacing.s)
                .pointerInput(elapsed) {
                    detectTapGestures { offset ->
                        val index = ((offset.x / size.width) * elapsed.size)
                            .toInt()
                            .coerceIn(0, elapsed.lastIndex)
                        onSelectDay(if (index == selectedDay) null else index)
                    }
                }
                .semantics {
                    contentDescription = "Spending day by day. Busiest day " +
                        (elapsed.maxByOrNull { it.amount }?.let { "${it.date.dayOfMonth}, ${Money.format(it.amount)}" } ?: "none")
                }
                .testTag("flow_canvas"),
        ) {
            val step = if (elapsed.size > 1) size.width / (elapsed.size - 1) else size.width
            fun pointFor(index: Int): Offset {
                val value = (elapsed[index].amount / peak).toFloat().coerceIn(0f, 1f) * grown
                return Offset(x = index * step, y = size.height - value * size.height)
            }

            // The filled area under the line, fading out as it falls away from the line itself.
            val area = Path().apply {
                moveTo(0f, size.height)
                elapsed.indices.forEach { lineTo(pointFor(it).x, pointFor(it).y) }
                lineTo((elapsed.size - 1) * step, size.height)
                close()
            }
            drawPath(
                path = area,
                brush = Brush.verticalGradient(
                    0f to WarmAmberPrimary.copy(alpha = 0.40f),
                    1f to WarmAmberPrimary.copy(alpha = 0.02f),
                ),
            )
            val line = Path().apply {
                moveTo(pointFor(0).x, pointFor(0).y)
                elapsed.indices.drop(1).forEach { lineTo(pointFor(it).x, pointFor(it).y) }
            }
            drawPath(line, color = WarmAmberPrimary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))

            if (selectedDay != null && selectedDay in elapsed.indices) {
                val point = pointFor(selectedDay)
                // A full-height rule, so the pinned day is findable without hunting for the dot.
                drawLine(
                    color = BarTrack,
                    start = Offset(point.x, 0f),
                    end = Offset(point.x, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(color = CharcoalCard, radius = 6.dp.toPx(), center = point)
                drawCircle(color = WarmAmberPrimary, radius = 4.dp.toPx(), center = point)
            }
        }

        // The exact numbers, because the chart only answers "was that a lot".
        //
        // Scrolled to the end, not the start: a month opens on the 1st, and early in the month the
        // first six cells are all "—" while the spending is two weeks along. The most recent day is
        // the one you came to look at.
        val strip = rememberScrollState()
        LaunchedEffect(elapsed.size) { strip.scrollTo(strip.maxValue) }
        LaunchedEffect(selectedDay) {
            val index = selectedDay ?: return@LaunchedEffect
            val cellPx = with(density) { (DayCellWidth + Spacing.xs).toPx() }
            strip.animateScrollTo((index * cellPx - cellPx * 2).toInt().coerceAtLeast(0))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.m)
                .horizontalScroll(strip),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            elapsed.forEachIndexed { index, day ->
                DayCell(
                    day = day,
                    selected = index == selectedDay,
                    onClick = { onSelectDay(if (index == selectedDay) null else index) },
                )
            }
        }
    }
}

@Composable
private fun DayCell(day: DaySpend, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(DayCellWidth)
            .clip(KpShapes.small)
            .background(if (selected) CharcoalElevated else Color.Transparent)
            .clickable(onClickLabel = "Show ${day.date.dayOfMonth}", onClick = onClick)
            .semantics {
                role = Role.Button
                this.selected = selected
                contentDescription = "${day.date.dayOfMonth}, ${Money.format(day.amount)}"
            }
            .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) OffWhiteText else SubMutedWarmGray,
        )
        Text(
            text = if (day.amount > 0.0) Money.digits(day.amount) else "—",
            style = MoneyText.small,
            color = if (selected) OffWhiteText else MutedWarmGray,
            maxLines = 1,
        )
    }
}
