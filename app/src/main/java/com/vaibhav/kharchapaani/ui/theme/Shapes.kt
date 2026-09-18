package com.vaibhav.kharchapaani.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Shapes
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Continuous-curvature ("squircle") corners: the curve eases into the straight edge instead of a
 * circular arc meeting it abruptly, the way iOS/Liquid Glass corners do. Each corner has its own
 * radius so asymmetric shapes use the same geometry.
 *
 * @param smoothing 0 gives a plain rounded rectangle; 0.6 matches iOS's continuous corners.
 */
class SquircleShape(
    topStart: CornerSize,
    topEnd: CornerSize = topStart,
    bottomEnd: CornerSize = topStart,
    bottomStart: CornerSize = topStart,
    private val smoothing: Float = 0.6f,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

    constructor(
        topStart: Dp,
        topEnd: Dp = topStart,
        bottomEnd: Dp = topStart,
        bottomStart: Dp = topStart,
        smoothing: Float = 0.6f,
    ) : this(CornerSize(topStart), CornerSize(topEnd), CornerSize(bottomEnd), CornerSize(bottomStart), smoothing)

    override fun copy(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize) =
        SquircleShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val ltr = layoutDirection == LayoutDirection.Ltr
        return Outline.Generic(
            squirclePath(
                size,
                tl = if (ltr) topStart else topEnd,
                tr = if (ltr) topEnd else topStart,
                br = if (ltr) bottomEnd else bottomStart,
                bl = if (ltr) bottomStart else bottomEnd,
                smoothing = smoothing,
            )
        )
    }

    override fun equals(other: Any?): Boolean =
        other is SquircleShape && other.topStart == topStart && other.topEnd == topEnd &&
            other.bottomEnd == bottomEnd && other.bottomStart == bottomStart && other.smoothing == smoothing

    override fun hashCode(): Int = listOf(topStart, topEnd, bottomEnd, bottomStart, smoothing).hashCode()
}

/**
 * Each corner is one cubic from a point [extent] = radius × (1 + smoothing) along one edge to the same
 * distance along the other, with both handles on the edges. The handle length is chosen so the curve's
 * midpoint lands where a circular arc of that radius would, so the corner looks the same size as a
 * plain rounded corner but eases into the edges. At smoothing 0 it reduces to the standard circle
 * approximation (handle factor 0.5523).
 */
private fun squirclePath(size: Size, tl: Float, tr: Float, br: Float, bl: Float, smoothing: Float): Path {
    val w = size.width
    val h = size.height
    val maxExtent = min(w, h) / 2f

    /** Returns (extent, handle offset from the corner vertex). */
    fun corner(radius: Float): Pair<Float, Float> {
        if (radius <= 0f) return 0f to 0f
        val r = min(radius, maxExtent)
        val extent = min(r * (1 + smoothing), maxExtent)
        val effectiveSmoothing = extent / r - 1f
        // Midpoint of the cubic (both handles on the edges) = extent × (4 − 3c) / 8 from each edge;
        // a circular arc's midpoint is r × (1 − cos 45°).
        val c = ((4f - 8f * 0.29289f / (1f + effectiveSmoothing)) / 3f).coerceIn(0f, 1f)
        return extent to extent * (1f - c)
    }

    val (eTL, hTL) = corner(tl)
    val (eTR, hTR) = corner(tr)
    val (eBR, hBR) = corner(br)
    val (eBL, hBL) = corner(bl)

    return Path().apply {
        moveTo(eTL, 0f)
        lineTo(w - eTR, 0f)
        cubicTo(w - hTR, 0f, w, hTR, w, eTR)
        lineTo(w, h - eBR)
        cubicTo(w, h - hBR, w - hBR, h, w - eBR, h)
        lineTo(eBL, h)
        cubicTo(hBL, h, 0f, h - hBL, 0f, h - eBL)
        lineTo(0f, eTL)
        cubicTo(0f, hTL, hTL, 0f, eTL, 0f)
        close()
    }
}

/**
 * A ticket: squircle corners with a half-circle notch cut into both long sides at [notchFraction]
 * of the height. Used for money that changed hands (settlements, the confirm summary).
 */
class TicketShape(
    private val corner: Dp = 18.dp,
    private val notchRadius: Dp = 7.dp,
    private val notchFraction: Float = 0.5f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { corner.toPx() }
        val n = with(density) { notchRadius.toPx() }
        val body = squirclePath(size, r, r, r, r, 0.6f)
        val y = size.height * notchFraction
        val notches = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(-n, y - n, n, y + n))
            addOval(androidx.compose.ui.geometry.Rect(size.width - n, y - n, size.width + n, y + n))
        }
        return Outline.Generic(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, body, notches))
    }
}

/** The shape system. Three container sizes, one signature action shape, plus the ticket. */
object KpShapes {
    /** Chips, small controls, inline badges. */
    val small = SquircleShape(12.dp)

    /** Rows, inputs, compact cards. */
    val medium = SquircleShape(18.dp)

    /** Hero surfaces, sheets' inner panels, large cards. */
    val large = SquircleShape(28.dp)

    /** Top corners of sheets and the dock. */
    val sheet = SquircleShape(topStart = 32.dp, topEnd = 32.dp, bottomEnd = 0.dp, bottomStart = 0.dp)

    /**
     * The primary action ("leaf"): generous on two opposite corners, tight on the other two, so a
     * CTA never reads as a stock pill or rectangle.
     */
    val action = SquircleShape(topStart = 22.dp, topEnd = 8.dp, bottomEnd = 22.dp, bottomStart = 8.dp)

    val ticket = TicketShape()
}

/** Material components that read MaterialTheme.shapes get the same geometry. */
val KpMaterialShapes = Shapes(
    extraSmall = SquircleShape(8.dp),
    small = KpShapes.small,
    medium = KpShapes.medium,
    large = KpShapes.large,
    extraLarge = SquircleShape(32.dp),
)

/**
 * The perforation across a ticket, at [fraction] of the surface's height.
 *
 * The group cards have had this since they were built and it is the most distinctive thing the app
 * draws — a torn stub, which is exactly what a bill is. It was one occurrence; it now belongs to
 * anything that records money changing hands, and deliberately **never** to the dock, which is
 * navigation rather than a receipt (owner decision, 2026-09-18).
 */
fun Modifier.perforation(
    fraction: Float = 0.5f,
    inset: Dp = 18.dp,
    color: Color = CharcoalBorder,
): Modifier = this.drawWithContent {
    drawContent()
    val y = size.height * fraction
    val insetPx = inset.toPx()
    drawLine(
        color = color,
        start = Offset(insetPx, y),
        end = Offset(size.width - insetPx, y),
        strokeWidth = 1.5.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())),
    )
}
