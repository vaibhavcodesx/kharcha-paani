package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class GroupInsightsTest {

    private val priya = PersonEntity(id = 2, name = "Priya", avatarColor = "#70C1FF")
    private val rohan = PersonEntity(id = 3, name = "Rohan", avatarColor = "#B5EAD7")
    private val members = listOf(priya, rohan)

    private var nextId = 0L

    private fun split(
        amount: Double,
        paidBy: Long,
        shares: Map<Long, Double>,
        category: String = "Food",
        type: String = "expense",
    ): FullTransaction {
        val id = ++nextId
        return FullTransaction(
            TransactionEntity(id = id, type = type, amount = amount, category = category, merchant = "m$id", paymentMethod = "UPI", date = id, groupId = 7, isSplit = true),
            split = SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = paidBy),
            shares = shares.map { (person, share) -> SplitShareEntity(splitId = id, personId = person, shareAmount = share) },
        )
    }

    private fun unsplit(amount: Double, category: String = "Transport"): FullTransaction {
        val id = ++nextId
        return FullTransaction(
            TransactionEntity(id = id, type = "expense", amount = amount, category = category, merchant = "m$id", paymentMethod = "UPI", date = id, groupId = 7),
        )
    }

    // I paid ₹900 for dinner split three ways; Priya paid ₹600 for a cab split with me.
    private val expenses = listOf(
        split(900.0, paidBy = -1L, shares = mapOf(-1L to 300.0, 2L to 300.0, 3L to 300.0)),
        split(600.0, paidBy = 2L, shares = mapOf(-1L to 300.0, 2L to 300.0), category = "Transport"),
    )

    @Test
    fun `member shares are what the splits say, largest first, adding up to the whole`() {
        val shares = GroupInsights.memberShares(expenses, members)

        assertEquals(listOf("You", "Priya", "Rohan"), shares.map { it.name })
        assertEquals(listOf(600.0, 600.0, 300.0), shares.map { it.amount })
        assertEquals(1f, shares.map { it.fraction }.sum(), 0.001f)
        assertEquals("#70C1FF", shares.first { it.name == "Priya" }.colorHex)
    }

    @Test
    fun `an expense with no split is all the payer's, as the balances treat it`() {
        val shares = GroupInsights.memberShares(expenses + unsplit(200.0), members)

        assertEquals(800.0, shares.first { it.personId == GroupInsights.ME }.amount, 0.0)
    }

    @Test
    fun `income and an empty group don't make shares`() {
        assertEquals(emptyList<MemberShare>(), GroupInsights.memberShares(emptyList(), members))
        val income = split(5000.0, paidBy = -1L, shares = mapOf(-1L to 5000.0), type = "income")
        assertEquals(emptyList<MemberShare>(), GroupInsights.memberShares(listOf(income), members))
    }

    @Test
    fun `who owes whom nets the group out, and settlements clear it`() {
        // I'm owed 600 (300 from each), and owe Priya 300: Rohan owes me 300, Priya owes me 0 net.
        val debts = GroupInsights.owesWhom(expenses, emptyList(), members)

        assertEquals(listOf("Rohan" to "You"), debts.map { it.fromPersonName to it.toPersonName })
        assertEquals(300.0, debts.single().amount, 0.0)

        val paid = SettlementEntity(id = 1, fromPersonId = 3, toPersonId = -1L, amount = 300.0, date = 0L, groupId = 7)
        assertEquals(emptyList<String>(), GroupInsights.owesWhom(expenses, listOf(paid), members).map { it.fromPersonName })
    }

    @Test
    fun `category totals are the group's whole spending, not my share`() {
        val totals = GroupInsights.categoryTotals(expenses)

        assertEquals(listOf("Food" to 900.0, "Transport" to 600.0), totals.map { it.category to it.amount })
        assertEquals(60f, totals.first().percentage, 0.01f)
        assertEquals(100f, totals.map { it.percentage }.sum(), 0.01f)
    }
}
