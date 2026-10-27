package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Test

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
}
