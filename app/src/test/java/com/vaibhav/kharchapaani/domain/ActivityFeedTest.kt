package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityFeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

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
        ActivityFeed.build(context, transactions, settlements, listOf(bob, carol), listOf(goa), query, filter)

    @Test
    fun `settlements and IOUs appear alongside transactions, newest first`() {
        assertEquals(
            listOf("tx_1", "settlement_10", "settlement_11", "tx_2", "tx_3"),
            feed().map { it.key },
        )
    }

    @Test
    fun `settlement rows read naturally from this account's point of view`() {
        val titles = feed().filterIsInstance<ActivityItem.Settlement>().associate { it.settlement.id to it.title(context) }

        assertEquals(mapOf(10L to "You paid Bob", 11L to "Bob lent you"), titles)
        assertEquals("Goa", feed().filterIsInstance<ActivityItem.Settlement>().single { it.settlement.id == 10L }.groupName)
    }

    @Test
    fun `settlements show under All and Splits but not Expenses or Income`() {
        assertEquals(listOf("tx_1"), feed(filter = ActivityFeed.EXPENSES).map { it.key })
        assertEquals(listOf("tx_3"), feed(filter = ActivityFeed.INCOME).map { it.key })
        assertEquals(listOf("settlement_10", "settlement_11", "tx_2"), feed(filter = ActivityFeed.SPLITS).map { it.key })
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

        val readOnly = ActivityFeed.build(context, listOf(mine, theirs, inArchived, theyAddedIPaid), emptyList(), emptyList(), listOf(archived), "", ActivityFeed.ALL)
            .filterIsInstance<ActivityItem.Transaction>()
            .associate { it.fullTransaction.transaction.merchant to it.isReadOnly }

        assertEquals(mapOf("Mine" to false, "Theirs" to true, "Archived" to true, "They added, I paid" to false), readOnly)
    }

    @Test
    fun `search finds settlements by person, note, group or kind`() {
        assertEquals(listOf("settlement_10", "settlement_11"), feed(query = "bob").map { it.key })
        assertEquals(emptyList<String>(), feed(query = "cab").map { it.key })
        assertEquals(listOf("settlement_10"), feed(query = "goa").map { it.key })
        assertEquals(listOf("settlement_11"), feed(query = "IOU").map { it.key })
        assertEquals(listOf("tx_1"), feed(query = "chai").map { it.key })
    }

    @Test
    fun `a settlement between two other people is not in my Activity, in a group or out of one`() {
        val others = listOf(
            settlement(30, 10, from = 2, to = 3, groupId = 7),
            settlement(31, 20, from = 3, to = 2),
        )
        val mine = listOf(
            settlement(32, 30, from = -1, to = 2, groupId = 7),
            settlement(33, 40, from = 3, to = -1, groupId = 7),
        )

        val keys = ActivityFeed.build(context, emptyList(), others + mine, listOf(bob, carol), listOf(goa), "", ActivityFeed.ALL).map { it.key }

        assertEquals(listOf("settlement_33", "settlement_32"), keys)
    }

    private fun groupExpense(
        id: Long,
        paidBy: Long?,
        shares: Map<Long, Double>,
        addedBy: String? = null,
        groupId: Long? = 7,
        guestOf: Long? = null,
    ): FullTransaction {
        val tx = TransactionEntity(
            id = id, type = "expense", amount = 100.0, category = "Food", merchant = "Item $id", paymentMethod = "UPI",
            date = id, isSplit = paidBy != null, createdAt = id, groupId = groupId, ownerProfileId = addedBy,
        )
        if (paidBy == null) return FullTransaction(tx)
        return FullTransaction(
            tx,
            SplitEntity(id = id, transactionId = id, totalAmount = 100.0, paidByPersonId = paidBy),
            shares.map { (person, amount) ->
                SplitShareEntity(splitId = id, personId = person, shareAmount = amount, guestName = if (person == guestOf) "Guest" else null)
            },
        )
    }

    @Test
    fun `a group expense I did not pay, add or share is hidden, however it is split`() {
        val hidden = listOf(
            groupExpense(40, paidBy = 2, shares = mapOf(2L to 50.0, 3L to 50.0), addedBy = "bob"),
            // I am listed, but my share is nothing: "yours ₹0".
            groupExpense(41, paidBy = 2, shares = mapOf(2L to 100.0, -1L to 0.0), addedBy = "bob"),
        )
        val shown = listOf(
            groupExpense(42, paidBy = 2, shares = mapOf(2L to 50.0, -1L to 50.0), addedBy = "bob"),
            groupExpense(43, paidBy = -1L, shares = mapOf(2L to 100.0), addedBy = "bob"),
            groupExpense(44, paidBy = 2, shares = mapOf(2L to 50.0, 3L to 50.0)), // I added it
            groupExpense(45, paidBy = 2, shares = mapOf(2L to 50.0, -1L to 50.0), addedBy = "bob", guestOf = -1L), // a guest I cover
            groupExpense(46, paidBy = null, shares = emptyMap(), groupId = null), // personal, no group
        )

        val keys = ActivityFeed.build(context, hidden + shown, emptyList(), listOf(bob, carol), listOf(goa), "", ActivityFeed.ALL).map { it.key }

        assertEquals(listOf("tx_46", "tx_45", "tx_44", "tx_43", "tx_42"), keys)
    }

    @Test
    fun `search and filters never bring back what I am not in`() {
        val notMine = groupExpense(50, paidBy = 2, shares = mapOf(2L to 50.0, 3L to 50.0), addedBy = "bob")

        for (filter in listOf(ActivityFeed.ALL, ActivityFeed.SPLITS)) {
            assertEquals(emptyList<String>(), ActivityFeed.build(context, listOf(notMine), emptyList(), listOf(bob, carol), listOf(goa), "Item 50", filter).map { it.key })
        }
    }
}
