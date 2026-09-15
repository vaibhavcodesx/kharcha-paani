package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityFeedTest {

    private val bob = PersonEntity(id = 2, name = "Bob", avatarColor = "#fff")
    private val carol = PersonEntity(id = 3, name = "Carol", avatarColor = "#fff")
    private val goa = GroupEntity(id = 7, name = "Goa")

    private fun expense(id: Long, date: Long, merchant: String, isSplit: Boolean = false, type: String = "expense") = FullTransaction(
        TransactionEntity(id = id, type = type, amount = 10.0, category = "Food", merchant = merchant, paymentMethod = "UPI", date = date, isSplit = isSplit, createdAt = date),
    )

    private fun settlement(id: Long, date: Long, from: Long, to: Long, isIou: Boolean = false, groupId: Long? = null, note: String = "") =
        SettlementEntity(id = id, fromPersonId = from, toPersonId = to, amount = 50.0, date = date, note = note, isIou = isIou, groupId = groupId, createdAt = date)

    private val transactions = listOf(expense(1, 300, "Chai"), expense(2, 100, "Dinner", isSplit = true), expense(3, 50, "Salary", type = "income"))
    private val settlements = listOf(
        settlement(10, 200, from = -1, to = 2, groupId = 7),
        settlement(11, 150, from = 2, to = -1, isIou = true),
        settlement(12, 250, from = 3, to = 2, note = "cab"),
    )

    private fun feed(query: String = "", filter: String = ActivityFeed.ALL) =
        ActivityFeed.build(transactions, settlements, listOf(bob, carol), listOf(goa), query, filter)

    @Test
    fun `settlements and IOUs appear alongside transactions, newest first`() {
        assertEquals(
            listOf("tx_1", "settlement_12", "settlement_10", "settlement_11", "tx_2", "tx_3"),
            feed().map { it.key },
        )
    }

    @Test
    fun `settlement rows read naturally from this account's point of view`() {
        val titles = feed().filterIsInstance<ActivityItem.Settlement>().associate { it.settlement.id to it.title }

        assertEquals(mapOf(12L to "Carol paid Bob", 10L to "You paid Bob", 11L to "Bob lent you"), titles)
        assertEquals("Goa", feed().filterIsInstance<ActivityItem.Settlement>().single { it.settlement.id == 10L }.groupName)
    }

    @Test
    fun `settlements show under All and Splits but not Expenses or Income`() {
        assertEquals(listOf("tx_1"), feed(filter = ActivityFeed.EXPENSES).map { it.key })
        assertEquals(listOf("tx_3"), feed(filter = ActivityFeed.INCOME).map { it.key })
        assertEquals(listOf("settlement_12", "settlement_10", "settlement_11", "tx_2"), feed(filter = ActivityFeed.SPLITS).map { it.key })
    }

    @Test
    fun `groupmates' expenses and expenses in archived groups are read-only`() {
        val archived = GroupEntity(id = 8, name = "Old trip", syncState = SyncState.SYNCED, archivedAt = 1L)
        val mine = expense(20, 10, "Mine")
        val theirs = expense(21, 20, "Theirs").let { it.copy(transaction = it.transaction.copy(ownerProfileId = "bob")) }
        val inArchived = expense(22, 30, "Archived").let { it.copy(transaction = it.transaction.copy(groupId = 8)) }
        // Bob added it to a group, but I paid: I can edit it too.
        val theyAddedIPaid = expense(23, 40, "They added, I paid").let {
            it.copy(
                transaction = it.transaction.copy(ownerProfileId = "bob", groupId = 9, isSplit = true),
                split = SplitEntity(id = 5, transactionId = 23, totalAmount = 100.0, paidByPersonId = -1L),
            )
        }

        val readOnly = ActivityFeed.build(listOf(mine, theirs, inArchived, theyAddedIPaid), emptyList(), emptyList(), listOf(archived), "", ActivityFeed.ALL)
            .filterIsInstance<ActivityItem.Transaction>()
            .associate { it.fullTransaction.transaction.merchant to it.isReadOnly }

        assertEquals(mapOf("Mine" to false, "Theirs" to true, "Archived" to true, "They added, I paid" to false), readOnly)
    }

    @Test
    fun `search finds settlements by person, note, group or kind`() {
        assertEquals(listOf("settlement_12", "settlement_10", "settlement_11"), feed(query = "bob").map { it.key })
        assertEquals(listOf("settlement_12"), feed(query = "cab").map { it.key })
        assertEquals(listOf("settlement_10"), feed(query = "goa").map { it.key })
        assertEquals(listOf("settlement_11"), feed(query = "IOU").map { it.key })
        assertEquals(listOf("tx_1"), feed(query = "chai").map { it.key })
    }
}
