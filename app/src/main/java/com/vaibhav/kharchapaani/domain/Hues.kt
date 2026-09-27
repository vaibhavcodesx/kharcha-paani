package com.vaibhav.kharchapaani.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One colour per person, chosen as a hue (design item 11). Lightness and chroma are fixed, in the band
 * the old eight-colour palette sat in, so every hue reads on the dark background with the same
 * contrast and only the hue itself is a choice.
 *
 * The rules mirror the server's (migration profile_hue): no two people who share a group within
 * [MIN_GAP] degrees of each other, and nobody in the band around the brand amber, the app's own accent.
 */
object Hues {

    const val LIGHTNESS = 0.80
    const val CHROMA = 0.13

    /** Closer than this and two colours look alike on a donut or a member chip. */
    const val MIN_GAP = 25

    /** The brand amber (#FF7A3D) is at 43 degrees: the app's accent, so no person wears it. */
    val RESERVED = 28..58

    fun distance(a: Int, b: Int): Int {
        val d = abs(norm(a) - norm(b))
        return minOf(d, 360 - d)
    }

    fun norm(hue: Int): Int = Math.floorMod(hue, 360)

    /** Whether [hue] may be picked with [taken] (other people's hues) around. */
    fun isFree(hue: Int, taken: Collection<Int>): Boolean =
        norm(hue) !in RESERVED && taken.none { distance(it, hue) < MIN_GAP }

    /** The free hue nearest to [wanted], or null when every hue is too close to someone's. */
    fun nearestFree(wanted: Int, taken: Collection<Int>): Int? {
        for (step in 0..180) {
            val up = norm(wanted + step)
            if (isFree(up, taken)) return up
            val down = norm(wanted - step)
            if (isFree(down, taken)) return down
        }
        return null
    }

    /**
     * A first colour for someone who hasn't chosen: as far as possible from everyone else's, starting
     * from where [key] points so two people with nobody around don't both get the same one.
     */
    fun firstPick(key: String, taken: Collection<Int>): Int? {
        val start = Math.floorMod(key.hashCode(), 360)
        if (taken.isEmpty()) return nearestFree(start, taken)
        return (0 until 360).map { norm(start + it) }
            .filter { isFree(it, taken) }
            .maxByOrNull { hue -> taken.minOf { distance(it, hue) } }
    }

    /** The colour for [hue], as #RRGGBB. Chroma is lowered, never the hue, where sRGB can't show it. */
    fun hex(hue: Int): String = hex(hue, LIGHTNESS)

