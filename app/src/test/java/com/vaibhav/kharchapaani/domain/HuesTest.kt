package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuesTest {

    @Test
    fun `the same rules as the server - 25 degrees apart, wrapping round, amber kept for You`() {
        assertEquals(15, Hues.distance(5, 350))
        assertFalse(Hues.isFree(210, listOf(200)))
        assertFalse(Hues.isFree(176, listOf(200)))
        assertTrue(Hues.isFree(225, listOf(200)))
        assertFalse(Hues.isFree(43, emptyList()))
        assertFalse(Hues.isFree(28, emptyList()))
        assertTrue(Hues.isFree(59, emptyList()))
    }

    @Test
    fun `the nearest free hue is found either side, and there's none when every arc is taken`() {
        assertEquals(225, Hues.nearestFree(205, listOf(200)))
        assertEquals(59, Hues.nearestFree(45, emptyList()))
        val full = (0 until 360 step 24).toList()
        assertNull(Hues.nearestFree(100, full))
    }

    @Test
    fun `a first pick keeps its distance from everyone`() {
        val taken = listOf(100, 200, 300)
        val pick = Hues.firstPick("profile-x", taken)
        assertNotNull(pick)
        assertTrue(Hues.isFree(pick!!, taken))
    }

    @Test
    fun `every hue draws as a real colour, and reading it back lands close to where it started`() {
        for (hue in (0 until 360 step 15).filter { it !in Hues.RESERVED }) {
            val hex = Hues.hex(hue)
            assertTrue(hex, Regex("^#[0-9A-F]{6}$").matches(hex))
            assertTrue("$hue -> $hex -> ${Hues.hueOf(hex)}", Hues.distance(Hues.hueOf(hex)!!, hue) <= 3)
        }
    }

    @Test
    fun `the friend field holds well over 50 friends, each clear of the others`() {
        // Owner, 2026-09-27: the one-row bar ran out after about ten friends.
        val placed = mutableListOf<Hues.Slot>()
        repeat(60) { i -> placed += Hues.firstFreeSlot("friend-$i", placed) ?: error("no room for friend ${i + 1}") }
        for (i in placed.indices) for (j in placed.indices) if (i != j) assertFalse("${placed[i]} vs ${placed[j]}", Hues.clashes(placed[i], placed[j]))
        assertTrue(Hues.roomFor(placed) >= 10)
    }

    @Test
    fun `a stored colour finds its way back to its slot`() {
        for (slot in listOf(Hues.Slot(120, 0), Hues.Slot(200, 1), Hues.Slot(300, 2))) {
            val back = Hues.slotOf(slot.hex)!!
            assertEquals(slot.shade, back.shade)
            assertTrue(Hues.distance(slot.hue, back.hue) <= 3)
        }
    }

    @Test
    fun `the old palette's colours have hues where expected`() {
        assertEquals(243, Hues.hueOf("#70C1FF"))
        assertEquals(43, Hues.hueOf("#FF7A3D"))
        assertNull(Hues.hueOf("#CCCCCC"))
    }
}
