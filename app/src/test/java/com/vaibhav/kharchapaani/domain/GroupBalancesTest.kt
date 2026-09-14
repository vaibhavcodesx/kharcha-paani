package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupBalancesTest {

    private val me = -1L
    private val bob = 2L
    private val carol = 3L

    private fun expense(amount: Double, paidBy: Long, vararg shares: Pair<Long, Double>) = FullTransaction(
        transaction = TransactionEntity(type = "expense", amount = amount, category = "Food", merchant = "", paymentMethod = "UPI", date = 0L, isSplit = true, groupId = 1L),
        split = SplitEntity(transactionId = 0L, totalAmount = amount, paidByPersonId = paidBy),
        shares = shares.map { (person, share) -> SplitShareEntity(splitId = 0L, personId = person, shareAmount = share) },
    )

    private fun settlement(from: Long, to: Long, amount: Double) =
        SettlementEntity(fromPersonId = from, toPersonId = to, amount = amount, groupId = 1L)

    @Test
    fun `a debt between two groupmates shows up even though I'm not part of it`() {
        val positions = GroupBalances.netPositions(
            expenses = listOf(expense(100.0, paidBy = bob, carol to 50.0, bob to 50.0)),
            settlements = emptyList(),
        )

        assertEquals(mapOf(bob to 50.0, carol to -50.0), positions)
        val debts = DebtSimplifier.simplifyPositions(positions) { if (it == bob) "Bob" else "Carol" }
        assertEquals(1, debts.size)
        assertEquals(carol, debts.single().fromPersonId)
        assertEquals(bob, debts.single().toPersonId)
        assertEquals(50.0, debts.single().amount, 0.0)
    }

    @Test
    fun `positions sum to zero and settlements pay debts down`() {
        val positions = GroupBalances.netPositions(
            expenses = listOf(
                expense(100.0, paidBy = me, me to 33.34, bob to 33.33, carol to 33.33),
                expense(60.0, paidBy = bob, me to 30.0, bob to 30.0),
            ),
            settlements = listOf(settlement(from = carol, to = me, amount = 33.33)),
        )

        // Me: +100 - 33.34 - 30 - 33.33 received = 3.33; Bob: +60 - 33.33 - 30 = -3.33; Carol settled.
        assertEquals(mapOf(me to 3.33, bob to -3.33), positions)
        assertEquals(0.0, positions.values.sum(), 1e-9)
    }

    @Test
    fun `an unsplit expense and a split income move nobody's balance`() {
        val unsplit = FullTransaction(
            TransactionEntity(type = "expense", amount = 80.0, category = "Food", merchant = "", paymentMethod = "UPI", date = 0L, groupId = 1L),
        )
        val income = expense(50.0, paidBy = bob, me to 50.0).let { it.copy(transaction = it.transaction.copy(type = "income")) }

        assertTrue(GroupBalances.netPositions(listOf(unsplit, income), emptyList()).isEmpty())
    }
}
