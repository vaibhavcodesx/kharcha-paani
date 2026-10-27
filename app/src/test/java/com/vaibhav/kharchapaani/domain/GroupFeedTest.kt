package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A group's history has to show the money that moved, not only the money that was spent.
 *
 * The group sheet rendered `transactions` and nothing else, so a settlement or an IOU recorded
 * inside a group appeared nowhere in it — while the balances at the top of the same sheet counted
 * it. The figures moved and the screen gave no reason, which is a worse failure than simply not
 * supporting settlements would have been.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupFeedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val me = -1L
    private val rohan = PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF")
    private val meera = PersonEntity(id = 3, name = "Meera", avatarColor = "#C6F24C")
    private val members = listOf(rohan, meera)

    private fun expense(id: Long, date: Long) = FullTransaction(
        TransactionEntity(
            id = id, type = "expense", amount = 100.0, category = "Food",
            merchant = "Dinner", paymentMethod = "Cash", date = date,
        )
    )

    private fun settlement(id: Long, date: Long, from: Long, to: Long, isIou: Boolean = false) =
        SettlementEntity(id = id, fromPersonId = from, toPersonId = to, amount = 50.0, date = date, isIou = isIou)

    @Test
    fun `settlements appear in the feed at all`() {
        // The bug, in one assertion.
        val feed = GroupFeed.of(
            transactions = listOf(expense(1, 1_000L)),
            settlements = listOf(settlement(1, 2_000L, from = rohan.id, to = me)),
        )

        assertEquals(2, feed.size)
        assertTrue(feed.any { it is GroupFeedEntry.Movement })
    }

    @Test
    fun `expenses and settlements interleave by date, newest first`() {
        val feed = GroupFeed.of(
            transactions = listOf(expense(1, 1_000L), expense(2, 3_000L)),
            settlements = listOf(settlement(1, 2_000L, from = rohan.id, to = me)),
        )

        assertEquals(listOf(3_000L, 2_000L, 1_000L), feed.map { it.date })
    }

    @Test
    fun `a group with settlements and no expenses is not an empty group`() {
        // "Nothing logged here yet" over a group where somebody was paid is simply untrue.
        val feed = GroupFeed.of(transactions = emptyList(), settlements = listOf(settlement(1, 1L, rohan.id, me)))

        assertEquals(1, feed.size)
    }

    // --- How each row reads

    @Test
    fun `a settlement is described from your side`() {
        assertEquals("Rohan paid you", GroupFeed.describe(context, settlement(1, 1L, from = rohan.id, to = me), members))
        assertEquals("You paid Rohan", GroupFeed.describe(context, settlement(1, 1L, from = me, to = rohan.id), members))
    }

    @Test
    fun `an IOU says lent, because it is not a payment`() {
        assertEquals("Rohan lent you", GroupFeed.describe(context, settlement(1, 1L, rohan.id, me, isIou = true), members))
        assertEquals("You lent Rohan", GroupFeed.describe(context, settlement(1, 1L, me, rohan.id, isIou = true), members))
    }

    @Test
    fun `money between two other members names both of them`() {
        // Possible in any group of three or more, and "Someone paid someone" would be useless.
        assertEquals("Rohan paid Meera", GroupFeed.describe(context, settlement(1, 1L, rohan.id, meera.id), members))
        assertEquals("Rohan lent Meera", GroupFeed.describe(context, settlement(1, 1L, rohan.id, meera.id, isIou = true), members))
    }

    @Test
    fun `a member who has since left is not rendered as a blank`() {
        val gone = settlement(1, 1L, from = 99L, to = me)

        assertEquals("Someone paid you", GroupFeed.describe(context, gone, members))
    }

    @Test
    fun `direction decides the colour, the same way it does everywhere else`() {
        assertTrue(GroupFeed.isIncoming(settlement(1, 1L, from = rohan.id, to = me)))
        assertFalse(GroupFeed.isIncoming(settlement(1, 1L, from = me, to = rohan.id)))
    }
}
