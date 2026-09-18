package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * The "3×" chip on an activity row (backlog §5, Image 10). Scoped to the calendar month on purpose:
 * "you have paid Rapido 47 times" since install is trivia.
 */
class MerchantVisitsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val month = YearMonth.of(2026, 9)

    private fun at(m: Int, day: Int) =
        LocalDateTime.of(2026, m, day, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(id: Long, merchant: String, m: Int = 9, day: Int = 5, type: String = "expense") =
        ActivityItem.Transaction(
            FullTransaction(
                TransactionEntity(
                    id = id, type = type, amount = 100.0, category = "Food", merchant = merchant,
                    paymentMethod = "UPI", date = at(m, day),
                )
            )
        )

    @Test
    fun `a merchant seen three times this month counts three`() {
        val counts = MerchantVisits.thisMonth(
            listOf(tx(1, "Rapido"), tx(2, "Rapido", day = 9), tx(3, "Rapido", day = 14), tx(4, "Swiggy")),
            month, zone,
        )

        assertEquals(3, counts["rapido"])
        assertEquals(1, counts["swiggy"])
    }

    @Test
    fun `spelling and spacing don't split one merchant into two`() {
        val counts = MerchantVisits.thisMonth(
            listOf(tx(1, "Blue Tokai"), tx(2, "  blue tokai "), tx(3, "BLUE TOKAI")),
            month, zone,
        )

        assertEquals(3, counts["blue tokai"])
        assertEquals(1, counts.size)
    }

    @Test
    fun `last month's visits don't count toward this month`() {
        val counts = MerchantVisits.thisMonth(
            listOf(tx(1, "Rapido", m = 8, day = 20), tx(2, "Rapido", m = 8, day = 21), tx(3, "Rapido")),
            month, zone,
        )

        assertEquals(1, counts["rapido"])
    }

    @Test
    fun `income is not a visit`() {
        val counts = MerchantVisits.thisMonth(listOf(tx(1, "Acme", type = "income")), month, zone)

        assertTrue(counts.isEmpty())
    }

    @Test
    fun `a settlement is not a merchant`() {
        val settlement = ActivityItem.Settlement(
            SettlementEntity(id = 1, fromPersonId = 2, toPersonId = -1L, amount = 500.0, date = at(9, 5)),
            "Rohan", "You", null,
        )

        assertTrue(MerchantVisits.thisMonth(listOf(settlement), month, zone).isEmpty())
    }

    @Test
    fun `a blank merchant has no key to count under`() {
        assertNull(MerchantVisits.key("   "))
        assertTrue(MerchantVisits.thisMonth(listOf(tx(1, "  ")), month, zone).isEmpty())
    }
}
