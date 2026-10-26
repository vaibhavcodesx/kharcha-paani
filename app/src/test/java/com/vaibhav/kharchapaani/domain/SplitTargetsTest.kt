package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Who a quick-add line says it was shared with: friends by name, or a group by its name. */
class SplitTargetsTest {

    private val rohan = PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF")
    private val ankit = PersonEntity(id = 2, name = "Ankit Shah", avatarColor = "#70C1FF")
    private val rahulK = PersonEntity(id = 3, name = "Rahul Kumar", avatarColor = "#70C1FF")
    private val rahulM = PersonEntity(id = 4, name = "Rahul Mehta", avatarColor = "#70C1FF")
    private val people = listOf(rohan, ankit, rahulK, rahulM)

    private val goa = GroupEntity(id = 10, name = "Goa trip")
    private val goaTwo = GroupEntity(id = 11, name = "Goa trip 2")
    private val flat = GroupEntity(id = 12, name = "Flat", archivedAt = 1L)
    private val groups = listOf(goa, goaTwo, flat)

    private fun resolve(text: String, names: List<String> = emptyList()) =
        SplitTargets.resolve(text, names, people, groups)

    @Test
    fun `a name as written finds that friend`() {
        val target = resolve("dinner 1800 with rohan", names = listOf("rohan"))

        assertEquals(setOf(1L), target.personIds)
        assertEquals(emptyList<String>(), target.unknownNames)
    }

    @Test
    fun `bhai and ji don't stop the match`() {
        val target = resolve("chai 40 rohan bhai", names = listOf("Rohan bhai", "Ankit ji"))

        assertEquals(setOf(1L, 2L), target.personIds)
    }

    @Test
    fun `a first name finds the friend saved with a full name`() {
        assertEquals(setOf(2L), resolve("auto 120 with ankit", names = listOf("Ankit")).personIds)
    }

    @Test
    fun `a first name two friends share is asked about, not guessed`() {
        val target = resolve("lunch 600 with rahul", names = listOf("Rahul"))

        assertEquals(emptySet<Long>(), target.personIds)
        assertEquals(listOf("Rahul"), target.ambiguousNames)
    }

    @Test
    fun `a full name settles which Rahul`() {
        assertEquals(setOf(4L), resolve("lunch 600 with rahul mehta", names = listOf("Rahul Mehta")).personIds)
    }

    @Test
    fun `a name that isn't a friend is reported as written`() {
        val target = resolve("movie 500 with priya", names = listOf("Priya"))

        assertEquals(emptySet<Long>(), target.personIds)
        assertEquals(listOf("Priya"), target.unknownNames)
    }

    @Test
    fun `me and you aren't friends to find`() {
        val target = resolve("pizza 900 me and rohan", names = listOf("me", "You", "Rohan"))

        assertEquals(setOf(1L), target.personIds)
        assertEquals(emptyList<String>(), target.unknownNames)
    }

    @Test
    fun `a group named in the line is the group`() {
        assertEquals(10L, resolve("petrol 500 goa trip").groupId)
    }

    @Test
    fun `the longest group name wins`() {
        assertEquals(11L, resolve("hotel 4000 Goa Trip 2").groupId)
    }

    @Test
    fun `a group name has to be whole words`() {
        // "Flat" is archived anyway; "Goa trip" must not match inside "goa tripod".
        assertNull(resolve("goa tripod 900").groupId)
    }

    @Test
    fun `an archived group isn't picked`() {
        assertNull(resolve("rent 9000 flat").groupId)
    }

    @Test
    fun `the group's own name isn't also a missing friend`() {
        // The AI sometimes returns the group in splitWith.
        val target = resolve("petrol 500 goa trip", names = listOf("Goa trip"))

        assertEquals(10L, target.groupId)
        assertEquals(emptyList<String>(), target.unknownNames)
    }

    @Test
    fun `the sheet says which names it couldn't place`() {
        val target = resolve("dinner 2000 with priya sam and rahul", names = listOf("Priya", "Sam", "Rahul"))

        assertEquals(
            "More than one Rahul in your friends, so pick the right one in the split. " +
                "Couldn't find Priya and Sam in your friends, so they're not in the split.",
            SplitTargets.notice(target),
        )
    }

    @Test
    fun `a friend outside the group is said, and a clean match says nothing`() {
        val found = resolve("petrol 500 goa trip with rohan", names = listOf("Rohan"))

        assertEquals("Rohan isn't in Goa trip, so they're not in its split.", SplitTargets.notice(found, listOf("Rohan"), "Goa trip"))
        assertNull(SplitTargets.notice(found))
    }

    @Test
    fun `no names and no group is your own expense`() {
        val target = resolve("chai 40")

        assertNull(target.groupId)
        assertEquals(emptySet<Long>(), target.personIds)
    }
}
