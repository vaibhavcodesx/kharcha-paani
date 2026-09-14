package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DebtSimplifierTest {

    private val bob = PersonEntity(id = 2, name = "Bob", avatarColor = "#fff")
    private val carol = PersonEntity(id = 3, name = "Carol", avatarColor = "#fff")

    @Test
    fun `amounts round to the nearest paisa instead of losing one`() {
        // 33.33 * 100 is 3332.9999… in floating point, which truncation turned into 33.32.
        val debts = DebtSimplifier.simplifyPositions(mapOf(-1L to 33.33, 2L to -33.33)) { "Bob" }

        assertEquals(33.33, debts.single().amount, 0.0)
    }

    @Test
    fun `a balance relative to me rounds the same way, in both directions`() {
        // 0.29 * 100 is 28.999…, which truncated to 0.28.
        val owedToMe = DebtSimplifier.simplify(listOf(bob), mapOf(2L to 0.29)).single()
        assertEquals(listOf(2L, -1L), listOf(owedToMe.fromPersonId, owedToMe.toPersonId))
        assertEquals(0.29, owedToMe.amount, 0.0)

        val iOwe = DebtSimplifier.simplify(listOf(bob), mapOf(2L to -0.29)).single()
        assertEquals(listOf(-1L, 2L), listOf(iOwe.fromPersonId, iOwe.toPersonId))
        assertEquals(0.29, iOwe.amount, 0.0)
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
