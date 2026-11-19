package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ItemSplitPropertyTest {

    @Test
    fun `rounding property holds across 500 random bills`() {
        val random = Random(42)

        for (seedIndex in 0 until 500) {
            val itemCount = random.nextInt(1, 13)
            val items = (0 until itemCount).map { i ->
                SplitItem(
                    id = "item_$i",
                    name = "Item $i",
                    quantity = random.nextInt(1, 5),
                    amountPaise = random.nextLong(100L, 500_001L),
                )
            }

            val peopleCount = random.nextInt(1, 9)
            val people = (0 until peopleCount).map { i ->
                SplitPerson(id = "person_$i", name = "Person $i")
            }

            val claims = mutableListOf<SplitClaim>()
            for (item in items) {
                // Some items unclaimed (e.g. 20% chance)
                if (random.nextInt(5) != 0) {
                    val claimerCount = random.nextInt(1, peopleCount + 1)
                    val claimers = people.shuffled(random).take(claimerCount)
                    for (claimer in claimers) {
                        claims.add(SplitClaim(itemId = item.id, personId = claimer.id))
                    }
                }
            }

            val subtotal = items.sumOf { it.amountPaise }
            val taxPaise = (subtotal * random.nextInt(0, 21)) / 100L
            val serviceChargePaise = (subtotal * random.nextInt(0, 21)) / 100L
            val tipPaise = (subtotal * random.nextInt(0, 21)) / 100L
            val totalPaise = subtotal + taxPaise + serviceChargePaise + tipPaise

            val bill = ItemSplit(
                totalPaise = totalPaise,
                items = items,
                people = people,
                claims = claims,
            )

            val failureContext = "seedIndex=$seedIndex, bill=$bill"

            val settled = ItemSplitMath.withLeftoversShared(bill)
            val amounts = ItemSplitMath.finalAmounts(settled)

            // (a) the sum of finalAmounts equals the bill total in paise exactly
            assertEquals(
                "Sum of finalAmounts must equal totalPaise ($failureContext)",
                totalPaise,
                amounts.values.sum(),
            )

            // (b) every amount is >= 0
            assertTrue(
                "Every amount must be >= 0 ($failureContext, amounts=$amounts)",
                amounts.values.all { it >= 0L },
            )

            // (c) a person who claimed nothing and is not sharing leftovers pays exactly 0
            val pickedItemIds = bill.claims.filter { it.weight > 0 }.map { it.itemId }.toSet()
            val hasLeftovers = bill.items.any { it.id !in pickedItemIds }
            val claimedPersonIds = bill.claims.filter { it.weight > 0 }.map { it.personId }.toSet()
            if (!hasLeftovers) {
                for (person in bill.people) {
                    if (person.id !in claimedPersonIds) {
                        assertEquals(
                            "Person ${person.id} claimed nothing and is not sharing leftovers, must pay 0 ($failureContext)",
                            0L,
                            amounts.getValue(person.id),
                        )
                    }
                }
            }

            val rawShares = ItemSplitMath.shares(bill)
            for (person in bill.people) {
                if (person.id !in claimedPersonIds) {
                    assertEquals(
                        "Person ${person.id} claimed nothing, initial shares total must be 0 ($failureContext)",
                        0L,
                        rawShares.people.getValue(person.id).totalPaise,
                    )
                }
            }

            // (d) running the function twice gives the same result
            val amountsSecondRun = ItemSplitMath.finalAmounts(settled)
            assertEquals(
                "finalAmounts must be deterministic when called twice ($failureContext)",
                amounts,
                amountsSecondRun,
            )
            assertEquals(
                "finalAmounts(bill) must match finalAmounts(settled) ($failureContext)",
                amounts,
                ItemSplitMath.finalAmounts(bill),
            )
        }
    }
}
