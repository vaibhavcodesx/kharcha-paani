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
 * [MIN_GAP] degrees of each other, and nobody in the band around the brand amber, which is "You".
 */
object Hues {

    const val LIGHTNESS = 0.80
    const val CHROMA = 0.13

    /** Closer than this and two colours look alike on a donut or a member chip. */
    const val MIN_GAP = 25

    /** The brand amber (#FF7A3D) is at 43 degrees; this band is kept for "You". */
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
    fun hex(hue: Int): String {
        var chroma = CHROMA
        while (chroma > 0.0) {
            srgb(LIGHTNESS, chroma, norm(hue).toDouble())?.let { (r, g, b) ->
                return "#%02X%02X%02X".format((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
            }
            chroma -= 0.005
        }
        return "#CCCCCC"
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
