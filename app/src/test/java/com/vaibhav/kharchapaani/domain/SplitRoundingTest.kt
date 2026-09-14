package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.math.BigDecimal

class SplitRoundingTest {

    private fun Map<Long, Double>.sumExact(): BigDecimal = values.fold(BigDecimal.ZERO) { acc, v -> acc + BigDecimal.valueOf(v) }

    @Test
    fun `100 split three ways gives the payer the extra paisa`() {
        val shares = SplitRounding.equal(100.0, listOf(-1L, 2L, 3L), payerId = 2L)

        assertEquals(mapOf(-1L to 33.33, 2L to 33.34, 3L to 33.33), shares)
        assertEquals(BigDecimal("100.00"), shares.sumExact().setScale(2))
    }

    @Test
    fun `an even split has no leftover`() {
        assertEquals(mapOf(-1L to 150.0, 2L to 150.0), SplitRounding.equal(300.0, listOf(-1L, 2L), payerId = -1L))
    }

    @Test
    fun `a payer outside the split leaves the leftover on the largest share, first listed on ties`() {
        val shares = SplitRounding.equal(10.0, listOf(2L, 3L, 4L), payerId = -1L)

        assertEquals(mapOf(2L to 3.34, 3L to 3.33, 4L to 3.33), shares)
    }

    @Test
    fun `equal shares always add up to the amount exactly`() {
        for (amount in listOf(0.01, 0.05, 1.0, 99.99, 1234.57, 100000.01)) {
            for (people in 1..9) {
                val ids = (1L..people.toLong()).toList()
                val shares = SplitRounding.equal(amount, ids, payerId = ids.last())
                assertEquals("$amount / $people", BigDecimal.valueOf(amount).setScale(2), shares.sumExact().setScale(2))
            }
        }
    }

    @Test
    fun `percentage shares that round away a paisa are corrected on the payer`() {
        // 33.333...% of 100 each: rounds to 33.33 x 3 = 99.99.
        val raw = mapOf(-1L to 100.0 / 3, 2L to 100.0 / 3, 3L to 100.0 / 3)

        val shares = SplitRounding.normalize(100.0, raw, payerId = 3L)

        assertEquals(mapOf(-1L to 33.33, 2L to 33.33, 3L to 33.34), shares)
    }

    @Test
    fun `shares that don't cover the amount are refused rather than silently fixed`() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitRounding.normalize(100.0, mapOf(-1L to 40.0, 2L to 40.0), payerId = -1L)
        }
    }

    @Test
    fun `a participant listed twice is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitRounding.equal(100.0, listOf(2L, 2L), payerId = 2L)
        }
    }

    @Test
    fun `a zero share can't be pushed negative by the leftover`() {
        assertThrows(IllegalArgumentException::class.java) {
            // Shares over-cover by a paisa and the payer's share is zero.
            SplitRounding.normalize(10.0, mapOf(-1L to 0.0, 2L to 10.01), payerId = -1L)
        }
    }
}
