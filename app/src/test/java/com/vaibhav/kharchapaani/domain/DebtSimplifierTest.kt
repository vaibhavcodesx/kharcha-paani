package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DebtSimplifierTest {

    private val bob = PersonEntity(id = 2, name = "Bob", avatarColor = "#fff")
    private val carol = PersonEntity(id = 3, name = "Carol", avatarColor = "#fff")

    @Test fun `display labels can be supplied for self and a missing friend`() {
        val debt = DebtSimplifier.simplify(emptyList(), mapOf(2L to 125.50), emptyList(), myName = "Self label", friendName = "Friend label").single()
        assertEquals("Friend label", debt.fromPersonName)
        assertEquals("Self label", debt.toPersonName)
        assertEquals(125.50, debt.amount, 0.0)
    }

    @Test
    fun `amounts round to the nearest paisa instead of losing one`() {
        // 33.33 * 100 is 3332.9999… in floating point, which truncation turned into 33.32.
        val debts = DebtSimplifier.simplifyPositions(mapOf(-1L to 33.33, 2L to -33.33)) { "Bob" }

        assertEquals(33.33, debts.single().amount, 0.0)
    }

    @Test
    fun `a balance relative to me rounds the same way, in both directions`() {
        // 0.29 * 100 is 28.999…, which truncated to 0.28.
        val owedToMe = DebtSimplifier.simplify(listOf(bob), mapOf(2L to 0.29), groups = emptyList()).single()
        assertEquals(listOf(2L, -1L), listOf(owedToMe.fromPersonId, owedToMe.toPersonId))
        assertEquals(0.29, owedToMe.amount, 0.0)

        val iOwe = DebtSimplifier.simplify(listOf(bob), mapOf(2L to -0.29), groups = emptyList()).single()
        assertEquals(listOf(-1L, 2L), listOf(iOwe.fromPersonId, iOwe.toPersonId))
        assertEquals(0.29, iOwe.amount, 0.0)
    }

    // --- Who pays whom across friends: only people who share a group pay each other.

    private val hist = PersonEntity(id = 4, name = "Hist", avatarColor = "#fff")
    private val shubham = PersonEntity(id = 5, name = "Shubham", avatarColor = "#fff")

    private fun described(debts: List<com.vaibhav.kharchapaani.data.model.SimplifiedDebt>) =
        debts.map { "${it.fromPersonName} -> ${it.toPersonName} ${it.amount}" }

    @Test
    fun `two friends who share no group never pay each other`() {
        // The 1.3 audit's case: Hist (a friend not on the app) owes you 75, you owe Shubham (from a
        // shared trip Hist was never on) 721. "Hist pays Shubham" was the suggestion.
        val debts = DebtSimplifier.simplify(listOf(hist, shubham), mapOf(4L to 75.0, 5L to -721.0), groups = listOf(setOf(-1L, 5L)))

        assertEquals(listOf("Hist -> You 75.0", "You -> Shubham 721.0"), described(debts))
    }

    @Test
    fun `friends in a group together can pay each other directly`() {
        val debts = DebtSimplifier.simplify(listOf(hist, shubham), mapOf(4L to 75.0, 5L to -721.0), groups = listOf(setOf(-1L, 4L, 5L)))

        assertEquals(listOf("Hist -> Shubham 75.0", "You -> Shubham 646.0"), described(debts))
    }

    @Test
    fun `a shared group of theirs that you're not in still counts`() {
        val debts = DebtSimplifier.simplify(listOf(hist, shubham), mapOf(4L to 75.0, 5L to -75.0), groups = listOf(setOf(4L, 5L)))

        assertEquals(listOf("Hist -> Shubham 75.0"), described(debts))
    }

    @Test
    fun `only the pairs that share a group are matched, and every balance still clears`() {
        // Bob and Carol share a flat; Hist shares nothing with anyone.
        val people = listOf(bob, carol, hist)
        val balances = mapOf(2L to 300.0, 4L to 50.0, 3L to -200.0)
        val debts = DebtSimplifier.simplify(people, balances, groups = listOf(setOf(-1L, 2L, 3L)))

        assertEquals(listOf("Bob -> Carol 200.0", "Bob -> You 100.0", "Hist -> You 50.0"), described(debts))
        // What each person ends up paying or receiving matches their balance with you.
        for ((id, balance) in balances) {
            val net = debts.filter { it.toPersonId == id }.sumOf { it.amount } - debts.filter { it.fromPersonId == id }.sumOf { it.amount }
            assertEquals(-balance, net, 0.001)
        }
    }

    @Test
    fun `a one-paisa debt is still reported`() {
        val debts = DebtSimplifier.simplifyPositions(mapOf(-1L to 0.01, 2L to -0.01)) { "Bob" }

        assertEquals(0.01, debts.single().amount, 0.0)
    }

    @Test
    fun `payments across three people add up to the positions exactly`() {
        val positions = mapOf(-1L to 66.67, 2L to -33.34, 3L to -33.33)

        val debts = DebtSimplifier.simplifyPositions(positions) { if (it == 2L) bob.name else carol.name }

        assertEquals(setOf(2L to 33.34, 3L to 33.33), debts.map { it.fromPersonId to it.amount }.toSet())
        assertEquals(66.67, roundPaisa(debts.sumOf { it.amount }), 0.0)
    }

    @Test
    fun `roundPaisa rounds half up and handles negatives symmetrically`() {
        assertEquals(0.29, roundPaisa(0.29), 0.0)
        assertEquals(-0.29, roundPaisa(-0.29), 0.0)
        assertEquals(10.01, roundPaisa(10.005), 0.0)
        assertEquals(-10.01, roundPaisa(-10.005), 0.0)
        assertEquals(33.33, roundPaisa(100.0 / 3), 0.0)
    }

    @Test
    fun `simplifyPositions preserves every net and produces at most n minus 1 transfers across varied group sizes and balances`() {
        val seeds = listOf(1337L, 424242L, 20261103L)
        for (seed in seeds) {
            val rng = Random(seed)
            for (trial in 1..100) {
                val n = rng.nextInt(2, 21)
                val memberIds = (1L..n.toLong()).toList()

                // Generate random whole-paisa balances that sum to zero
                val rawPaisa = memberIds.dropLast(1).map { rng.nextLong(-250000L, 250000L) }.toMutableList()
                rawPaisa.add(-rawPaisa.sum())

                val positions = memberIds.zip(rawPaisa).associate { (id, paisa) -> id to (paisa / 100.0) }
                val activeCount = positions.count { abs(roundPaisa(it.value)) >= 0.01 }

                val debts = DebtSimplifier.simplifyPositions(positions) { "Person $it" }

                // INVARIANT 1: At most n - 1 transfers (specifically at most activeCount - 1)
                val maxAllowedTransfers = maxOf(0, activeCount - 1)
                assertTrue(
                    "Seed $seed trial $trial: transfers count ${debts.size} must be at most $maxAllowedTransfers for $activeCount active participants",
                    debts.size <= maxAllowedTransfers
                )

                // INVARIANT 2: Every transfer is positive whole paisa and between distinct people
                for (debt in debts) {
                    assertTrue("Transfer amount must be >= 0.01", debt.amount >= 0.01)
                    assertTrue("No self-transfer", debt.fromPersonId != debt.toPersonId)
                }

                // INVARIANT 3: Every participant's net position is preserved to the exact paisa
                val netReceived = mutableMapOf<Long, Double>()
                for (debt in debts) {
                    netReceived[debt.toPersonId] = (netReceived[debt.toPersonId] ?: 0.0) + debt.amount
                    netReceived[debt.fromPersonId] = (netReceived[debt.fromPersonId] ?: 0.0) - debt.amount
                }

                for ((personId, initialNet) in positions) {
                    val expectedNet = roundPaisa(initialNet)
                    val actualNet = roundPaisa(netReceived[personId] ?: 0.0)
                    assertEquals(
                        "Seed $seed trial $trial: net position of person $personId must be preserved",
                        expectedNet,
                        actualNet,
                        0.0
                    )
                }
            }
        }
    }

    @Test
    fun `simplify preserves every friend balance and restricts direct payments to shared groups across random scenarios`() {
        val seeds = listOf(777L, 9999L, 123456L)
        for (seed in seeds) {
            val rng = Random(seed)
            for (trial in 1..50) {
                val friendCount = rng.nextInt(2, 9)
                val friends = (2L..1L + friendCount).map { id ->
                    PersonEntity(id = id, name = "Friend $id", avatarColor = "#fff")
                }

                // Net balances with "You" (-1L)
                val netBalances = friends.associate { it.id to (rng.nextLong(-100000L, 100000L) / 100.0) }

                // Generate random groups (some friends share groups, some don't)
                val groupCount = rng.nextInt(1, 4)
                val groups = (1..groupCount).map {
                    val memberCount = rng.nextInt(2, friends.size + 1)
                    val groupMembers = friends.shuffled(rng).take(memberCount).map { it.id }.toMutableSet()
                    if (rng.nextBoolean()) groupMembers.add(-1L) // "You" may or may not be in this group
                    groupMembers.toSet()
                }

                val debts = DebtSimplifier.simplify(
                    people = friends,
                    netBalances = netBalances,
                    groups = groups,
                    myName = "You",
                    friendName = "Friend"
                )

                // INVARIANT 1: Friend-to-friend payments occur only if they share a group
                for (debt in debts) {
                    if (debt.fromPersonId != -1L && debt.toPersonId != -1L) {
                        val shareGroup = groups.any { debt.fromPersonId in it && debt.toPersonId in it }
                        assertTrue(
                            "Direct payment between friend ${debt.fromPersonId} and ${debt.toPersonId} requires a shared group",
                            shareGroup
                        )
                    }
                }

                // INVARIANT 2: Every friend's balance with "You" is accurately cleared
                for (friend in friends) {
                    val received = debts.filter { it.toPersonId == friend.id }.sumOf { it.amount }
                    val paid = debts.filter { it.fromPersonId == friend.id }.sumOf { it.amount }
                    val actualNet = roundPaisa(received - paid)
                    val expectedNet = -roundPaisa(netBalances[friend.id] ?: 0.0)
                    assertEquals(
                        "Seed $seed trial $trial: friend ${friend.id} balance must be cleared exactly",
                        expectedNet,
                        actualNet,
                        0.0
                    )
                }

                // INVARIANT 3: "You" net position clears the sum of all friend balances
                val myReceived = debts.filter { it.toPersonId == -1L }.sumOf { it.amount }
                val myPaid = debts.filter { it.fromPersonId == -1L }.sumOf { it.amount }
                val myActualNet = roundPaisa(myReceived - myPaid)
                val myExpectedNet = roundPaisa(netBalances.values.sumOf { roundPaisa(it) })
                assertEquals(
                    "Seed $seed trial $trial: My net position must match sum of friend balances",
                    myExpectedNet,
                    myActualNet,
                    0.0
                )
            }
        }
    }

    @Test
    fun `simplifyPositions with large group of 50 members scales and respects at most 49 transfers`() {
        val n = 50
        val memberIds = (1L..n.toLong()).toList()
        val rng = Random(12345)
        val rawPaisa = memberIds.dropLast(1).map { rng.nextLong(-100000L, 100000L) }.toMutableList()
        rawPaisa.add(-rawPaisa.sum())

        val positions = memberIds.zip(rawPaisa).associate { (id, paisa) -> id to (paisa / 100.0) }
        val activeCount = positions.count { abs(roundPaisa(it.value)) >= 0.01 }

        val debts = DebtSimplifier.simplifyPositions(positions) { "Member $it" }

        assertTrue(debts.size <= activeCount - 1)
        assertTrue(debts.size < n)

        val netReceived = mutableMapOf<Long, Double>()
        for (debt in debts) {
            netReceived[debt.toPersonId] = (netReceived[debt.toPersonId] ?: 0.0) + debt.amount
            netReceived[debt.fromPersonId] = (netReceived[debt.fromPersonId] ?: 0.0) - debt.amount
        }

        for ((id, initialNet) in positions) {
            assertEquals(roundPaisa(initialNet), roundPaisa(netReceived[id] ?: 0.0), 0.0)
        }
    }

    @Test
    fun `simplifyPositions with zero balances or sub-paisa amounts generates no spurious transfers`() {
        val positions = mapOf(
            1L to 50.0,
            2L to -50.0,
            3L to 0.0,
            4L to 0.002, // Below half paisa, rounds to 0.00
            5L to -0.002,
        )

        val debts = DebtSimplifier.simplifyPositions(positions) { "Person $it" }

        assertEquals(1, debts.size)
        assertEquals(2L, debts.single().fromPersonId)
        assertEquals(1L, debts.single().toPersonId)
        assertEquals(50.0, debts.single().amount, 0.0)
    }
}
