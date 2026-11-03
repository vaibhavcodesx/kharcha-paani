package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import kotlin.random.Random

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
    fun `percentages that don't quite total 100 still cover the amount exactly`() {
        val shares = SplitRounding.byWeights(100.0, mapOf(-1L to 33.3, 2L to 33.3, 3L to 33.3), payerId = 2L)

        assertEquals(mapOf(-1L to 33.33, 2L to 33.34, 3L to 33.33), shares)
    }

    @Test
    fun `uneven weights split proportionally`() {
        val shares = SplitRounding.byWeights(999.99, mapOf(-1L to 70.0, 2L to 30.0), payerId = -1L)

        // 699.993 and 299.997 floor to 699.99 and 299.99; the 0.01 left over goes to the payer.
        assertEquals(mapOf(-1L to 700.0, 2L to 299.99), shares)
        assertEquals(BigDecimal("999.99"), shares.sumExact().setScale(2))
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

    @Test
    fun `equal mode sums to the paisa across exhaustive amounts and group sizes up to 50`() {
        val testAmounts = listOf(
            0.01, 0.02, 0.03, 0.07, 0.10, 0.99,
            1.00, 3.00, 7.00, 10.00, 19.99, 33.33, 49.95, 99.99, 100.00,
            123.45, 333.33, 999.99, 1234.57, 9876.54, 11111.11,
            50000.00, 99999.99, 100000.01, 500000.00, 1000000.00
        )
        val sizes = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 13, 17, 23, 30, 42, 50)

        for (amount in testAmounts) {
            val expectedPaisa = SplitRounding.toPaisa(amount)
            for (size in sizes) {
                val participants = (1L..size.toLong()).toList()

                // Case 1: Payer is among participants
                val payerInside = participants.last()
                val sharesInside = SplitRounding.equal(amount, participants, payerId = payerInside)
                assertEquals(
                    "Equal shares inside payer for amount $amount with $size participants must match size",
                    size,
                    sharesInside.size
                )
                val sumInside = sharesInside.values.sumOf { SplitRounding.toPaisa(it) }
                assertEquals(
                    "Equal shares inside payer for amount $amount with $size participants must sum to paisa",
                    expectedPaisa,
                    sumInside
                )
                assertTrue(
                    "All shares inside must be non-negative",
                    sharesInside.values.all { it >= 0.0 }
                )

                // Case 2: Payer is outside participants
                val payerOutside = 999L
                val sharesOutside = SplitRounding.equal(amount, participants, payerId = payerOutside)
                assertEquals(
                    "Equal shares outside payer for amount $amount with $size participants must match size",
                    size,
                    sharesOutside.size
                )
                val sumOutside = sharesOutside.values.sumOf { SplitRounding.toPaisa(it) }
                assertEquals(
                    "Equal shares outside payer for amount $amount with $size participants must sum to paisa",
                    expectedPaisa,
                    sumOutside
                )
                assertTrue(
                    "All shares outside must be non-negative",
                    sharesOutside.values.all { it >= 0.0 }
                )
            }
        }
    }

    @Test
    fun `exact normalize mode sums to the paisa across varied amounts, sizes and drift directions`() {
        val testAmounts = listOf(1.00, 10.00, 50.25, 100.00, 333.33, 999.99, 5000.00, 25000.00, 100000.00)
        val rng = Random(42)

        for (amount in testAmounts) {
            val totalPaisa = SplitRounding.toPaisa(amount)
            for (size in 2..20) {
                val participants = (1L..size.toLong()).toList()
                val payer = participants.first()

                // Base distribution in paisa
                val baseEach = totalPaisa / size
                val baseRemainder = (totalPaisa % size).toInt()

                // Generate valid raw shares that deviate by at most 'size' paisa
                for (drift in -size..size) {
                    val rawPaisa = participants.mapIndexed { idx, _ ->
                        baseEach + if (idx < baseRemainder) 1L else 0L
                    }.toMutableList()

                    // Apply drift to the first participant's raw share, ensuring it stays positive
                    if (rawPaisa[0] + drift >= 0) {
                        rawPaisa[0] += drift
                    } else {
                        continue
                    }

                    val rawShares = participants.zip(rawPaisa).associate { (id, p) -> id to (p / 100.0) }
                    val normalized = SplitRounding.normalize(amount, rawShares, payerId = payer)

                    val actualSum = normalized.values.sumOf { SplitRounding.toPaisa(it) }
                    assertEquals(
                        "Normalized shares for amount $amount, size $size, drift $drift must sum to total paisa",
                        totalPaisa,
                        actualSum
                    )
                    assertTrue(
                        "All normalized shares must be non-negative",
                        normalized.values.all { it >= 0.0 }
                    )
                }
            }
        }
    }

    @Test
    fun `share byWeights mode sums to the paisa across varied amounts, group sizes and weight distributions`() {
        val testAmounts = listOf(
            0.01, 0.05, 1.00, 10.00, 77.77, 100.00, 250.50, 999.99, 3456.78, 10000.00, 50000.00, 100000.00
        )
        val seeds = listOf(101L, 202L, 303L)

        for (seed in seeds) {
            val rng = Random(seed)
            for (amount in testAmounts) {
                val totalPaisa = SplitRounding.toPaisa(amount)
                for (size in 2..25) {
                    val participants = (1L..size.toLong()).toList()
                    val payerInside = participants.random(rng)
                    val payerOutside = 888L

                    // Test with three different weight styles:
                    val weightScenarios = listOf(
                        // 1. Integer shares / units (e.g. 1 share, 2 shares, 3 shares)
                        participants.associateWith { rng.nextInt(1, 10).toDouble() },
                        // 2. Continuous weights / percentages (e.g. 15.5%, 33.3%, 51.2%)
                        participants.associateWith { rng.nextDouble(0.1, 100.0) },
                        // 3. Highly skewed weights (one dominant participant, others minor)
                        participants.mapIndexed { idx, id ->
                            id to (if (idx == 0) 1000.0 else rng.nextDouble(0.01, 1.0))
                        }.toMap()
                    )

                    for (weights in weightScenarios) {
                        // Payer inside
                        val sharesInside = SplitRounding.byWeights(amount, weights, payerId = payerInside)
                        assertEquals(
                            "byWeights shares count must match size",
                            size,
                            sharesInside.size
                        )
                        assertEquals(
                            "byWeights shares inside for amount $amount, size $size must sum to paisa",
                            totalPaisa,
                            sharesInside.values.sumOf { SplitRounding.toPaisa(it) }
                        )
                        assertTrue(
                            "All shares must be non-negative",
                            sharesInside.values.all { it >= 0.0 }
                        )

                        // Payer outside
                        val sharesOutside = SplitRounding.byWeights(amount, weights, payerId = payerOutside)
                        assertEquals(
                            "byWeights shares outside for amount $amount, size $size must sum to paisa",
                            totalPaisa,
                            sharesOutside.values.sumOf { SplitRounding.toPaisa(it) }
                        )
                        assertTrue(
                            "All shares outside must be non-negative",
                            sharesOutside.values.all { it >= 0.0 }
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `byWeights rejects non-positive or negative weights`() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitRounding.byWeights(100.0, mapOf(1L to -5.0, 2L to 10.0), payerId = 1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SplitRounding.byWeights(100.0, mapOf(1L to 0.0, 2L to 0.0), payerId = 1L)
        }
    }

    @Test
    fun `equal rejects empty participants list`() {
        assertThrows(IllegalArgumentException::class.java) {
            SplitRounding.equal(100.0, emptyList(), payerId = 1L)
        }
    }
}
