package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/**
 * Invariant test suite for group balances (T1):
 * - Group balances always sum to zero.
 * - Split shares always sum exactly to the expense amount in paisa.
 * - Every member's net position satisfies conservation of money across arbitrary sequences
 *   of add, edit, delete (for expenses and settlements), including unconfirmed settlements,
 *   payers outside the split, and varied split modes (equal, weighted, exact).
 */
class GroupBalancesInvariantsTest {

    private fun expense(
        id: Long,
        amount: Double,
        paidBy: Long,
        shares: Map<Long, Double>,
        type: String = "expense",
        isSplit: Boolean = true,
    ): FullTransaction {
        return FullTransaction(
            transaction = TransactionEntity(
                id = id,
                type = type,
                amount = amount,
                category = "Food",
                merchant = "Merchant",
                paymentMethod = "UPI",
                date = 1000L * id,
                isSplit = isSplit,
                groupId = 1L,
            ),
            split = if (isSplit) SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = paidBy) else null,
            shares = if (isSplit) {
                shares.map { (personId, shareAmount) ->
                    SplitShareEntity(splitId = id, personId = personId, shareAmount = shareAmount)
                }
            } else emptyList(),
        )
    }

    private fun settlement(
        id: Long,
        from: Long,
        to: Long,
        amount: Double,
        isConfirmed: Boolean = true,
    ): SettlementEntity {
        return SettlementEntity(
            id = id,
            fromPersonId = from,
            toPersonId = to,
            amount = amount,
            isConfirmed = isConfirmed,
            groupId = 1L,
        )
    }

    private fun toPaisa(rupees: Double): Long =
        BigDecimal.valueOf(rupees).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()

    private fun toRupees(paisa: Long): Double =
        BigDecimal.valueOf(paisa, 2).toDouble()

    @Test
    fun `equal split shares sum to amount across varied amounts and participant counts`() {
        val amounts = listOf(0.01, 0.05, 1.0, 10.0, 99.99, 100.0, 333.33, 1000.0, 9876.54, 50000.0)
        for (amount in amounts) {
            for (count in 1..15) {
                val participants = (1L..count.toLong()).toList()
                val payer = participants.first()
                val shares = SplitRounding.equal(amount, participants, payer)

                val sumPaisa = shares.values.sumOf { toPaisa(it) }
                assertEquals("Shares must sum to amount for $amount split among $count", toPaisa(amount), sumPaisa)

                val exp = expense(1L, amount, payer, shares)
                val positions = GroupBalances.netPositions(listOf(exp), emptyList())
                val netSumPaisa = positions.values.sumOf { toPaisa(it) }
                assertEquals("Net positions must sum to zero for $amount split among $count", 0L, netSumPaisa)
            }
        }
    }

    @Test
    fun `weighted split shares sum to amount across varied weights and amounts`() {
        val amounts = listOf(1.0, 100.0, 250.75, 999.99, 10000.0)
        val rng = Random(42)
        for (amount in amounts) {
            for (count in 2..8) {
                val participants = (1L..count.toLong()).toList()
                val payer = participants.last()
                val weights = participants.associateWith { rng.nextDouble(1.0, 100.0) }
                val shares = SplitRounding.byWeights(amount, weights, payer)

                val sumPaisa = shares.values.sumOf { toPaisa(it) }
                assertEquals("Weighted shares must sum to amount for $amount among $count", toPaisa(amount), sumPaisa)

                val exp = expense(1L, amount, payer, shares)
                val positions = GroupBalances.netPositions(listOf(exp), emptyList())
                val netSumPaisa = positions.values.sumOf { toPaisa(it) }
                assertEquals("Net positions must sum to zero for weighted split", 0L, netSumPaisa)
            }
        }
    }

    @Test
    fun `payer outside the split has full credit and zero-sum balance holds`() {
        val payer = 99L
        val participants = listOf(1L, 2L, 3L)
        val shares = SplitRounding.equal(150.0, participants, payer)

        val exp = expense(1L, 150.0, payer, shares)
        val positions = GroupBalances.netPositions(listOf(exp), emptyList())

        assertEquals(150.0, positions[payer] ?: 0.0, 0.0)
        assertEquals(-50.0, positions[1L] ?: 0.0, 0.0)
        assertEquals(-50.0, positions[2L] ?: 0.0, 0.0)
        assertEquals(-50.0, positions[3L] ?: 0.0, 0.0)
        assertEquals(0L, positions.values.sumOf { toPaisa(it) })
    }

    @Test
    fun `unconfirmed settlements and unsplit expenses do not move balances`() {
        val p1 = 1L
        val p2 = 2L
        val expenses = listOf(
            expense(1L, 100.0, p1, SplitRounding.equal(100.0, listOf(p1, p2), p1)),
            expense(2L, 50.0, p1, emptyMap(), isSplit = false), // Unsplit: payer's own expense
            expense(3L, 20.0, p2, SplitRounding.equal(20.0, listOf(p1, p2), p2), type = "income"), // Income ignored
        )
        val settlements = listOf(
            settlement(1L, from = p2, to = p1, amount = 25.0, isConfirmed = true),
            settlement(2L, from = p2, to = p1, amount = 25.0, isConfirmed = false), // Pending: must not move balance
        )

        val positions = GroupBalances.netPositions(expenses, settlements)
        // Expense 1: p1 paid 100, shares: p1=50, p2=50 -> net p1=+50, p2=-50.
        // Settlement 1 (confirmed): p2 pays p1 25 -> p2 debited -50 + 25 = -25, p1 credited +50 - 25 = +25.
        // Settlement 2 (unconfirmed): ignored.
        assertEquals(25.0, positions[p1] ?: 0.0, 0.0)
        assertEquals(-25.0, positions[p2] ?: 0.0, 0.0)
        assertEquals(0L, positions.values.sumOf { toPaisa(it) })
    }

    @Test
    fun `random sequences of add, edit, delete and settle operations preserve all invariants`() {
        val members = listOf(10L, 20L, 30L, 40L, 50L, 60L)
        val seeds = listOf(1337L, 424242L, 999999L)

        for (seed in seeds) {
            val rng = Random(seed)
            val expenses = mutableMapOf<Long, FullTransaction>()
            val settlements = mutableMapOf<Long, SettlementEntity>()
            var nextExpenseId = 1L
            var nextSettlementId = 1L

            // Run 200 random operations per seed
            for (step in 1..200) {
                val opChoice = if (expenses.isEmpty() && settlements.isEmpty()) {
                    0 // Must add first
                } else {
                    rng.nextInt(6)
                }

                when (opChoice) {
                    0 -> {
                        // Add expense
                        val id = nextExpenseId++
                        val payer = members.random(rng)
                        val participantCount = rng.nextInt(1, members.size + 1)
                        val participants = members.shuffled(rng).take(participantCount)
                        val amountPaisa = rng.nextLong(100L, 500000L) // ₹1.00 to ₹5000.00
                        val amount = toRupees(amountPaisa)

                        val shares = when (rng.nextInt(3)) {
                            0 -> SplitRounding.equal(amount, participants, payer)
                            1 -> {
                                val weights = participants.associateWith { rng.nextDouble(1.0, 50.0) }
                                SplitRounding.byWeights(amount, weights, payer)
                            }
                            else -> {
                                // Exact shares with small rounding drift
                                val baseEach = amountPaisa / participants.size
                                val rawShares = participants.associateWith { toRupees(baseEach) }
                                SplitRounding.normalize(amount, rawShares, payer)
                            }
                        }
                        expenses[id] = expense(id, amount, payer, shares)
                    }
                    1 -> {
                        // Edit expense
                        if (expenses.isNotEmpty()) {
                            val id = expenses.keys.random(rng)
                            val payer = members.random(rng)
                            val participantCount = rng.nextInt(1, members.size + 1)
                            val participants = members.shuffled(rng).take(participantCount)
                            val amountPaisa = rng.nextLong(100L, 500000L)
                            val amount = toRupees(amountPaisa)
                            val shares = SplitRounding.equal(amount, participants, payer)
                            expenses[id] = expense(id, amount, payer, shares)
                        }
                    }
                    2 -> {
                        // Delete expense
                        if (expenses.isNotEmpty()) {
                            val id = expenses.keys.random(rng)
                            expenses.remove(id)
                        }
                    }
                    3 -> {
                        // Add settlement
                        val id = nextSettlementId++
                        val from = members.random(rng)
                        val to = (members - from).random(rng)
                        val amountPaisa = rng.nextLong(100L, 200000L) // ₹1.00 to ₹2000.00
                        val amount = toRupees(amountPaisa)
                        val isConfirmed = rng.nextDouble() < 0.85
                        settlements[id] = settlement(id, from, to, amount, isConfirmed)
                    }
                    4 -> {
                        // Edit settlement
                        if (settlements.isNotEmpty()) {
                            val id = settlements.keys.random(rng)
                            val old = settlements.getValue(id)
                            val newAmount = if (rng.nextBoolean()) toRupees(rng.nextLong(100L, 200000L)) else old.amount
                            val newConfirmed = if (rng.nextBoolean()) !old.isConfirmed else old.isConfirmed
                            settlements[id] = old.copy(amount = newAmount, isConfirmed = newConfirmed)
                        }
                    }
                    5 -> {
                        // Delete settlement
                        if (settlements.isNotEmpty()) {
                            val id = settlements.keys.random(rng)
                            settlements.remove(id)
                        }
                    }
                }

                // INVARIANT 1: For every expense, shares sum exactly to total amount in paisa
                for (exp in expenses.values) {
                    val expectedPaisa = toPaisa(exp.split!!.totalAmount)
                    val actualPaisa = exp.shares.sumOf { toPaisa(it.shareAmount) }
                    assertEquals(
                        "Seed $seed step $step: expense ${exp.transaction.id} shares must sum to total amount",
                        expectedPaisa,
                        actualPaisa,
                    )
                }

                // INVARIANT 2: Group balances always sum to zero in paisa
                val currentExpenses = expenses.values.toList()
                val currentSettlements = settlements.values.toList()
                val netPositions = GroupBalances.netPositions(currentExpenses, currentSettlements)
                val totalPaisa = netPositions.values.sumOf { toPaisa(it) }
                assertEquals(
                    "Seed $seed step $step: group net positions must sum to zero in paisa",
                    0L,
                    totalPaisa,
                )

                // INVARIANT 3: Conservation of money holds for every member
                for (m in members) {
                    val paidInSplits = currentExpenses
                        .filter { it.split?.paidByPersonId == m }
                        .sumOf { exp -> exp.shares.sumOf { toPaisa(it.shareAmount) } }
                    val consumedInSplits = currentExpenses
                        .sumOf { exp -> exp.shares.filter { it.personId == m }.sumOf { toPaisa(it.shareAmount) } }
                    val settledSent = currentSettlements
                        .filter { it.isConfirmed && it.fromPersonId == m }
                        .sumOf { toPaisa(it.amount) }
                    val settledReceived = currentSettlements
                        .filter { it.isConfirmed && it.toPersonId == m }
                        .sumOf { toPaisa(it.amount) }

                    val expectedNetPaisa = paidInSplits - consumedInSplits + settledSent - settledReceived
                    val actualNetPaisa = toPaisa(netPositions[m] ?: 0.0)
                    assertEquals(
                        "Seed $seed step $step: member $m net position must match conservation formula",
                        expectedNetPaisa,
                        actualNetPaisa,
                    )
                }
            }
        }
    }
}