    /** The colour for [hue] at [lightness], as #RRGGBB, lowering chroma where sRGB can't show it. */
    fun hex(hue: Int, lightness: Double): String {
        var chroma = CHROMA
        while (chroma > 0.0) {
            srgb(lightness, chroma, norm(hue).toDouble())?.let { (r, g, b) ->
                return "#%02X%02X%02X".format((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
            }
            chroma -= 0.005
        }
        return "#CCCCCC"
    }

    // ---- Friends on this phone (not on the app): a bigger field, hue across and shade down ----
    //
    // One row of 25-degree slots holds about 13 people, which ran out after ten friends (owner,
    // 2026-09-27: "capable of handling at least 50 local friends"). Friends who aren't on the app
    // only ever meet on this phone, so they get three shades of every hue and a tighter gap within a
    // shade: about 90 distinct colours, all still readable on the dark background. A groupmate's own
    // colour keeps the stricter one-row rule, because the server checks that one.

    /** Lightness of each shade row, lightest first. The middle one is the groupmate row. */
    val SHADES = listOf(0.88, LIGHTNESS, 0.70)

    /** Within one shade, colours closer than this look alike. */
    const val FRIEND_GAP = 12

    /** Across neighbouring shades the lightness already tells them apart, bar nearly equal hues. */
    const val FRIEND_GAP_NEXT_SHADE = 5

    data class Slot(val hue: Int, val shade: Int) {
        val hex: String get() = hex(hue, SHADES[shade])
    }

    /** Where a stored colour sits in the field: its hue and the nearest shade row. */
    fun slotOf(hex: String): Slot? {
        val hue = hueOf(hex) ?: return null
        val l = lightnessOf(hex) ?: return null
        return Slot(hue, SHADES.indices.minBy { abs(SHADES[it] - l) })
    }

    /**
     * Whether two friends' colours are too alike. Two degrees of slack under the spacing new friends
     * get: a colour is stored as hex, and reading its hue back can land a degree or two away, which
     * must not make two neighbours on the grid suddenly count as clashing.
     */
    fun clashes(a: Slot, b: Slot): Boolean = when (abs(a.shade - b.shade)) {
        0 -> distance(a.hue, b.hue) < FRIEND_GAP - 2
        1 -> distance(a.hue, b.hue) < FRIEND_GAP_NEXT_SHADE - 1
        else -> false
    }

    fun isFreeSlot(slot: Slot, taken: Collection<Slot>): Boolean =
        norm(slot.hue) !in RESERVED && taken.none { clashes(it, slot) }

    /** The free slot nearest [wanted], its own row first; null only when all ~90 are taken. */
    fun nearestFreeSlot(wanted: Slot, taken: Collection<Slot>): Slot? {
        val rows = SHADES.indices.sortedBy { abs(it - wanted.shade) }
        for (row in rows) {
            for (step in 0..180) {
                for (hue in listOf(norm(wanted.hue + step), norm(wanted.hue - step))) {
                    val slot = Slot(hue, row)
                    if (isFreeSlot(slot, taken)) return slot
                }
            }
        }
        return null
    }

    /**
     * The grid new friends are placed on: every [FRIEND_GAP] degrees in each shade, the middle shade
     * offset by half a step so neighbouring shades never sit on the same hue. It packs about 80 into
     * the field; placing each friend as far as possible from the rest instead left scattered gaps
     * and ran out at 55.
     */
    private val GRID: List<Slot> = SHADES.indices.sortedBy { abs(it - 1) }.flatMap { row ->
        val offset = if (row == 1) FRIEND_GAP / 2 else 0
        (0 until 360 step FRIEND_GAP).map { Slot(norm(it + offset), row) }
    }

    /**
     * A new friend's first colour: a free grid spot, the one furthest from everyone (middle shade
     * first), starting where [key] points so it depends on them. When existing colours block the
     * whole grid, any free spot at all.
     */
    fun firstFreeSlot(key: String, taken: Collection<Slot>): Slot? {
        fun spread(slot: Slot) = if (taken.isEmpty()) 0 else taken.minOf { distance(it.hue, slot.hue) + 20 * abs(it.shade - slot.shade) }
        val start = Math.floorMod(key.hashCode(), 360)
        GRID.filter { isFreeSlot(it, taken) }
            .maxWithOrNull(compareBy<Slot> { spread(it) }.thenBy { -abs(it.shade - 1) }.thenBy { -distance(it.hue, start) })
            ?.let { return it }
        return SHADES.indices.flatMap { row -> (0 until 360).map { Slot(norm(start + it), row) } }.firstOrNull { isFreeSlot(it, taken) }
    }

    /** How many more friends fit with [taken] already placed: filled greedily, as new friends are. */
    fun roomFor(taken: Collection<Slot>): Int {
        val placed = taken.toMutableList()
        var room = 0
        while (room < 200) {
            placed += firstFreeSlot("room-$room", placed) ?: break
            room++
        }
        return room
    }

    private fun lightnessOf(hex: String): Double? {
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) return null
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        val r = lin(hex.substring(1, 3).toInt(16)); val g = lin(hex.substring(3, 5).toInt(16)); val b = lin(hex.substring(5, 7).toInt(16))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
    }

    /** A colour's OKLCH hue, for fitting colours stored as hex (friends not on the app) into the rules. */
    fun hueOf(hex: String): Int? {
        if (!Regex("^#[0-9A-Fa-f]{6}$").matches(hex)) return null
        fun lin(c: Int): Double { val v = c / 255.0; return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4) }
        val r = lin(hex.substring(1, 3).toInt(16)); val g = lin(hex.substring(3, 5).toInt(16)); val b = lin(hex.substring(5, 7).toInt(16))
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        if (sqrt(a * a + bb * bb) < 0.02) return null // grey: no meaningful hue
        return norm(Math.toDegrees(atan2(bb, a)).roundToInt())
    }

    private fun srgb(lightness: Double, chroma: Double, hueDegrees: Double): Triple<Double, Double, Double>? {
        val h = Math.toRadians(hueDegrees)
        val a = chroma * cos(h)
        val b = chroma * sin(h)
        val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
        val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
        val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
        val lin = listOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
        if (lin.any { it < -1e-4 || it > 1 + 1e-4 }) return null
        val (r, g, bl) = lin.map { c -> val v = c.coerceIn(0.0, 1.0); if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1 / 2.4) - 0.055 }
        return Triple(r, g, bl)
    }
}
