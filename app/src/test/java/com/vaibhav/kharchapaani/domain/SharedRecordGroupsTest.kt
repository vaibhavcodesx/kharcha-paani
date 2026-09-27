package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRecordGroupsTest {

    private val bob = PersonEntity(id = 2, name = "Bob", avatarColor = "#fff", profileId = "b")
    private val carol = PersonEntity(id = 3, name = "Carol", avatarColor = "#fff", profileId = "c")
    private val rahul = PersonEntity(id = 4, name = "Rahul", avatarColor = "#fff")
    private val people = listOf(bob, carol, rahul)

    private fun group(id: Long, members: List<PersonEntity>, archived: Boolean = false, shared: Boolean = true) = GroupWithDetails(
        group = GroupEntity(
            id = id,
            name = "G$id",
            createdAt = id,
            syncState = if (shared) SyncState.SYNCED else SyncState.LOCAL_ONLY,
            archivedAt = if (archived) 1L else null,
        ),
        members = members,
        totalExpenses = 0.0, myPaidAmount = 0.0, myShare = 0.0, netBalanceForMe = 0.0, transactionsCount = 0,
    )

    @Test
    fun `a groupmate in one active shared group gets that group preselected`() {
        val options = SharedRecordGroups.optionsFor(listOf(-1L, bob.id), people, listOf(group(1, listOf(bob))))

        assertTrue(options.involvesGroupmates)
        assertTrue(options.canSync)
        assertEquals(1L, options.onlyGroup?.id)
    }

    @Test
    fun `a split with a groupmate and a friend not on the app goes in the group, the friend as my guest`() {
        // The owner's case: one expense with both kinds of friend, and the group chosen for the app part.
        val options = SharedRecordGroups.optionsFor(listOf(-1L, bob.id, rahul.id), people, listOf(group(1, listOf(bob))), splitPayerId = -1L)

        assertTrue(options.canSync)
        assertEquals(1L, options.onlyGroup?.id)
        assertEquals(listOf(rahul), options.guests)
        assertTrue(options.withoutAccount.isEmpty())
    }

    @Test
    fun `a split paid by a friend not on the app stays on this phone`() {
        val options = SharedRecordGroups.optionsFor(listOf(-1L, bob.id, rahul.id), people, listOf(group(1, listOf(bob))), splitPayerId = rahul.id)

        assertFalse(options.canSync)
        assertEquals(listOf(rahul), options.withoutAccount)
    }

    @Test
    fun `a settlement or IOU with a friend not on the app still can't go in a group`() {
        val options = SharedRecordGroups.optionsFor(listOf(rahul.id), people, listOf(group(1, listOf(bob))))

        assertFalse(options.canSync)
        assertTrue(options.guests.isEmpty())
    }

    @Test
    fun `only active shared groups containing everyone are offered, newest first`() {
        val groups = listOf(
            group(1, listOf(bob, carol)),
            group(2, listOf(bob)),
            group(3, listOf(bob, carol), archived = true),
            group(4, listOf(bob, carol), shared = false),
            group(5, listOf(bob, carol)),
        )

        val options = SharedRecordGroups.optionsFor(listOf(-1L, bob.id, carol.id), people, groups)

        assertEquals(listOf(5L, 1L), options.groups.map { it.id })
        assertNull("two choices: nothing preselected", options.onlyGroup)
    }

    @Test
    fun `a friend without an account means it can't sync even when a group exists`() {
        val options = SharedRecordGroups.optionsFor(listOf(-1L, bob.id, rahul.id), people, listOf(group(1, listOf(bob))))

        assertEquals(listOf("Rahul"), options.withoutAccount.map { it.name })
        assertFalse(options.canSync)
        assertTrue(options.involvesGroupmates)
    }

    @Test
    fun `local friends only, or nobody else, never ask for a group`() {
        assertFalse(SharedRecordGroups.optionsFor(listOf(-1L, rahul.id), people, listOf(group(1, listOf(bob)))).involvesGroupmates)
        val justMe = SharedRecordGroups.optionsFor(listOf(-1L), people, listOf(group(1, listOf(bob))))
        assertFalse(justMe.involvesGroupmates)
        assertTrue(justMe.groups.isEmpty())
    }
}
