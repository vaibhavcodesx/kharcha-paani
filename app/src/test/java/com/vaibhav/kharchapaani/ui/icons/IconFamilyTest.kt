package com.vaibhav.kharchapaani.ui.icons

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.declaredMemberProperties

/**
 * Round 2 §10.9: "one pass confirming every icon comes from one consistent family and stroke
 * weight". Done as a test rather than as a pass, because more icons keep arriving — three more in
 * this round alone — and a pass only holds until the next one.
 *
 * The set is drawn through a small DSL that makes the house style the default, so what this really
 * checks is that nobody has gone around it: a raw `addPath` with its own stroke width, a glyph
 * drawn on a different grid, or an icon that is all fill and no line and so reads as a sticker
 * beside the rest.
 */
class IconFamilyTest {

    /**
     * Two weights, and who gets which.
     *
     * Almost the whole set is [regularStroke]. Exactly three glyphs are heavier, and they are the
     * three that are not drawn at icon size: the plus on the gradient add button, which is drawn
     * far larger than anything else in the app and looked thin at 1.75 against a fill that bright;
     * and the tick and the arrow, which go inside 15-18dp checkboxes and chips, where a 1.75 stroke
     * on a 24-unit grid comes out under a pixel.
     *
     * Named rather than inferred from a rule. The obvious rule — "a bare glyph without a tone fill
     * is heavy" — is not the one the set follows: `ChevronDown` and `Search` are bare and regular.
     * Size is what decides, and an ImageVector does not know what size it will be drawn at, so the
     * honest thing is a list with the reason beside it.
     */
    private val regularStroke = 1.75f
    private val heavyStroke = 2.25f

    private val heavyIcons = mapOf(
        "Add" to "drawn oversized on the add button",
        "Check" to "drawn at 15-16dp inside checkboxes",
        "Send" to "drawn at 18dp inside chips and buttons",
    )

    /**
     * The one icon with no outline at all: a three-dot overflow menu is fills by nature, and
     * outlining the dots would draw three rings, which is not what a three-dot menu is.
     */
    private val allFillByDesign = setOf("More")

    private val icons: List<Pair<String, ImageVector>> = KpIcons::class.declaredMemberProperties
        .filter { it.returnType.classifier == ImageVector::class }
        .map { it.name to (it.getter.call(KpIcons) as ImageVector) }
        .sortedBy { it.first }

    @Test
    fun `there are icons to check`() {
        // Guards the reflection itself: if the property scan ever returns nothing, every other
        // test here would pass vacuously.
        assertTrue("found only ${icons.size} icons", icons.size >= 30)
    }

    @Test
    fun `every icon is drawn on the same grid`() {
        for ((name, icon) in icons) {
            assertEquals("$name viewport width", 24f, icon.viewportWidth, 0.001f)
            assertEquals("$name viewport height", 24f, icon.viewportHeight, 0.001f)
            assertEquals("$name default width", 24f, icon.defaultWidth.value, 0.001f)
            assertEquals("$name default height", 24f, icon.defaultHeight.value, 0.001f)
        }
    }

    @Test
    fun `every icon uses one of the two weights, and the right one`() {
        for ((name, icon) in icons.filterNot { it.first in allFillByDesign }) {
            val strokes = icon.root.paths().map { it.strokeLineWidth }.filter { it > 0f }.distinct()
            val expected = if (name in heavyIcons) heavyStroke else regularStroke

            assertTrue("$name has no outline at all", strokes.isNotEmpty())
            assertEquals("$name mixes stroke weights within one icon: $strokes", 1, strokes.size)
            assertEquals(
                "$name should be $expected" + (heavyIcons[name]?.let { " ($it)" } ?: ""),
                expected,
                strokes.single(),
                0.001f,
            )
        }
    }

    @Test
    fun `the only all-fill icon is the one that should be`() {
        for ((name, icon) in icons) {
            val bare = icon.root.paths().none { it.strokeLineWidth > 0f }
            assertEquals("$name: all-fill should be ${name in allFillByDesign}", name in allFillByDesign, bare)
        }
    }

    @Test
    fun `stroked paths end and join the same way everywhere`() {
        val (_, reference) = icons.first()
        val cap = reference.root.paths().first { it.strokeLineWidth > 0f }.strokeLineCap
        val join = reference.root.paths().first { it.strokeLineWidth > 0f }.strokeLineJoin

        for ((name, icon) in icons) {
            for (path in icon.root.paths().filter { it.strokeLineWidth > 0f }) {
                assertEquals("$name stroke cap", cap, path.strokeLineCap)
                assertEquals("$name stroke join", join, path.strokeLineJoin)
            }
        }
    }

    @Test
    fun `the payment icons added this round belong to the same family as the rest`() {
        // Named rather than left to the sweep above, because these are the newest and the ones a
        // regression would most likely come from.
        for (icon in listOf(KpIcons.Cash, KpIcons.Upi, KpIcons.Bank)) {
            assertTrue(icons.any { it.second === icon })
        }
    }

    private fun VectorGroup.paths(): List<VectorPath> = buildList {
        fun walk(node: VectorNode) {
            when (node) {
                is VectorPath -> add(node)
                is VectorGroup -> node.forEach(::walk)
            }
        }
        walk(this@paths)
    }
}
