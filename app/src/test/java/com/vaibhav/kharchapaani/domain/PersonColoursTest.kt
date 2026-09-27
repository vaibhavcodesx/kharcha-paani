package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonColoursTest {

    private var nextCreated = 1_000L

    private fun person(id: Long, key: String, colour: String = "", createdAt: Long = nextCreated++) =
        PersonColour(id = id, key = key, colorHex = colour, createdAt = createdAt)

    // --- The arithmetic bug

    @Test
    fun `a negative hash does not land on the same slot as its positive twin`() {
        // The bug: `abs(hashCode() % 8)` reaches index 3 from both +3 and -3, so two people whose
        // hashes differ only in sign were given the same colour. These are the four profile IDs on
        // the reporting account, where it collided twice out of six pairs.
        val ids = listOf(
            "1f772252-80fa-4358-a8e5-3c20ba93e6dd", // Mayank,  hash -399321934, rem -6
            "88efe830-1be5-4166-8e8c-3ac0f5c36e96", // Shubham, hash  332113659, rem +3
            "1fb89c5e-a778-4611-a633-da774ac50b0d", // Lal,     hash -862288227, rem -3
            "cddb7931-0cbc-4010-9c2c-d1f9e58f88d1", // Vaibhav, hash 1569362038, rem +6
        )

        val buggy = ids.map { PersonColours.palette[Math.abs(it.hashCode() % PersonColours.palette.size)] }
        val fixed = ids.map { PersonColours.stable(it) }

        assertEquals("the old formula really did collide on this data", 2, buggy.distinct().size)
        assertEquals("floorMod separates them", 4, fixed.distinct().size)
    }

    @Test
    fun `a stable key always gives the same colour`() {
        assertEquals(PersonColours.stable("rohan"), PersonColours.stable("rohan"))
        assertTrue(PersonColours.stable("rohan") in PersonColours.palette)
    }

    // --- Respacing

    @Test
    fun `two people holding the same colour are separated`() {
        val changes = PersonColours.respace(
            listOf(person(1, "lal", "#5BD6B0"), person(2, "shubham", "#5BD6B0")),
        )

        assertFalse("the one who had it first keeps it", changes.containsKey(1))
        assertNotEquals("#5BD6B0", changes[2])
        // Moved somewhere in the friend field, clear of the first one (no longer only the palette).
        assertFalse(Hues.clashes(Hues.slotOf("#5BD6B0")!!, Hues.slotOf(changes.getValue(2))!!))
    }

    @Test
    fun `whoever has had it longest keeps it, whatever order they arrive in`() {
        val older = person(9, "lal", "#5BD6B0", createdAt = 100)
        val newer = person(2, "shubham", "#5BD6B0", createdAt = 500)

        val forwards = PersonColours.respace(listOf(older, newer))
        val backwards = PersonColours.respace(listOf(newer, older))

        assertEquals(forwards, backwards)
        assertFalse(forwards.containsKey(9))
    }

    @Test
    fun `a phone with no collisions is not touched at all`() {
        // A repair pass that rewrites rows it doesn't need to is a repair pass that recolours
        // people for no reason.
        val settled = listOf(person(1, "a", "#5BD6B0"), person(2, "b", "#70C1FF"), person(3, "c", "#F2D06B"))

        assertTrue(PersonColours.respace(settled).isEmpty())
    }

    @Test
    fun `nobody is given the colour that means you`() {
        val changes = PersonColours.respace(listOf(person(1, "a", PersonColours.MINE), person(2, "b", PersonColours.MINE)))

        assertNotEquals(PersonColours.MINE, changes[1])
        assertNotEquals(PersonColours.MINE, changes[2])
    }

    @Test
    fun `a missing or broken colour is replaced rather than left to fall back to amber`() {
        // parseColor's fallback is the brand amber, which is also "You" — so an unreadable colour
        // silently made a groupmate look like the account holder.
        val changes = PersonColours.respace(listOf(person(1, "a", ""), person(2, "b", "not-a-colour")))

        assertTrue(changes.getValue(1) in PersonColours.palette)
        assertTrue(changes.getValue(2) in PersonColours.palette)
        assertNotEquals(changes[1], changes[2])
    }

    @Test
    fun `a whole group ends up with as many colours as it has people`() {
        val everyone = (1L..8L).map { person(it, "person-$it", "#5BD6B0") }

        val after = everyone.associate { it.id to (PersonColours.respace(everyone)[it.id] ?: it.colorHex) }

        assertEquals(8, after.values.distinct().size)
    }

    @Test
    fun `more people than colours still settles rather than looping`() {
        val crowd = (1L..20L).map { person(it, "person-$it") }

        val changes = PersonColours.respace(crowd)

        assertEquals("everyone is given something", 20, changes.size)
        // Twenty fit in the friend field with nobody sharing (the eight-colour palette forced repeats).
        val slots = changes.values.map { Hues.slotOf(it)!! }
        for (i in slots.indices) for (j in slots.indices) if (i != j) assertFalse(Hues.clashes(slots[i], slots[j]))
    }

    @Test
    fun `sixty friends settle into the field and stay put`() {
        val crowd = (1L..60L).map { person(it, "friend-$it", createdAt = it) }
        val first = PersonColours.respace(crowd)
        val settled = crowd.map { it.copy(colorHex = first[it.id] ?: it.colorHex) }

        assertTrue("stored as hex and read back, nobody moves again", PersonColours.respace(settled).isEmpty())
    }

    @Test
    fun `respacing twice changes nothing the second time`() {
        val people = listOf(person(1, "a", "#5BD6B0"), person(2, "b", "#5BD6B0"), person(3, "c", ""))
        val first = PersonColours.respace(people)
        val settled = people.map { it.copy(colorHex = first[it.id] ?: it.colorHex) }

        assertTrue("the repair is idempotent", PersonColours.respace(settled).isEmpty())
    }

    @Test
    fun `every colour in the palette is one the app can draw`() {
        assertTrue(PersonColours.palette.all { PersonColours.isValid(it) })
        assertTrue(PersonColours.isValid(PersonColours.MINE))
        assertFalse(PersonColours.isValid(null))
        assertFalse(PersonColours.isValid("#FFF"))
        assertFalse(PersonColours.isValid("teal"))
    }
}
