package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * What the model is actually asked. The voice rules forbid inventing a figure, so anything an
 * answer should be able to say has to be in this block — which makes the block the part most worth
 * pinning down, and the reason it is built in the domain layer rather than at the call site.
 */
class AiContextTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val month = YearMonth.of(2026, 9)
    private val today = LocalDate.of(2026, 9, 18)

    private fun at(m: Int, day: Int) =
        LocalDateTime.of(2026, m, day, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun tx(id: Long, merchant: String, amount: Double, m: Int = 9, day: Int = 5, method: String = "UPI") =
        FullTransaction(
            TransactionEntity(
                id = id, type = "expense", amount = amount, category = "Food", merchant = merchant,
                paymentMethod = method, date = at(m, day),
            )
        )

    private val analytics = MonthlyAnalytics(
        totalExpense = 5265.34,
        totalIncome = 0.0,
        netCashflow = -5265.34,
        categorySummaries = listOf(
            CategorySummary("Transport", 2636.66, 50f, 6),
            CategorySummary("Food", 2009.68, 38f, 11),
        ),
        topMerchant = "Train tickets return",
        dailyStreak = 4,
    )

    // --- Month in review

    @Test
    fun `the month block carries every figure the screen shows`() {
        val days = DailySpend.of(listOf(tx(1, "Chai", 120.0, day = 3)), month, today, zone)
        val block = AiContext.monthInReview(analytics, days, month)

        assertTrue(block.contains("₹5265.34"))
        assertTrue(block.contains("Transport: ₹2636.66"))
        assertTrue(block.contains("Food: ₹2009.68"))
        assertTrue(block.contains("Train tickets return"))
        assertTrue(block.contains("Days logged in a row: 4"))
    }

    @Test
    fun `the month block carries the daily series, which is what a total cannot say`() {
        val days = DailySpend.of(
            listOf(tx(1, "Chai", 120.0, day = 3), tx(2, "Dinner", 1800.0, day = 9)),
            month, today, zone,
        )
        val block = AiContext.monthInReview(analytics, days, month)

        assertTrue(block.contains("Busiest day: 9 September"))
        // Spoken, like the busiest day: ISO lines were quoted back as "2026-09-09".
        assertTrue(block.contains("3 September: ₹120"))
        assertTrue(block.contains("9 September: ₹1800"))
        assertFalse(block.contains("2026-09-0"))
    }

    @Test
    fun `amounts carry the rupee sign, and never a dollar`() {
        // The prompt used to say "Rs", and answers dutifully came back saying "Rs 4672" beside a
        // screen reading "₹4,672". A model writes back the notation it is shown.
        val block = AiContext.monthInReview(analytics, emptyList(), month)

        assertFalse(block.contains("$"))
        assertFalse(block.contains("Rs "))
        assertTrue(block.contains("₹"))
    }

    // --- The trailing comparison

    @Test
    fun `with no history at all the block says so, rather than leaving it to be guessed`() {
        val block = AiContext.trailingComparison(analytics.categorySummaries, emptyMap(), month)

        assertTrue(block.contains("There is no earlier month of data at all"))
        assertTrue(block.contains("Months of history available before this one: 0"))
    }

    @Test
    fun `a month still in progress says so, and months are named the way people say them`() {
        // Otherwise 18 days of September were compared against three whole months, and categories
        // that simply hadn't happened yet were called "down".
        val previous = mapOf(YearMonth.of(2026, 8) to listOf(CategorySummary("Food", 3000.0, 100f, 9)))
        val block = AiContext.trailingComparison(analytics.categorySummaries, previous, month, today = today)

        assertTrue(block.contains("still in progress: day 18 of 30"))
        assertTrue(block.contains("September 2026"))
        assertTrue(block.contains("August 2026:"))
        assertFalse(block.contains("2026-08"))
    }

    @Test
    fun `a past month is not called in progress`() {
        val block = AiContext.trailingComparison(analytics.categorySummaries, emptyMap(), YearMonth.of(2026, 8), today = today)

        assertFalse(block.contains("in progress"))
    }

    @Test
    fun `a category missing from an earlier month averages as zero, not as absent`() {
        // Otherwise "Food ₹2,000 this month vs ₹2,000 last month" reads as flat when the two
        // earlier months had no Food at all, and the average is really ₹667.
        val previous = mapOf(
            YearMonth.of(2026, 6) to listOf(CategorySummary("Food", 2000.0, 100f, 4)),
            YearMonth.of(2026, 7) to emptyList(),
            YearMonth.of(2026, 8) to emptyList(),
        )

        assertEquals(666.67, AiContext.averagePerCategory(previous)["Food"]!!, 0.01)
    }

    @Test
    fun `history is counted from months that actually have something in them`() {
        assertFalse(AiContext.hasEnoughHistory(mapOf(YearMonth.of(2026, 8) to emptyList())))
        assertTrue(
            AiContext.hasEnoughHistory(
                mapOf(YearMonth.of(2026, 8) to listOf(CategorySummary("Food", 10.0, 100f, 1)))
            )
        )
    }

    @Test
    fun `the comparison never reaches back before the first month with data`() {
        val months = AiContext.trailingMonths(month, earliest = YearMonth.of(2026, 8))

        assertEquals(listOf(YearMonth.of(2026, 8)), months)
    }

    @Test
    fun `a long history reaches back exactly three months`() {
        val months = AiContext.trailingMonths(month, earliest = YearMonth.of(2020, 1))

        assertEquals(
            listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)),
            months,
        )
    }

    // --- What to change

    @Test
    fun `a yearly subscription is comparable with a monthly one`() {
        val yearly = SubscriptionEntity(
            id = 1, name = "Prime", amount = 1499.0, category = "Bills", paymentMethod = "UPI",
            cycle = "YEARLY", startDate = at(1, 1),
        )

        assertEquals(124.92, AiContext.monthlyEquivalent(yearly), 0.01)
    }

    @Test
    fun `a cancelled subscription is not something to change`() {
        val live = SubscriptionEntity(
            id = 1, name = "Netflix", amount = 199.0, category = "Bills", paymentMethod = "UPI",
            cycle = "MONTHLY", startDate = at(1, 1),
        )
        val cancelled = live.copy(id = 2, name = "Hotstar", cancelledAt = at(8, 1))
        val block = AiContext.whatToChange("", listOf(live, cancelled), emptyList(), month, zone)

        assertTrue(block.contains("Netflix"))
        assertFalse(block.contains("Hotstar"))
    }

    @Test
    fun `with nothing running the block says none rather than omitting the section`() {
        val block = AiContext.whatToChange("", emptyList(), emptyList(), month, zone)

        assertTrue(block.contains("Subscriptions running"))
        assertTrue(block.contains("- none"))
    }

    @Test
    fun `the payment mix is this month's expenses, biggest first`() {
        val mix = AiContext.paymentMix(
            listOf(
                tx(1, "A", 100.0, method = "UPI"),
                tx(2, "B", 500.0, method = "Credit Card"),
                tx(3, "C", 50.0, method = "UPI"),
                tx(4, "Old", 9000.0, m = 8, method = "Cash"),
            ),
            month, zone,
        )

        assertEquals(listOf("Credit Card", "UPI"), mix.keys.toList())
        assertEquals(500.0, mix["Credit Card"]!!, 0.001)
        assertEquals(150.0, mix["UPI"]!!, 0.001)
    }

    // --- The trip read

    @Test
    fun `a trip block says how long it ran and what that came to per day`() {
        // Without the span an answer can only restate the total, which is the difference between
        // this prompt and a receipt.
        val details = GroupWithDetails(
            group = GroupEntity(id = 1, name = "Goa trip", emoji = "🏖", description = ""),
            members = listOf(PersonEntity(id = 2, name = "Lal", avatarColor = "#70C1FF")),
            totalExpenses = 9000.0,
            myPaidAmount = 5000.0,
            myShare = 4500.0,
            netBalanceForMe = 500.0,
            transactionsCount = 2,
            transactions = listOf(tx(1, "Hotel", 8000.0, day = 1), tx(2, "Cab", 1000.0, day = 9)),
        )
        val block = AiContext.tripReview(
            details, emptyList(), emptyList(), emptyList(), emptyList(), details.members, zone,
        )

        assertTrue(block.contains("Goa trip"))
        assertTrue(block.contains("1 September to 9 September"))
        assertTrue(block.contains("9 day(s)"))
        assertTrue(block.contains("₹1000 a day"))
        assertTrue(block.contains("Single biggest expense: Hotel at ₹8000"))
    }

    @Test
    fun `who paid up front is kept apart from what each share came to`() {
        // "Who carried the cost" needs who paid. The block had only shares, headed "Who spent
        // what", and answers credited whoever ate most with paying for everything.
        val lal = PersonEntity(id = 2, name = "Lal", avatarColor = "#70C1FF")
        val hotel = FullTransaction(
            TransactionEntity(id = 1, type = "expense", amount = 8000.0, category = "Stay", merchant = "Hotel", paymentMethod = "UPI", date = at(9, 1)),
            split = com.vaibhav.kharchapaani.data.db.entity.SplitEntity(id = 1, transactionId = 1, totalAmount = 8000.0, paidByPersonId = 2),
        )
        val cab = FullTransaction(
            TransactionEntity(id = 2, type = "expense", amount = 1000.0, category = "Transport", merchant = "Cab", paymentMethod = "UPI", date = at(9, 2)),
        )

        assertEquals(listOf("Lal" to 8000.0, "You" to 1000.0), AiContext.paidBy(listOf(hotel, cab), listOf(lal)))

        val details = GroupWithDetails(
            group = GroupEntity(id = 1, name = "Goa trip", emoji = "🏖", description = ""),
            members = listOf(lal), totalExpenses = 9000.0, myPaidAmount = 1000.0, myShare = 4500.0,
            netBalanceForMe = -3500.0, transactionsCount = 2, transactions = listOf(hotel, cab),
        )
        val block = AiContext.tripReview(details, emptyList(), emptyList(), emptyList(), emptyList(), details.members, zone)
        assertTrue(block.contains("Who paid up front:\n- Lal: ₹8000\n- You: ₹1000"))
        assertTrue(block.contains("Each person's share of the spending"))
    }

    @Test
    fun `a settled group says everyone is square rather than listing nothing`() {
        val details = GroupWithDetails(
            group = GroupEntity(id = 1, name = "Flat", emoji = "🏠", description = ""),
            members = emptyList(),
            totalExpenses = 0.0, myPaidAmount = 0.0, myShare = 0.0, netBalanceForMe = 0.0,
            transactionsCount = 0,
        )
        val block = AiContext.tripReview(details, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), zone)

        assertTrue(block.contains("nobody. Everyone is square."))
    }
}
