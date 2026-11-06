package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class FrequentPeopleTest {

    private fun person(id: Long, name: String) = PersonEntity(
        id = id,
        name = name,
        avatarColor = "#70C1FF"
    )

    private fun splitTx(
        id: Long,
        date: Long,
        paidBy: Long = -1L,
        shares: List<Pair<Long, Boolean>> = emptyList(), // personId to isGuest
    ): FullTransaction {
        val tx = TransactionEntity(
            id = id,
            type = "expense",
            amount = 100.0,
            category = "Food",
            merchant = "Test",
            paymentMethod = "Cash",
            date = date,
            isSplit = true,
        )
        val split = SplitEntity(
            id = id,
            transactionId = id,
            totalAmount = 100.0,
            paidByPersonId = paidBy,
        )
        val splitShares = shares.mapIndexed { index, (pId, isGuest) ->
            SplitShareEntity(
                id = index.toLong() + 1,
                splitId = id,
                personId = if (isGuest) -1L else pId,
                guestPersonId = if (isGuest) pId else null,
                guestName = if (isGuest) "Guest" else null,
                shareAmount = 50.0,
            )
        }
        return FullTransaction(tx, split, splitShares)
    }

    @Test
    fun `empty and single-person lists return unmodified`() {
        assertEquals(emptyList<PersonEntity>(), FrequentPeople.sortByFrequency(emptyList(), emptyList()))
        val single = listOf(person(1, "Rohan"))
        assertEquals(single, FrequentPeople.sortByFrequency(single, emptyList()))
    }

    @Test
    fun `zero split transactions sort alphabetically by name`() {
        val people = listOf(
            person(1, "Zara"),
            person(2, "Amit"),
            person(3, "Rohan")
        )
        val sorted = FrequentPeople.sortByFrequency(people, emptyList())
        assertEquals(listOf("Amit", "Rohan", "Zara"), sorted.map { it.name })
    }

    @Test
    fun `person with higher split count appears first`() {
        val p1 = person(1, "Amit")
        val p2 = person(2, "Rohan")
        val p3 = person(3, "Zara")

        val txs = listOf(
            splitTx(id = 1, date = 1000L, shares = listOf(2L to false)),
            splitTx(id = 2, date = 2000L, shares = listOf(2L to false)),
            splitTx(id = 3, date = 3000L, shares = listOf(3L to false)),
        )

        val sorted = FrequentPeople.sortByFrequency(listOf(p1, p2, p3), txs)
        assertEquals(listOf("Rohan", "Zara", "Amit"), sorted.map { it.name })
    }

    @Test
    fun `equal split counts tie break by most recent split date`() {
        val p1 = person(1, "Amit")
        val p2 = person(2, "Rohan")

        val txs = listOf(
            splitTx(id = 1, date = 1000L, shares = listOf(1L to false)),
            splitTx(id = 2, date = 5000L, shares = listOf(2L to false)),
        )

        val sorted = FrequentPeople.sortByFrequency(listOf(p1, p2), txs)
        assertEquals(listOf("Rohan", "Amit"), sorted.map { it.name })
    }

    @Test
    fun `payer counts toward split frequency`() {
        val p1 = person(1, "Amit")
        val p2 = person(2, "Rohan")

        val txs = listOf(
            splitTx(id = 1, date = 1000L, paidBy = 1L, shares = listOf(-1L to false)),
            splitTx(id = 2, date = 2000L, paidBy = 1L, shares = listOf(-1L to false)),
        )

        val sorted = FrequentPeople.sortByFrequency(listOf(p2, p1), txs)
        assertEquals(listOf("Amit", "Rohan"), sorted.map { it.name })
    }

    @Test
    fun `guest shares count toward split frequency`() {
        val p1 = person(1, "Amit")
        val p2 = person(2, "Rohan")

        val txs = listOf(
            splitTx(id = 1, date = 1000L, shares = listOf(1L to true)), // guest
        )

        val sorted = FrequentPeople.sortByFrequency(listOf(p2, p1), txs)
        assertEquals(listOf("Amit", "Rohan"), sorted.map { it.name })
    }

    @Test
    fun `non split transactions are ignored`() {
        val p1 = person(1, "Zara")
        val p2 = person(2, "Amit")

        val nonSplitTx = FullTransaction(
            transaction = TransactionEntity(
                id = 1,
                type = "expense",
                amount = 100.0,
                category = "Food",
                merchant = "Zara",
                paymentMethod = "Cash",
                isSplit = false,
            ),
            split = null,
            shares = emptyList(),
        )

        val sorted = FrequentPeople.sortByFrequency(listOf(p1, p2), listOf(nonSplitTx))
        assertEquals(listOf("Amit", "Zara"), sorted.map { it.name })
    }
}
