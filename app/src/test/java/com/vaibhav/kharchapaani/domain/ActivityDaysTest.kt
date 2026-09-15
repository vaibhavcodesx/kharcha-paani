package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

class ActivityDaysTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 9, 15)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun tx(id: Long, date: Long, amount: Double, type: String = "expense") =
        ActivityItem.Transaction(FullTransaction(TransactionEntity(id = id, type = type, amount = amount, category = "Food", merchant = "m", paymentMethod = "UPI", date = date)))

    private fun settlement(id: Long, date: Long) =
        ActivityItem.Settlement(SettlementEntity(id = id, fromPersonId = -1, toPersonId = 2, amount = 500.0, date = date), "You", "Bob", null)

    private fun group(vararg items: ActivityItem) = ActivityDays.group(items.toList(), today, zone, Locale.US)

    @Test
    fun `days are labelled Today, Yesterday, then by date, with the year only when it differs`() {
        val days = group(
            tx(1, at(today, 9), 10.0),
            tx(2, at(today.minusDays(1), 22), 10.0),
            tx(3, at(LocalDate.of(2026, 9, 7), 12), 10.0),
            tx(4, at(LocalDate.of(2025, 12, 31), 12), 10.0),
        )

        assertEquals(listOf("Today", "Yesterday", "Mon, 7 Sep", "Wed, 31 Dec 2025"), days.map { it.label })
    }

    @Test
    fun `a day is the person's local day, not UTC`() {
        // 00:30 in India is still the previous day in UTC.
        val days = group(tx(1, at(today, 0, 30), 10.0), tx(2, at(today.minusDays(1), 23, 50), 10.0))

        assertEquals(listOf("Today", "Yesterday"), days.map { it.label })
    }

    @Test
    fun `items keep the feed's order within and across days`() {
        val days = group(tx(1, at(today, 18), 1.0), settlement(9, at(today, 12)), tx(2, at(today, 8), 1.0), tx(3, at(today.minusDays(3), 8), 1.0))

        assertEquals(listOf(listOf("tx_1", "settlement_9", "tx_2"), listOf("tx_3")), days.map { day -> day.items.map { it.key } })
    }

    @Test
    fun `a day's spend counts my share of expenses, not income or settlements`() {
        val split = ActivityItem.Transaction(
            FullTransaction(
                TransactionEntity(id = 5, type = "expense", amount = 100.0, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = at(today, 20), isSplit = true),
                split = SplitEntity(id = 1, transactionId = 5, totalAmount = 100.0, paidByPersonId = -1L),
                shares = listOf(
                    SplitShareEntity(splitId = 1, personId = -1L, shareAmount = 33.34),
                    SplitShareEntity(splitId = 1, personId = 2L, shareAmount = 33.33),
                    SplitShareEntity(splitId = 1, personId = 3L, shareAmount = 33.33),
                ),
            ),
        )

        val day = group(tx(1, at(today, 9), 34.5), split, tx(2, at(today, 10), 5000.0, type = "income"), settlement(9, at(today, 11))).single()

        assertEquals(67.84, day.spent, 0.0)
    }
}
