package com.vaibhav.kharchapaani.ui.format

import com.vaibhav.kharchapaani.domain.HisaabItemiser
import com.vaibhav.kharchapaani.domain.HisaabItemiser.Kind
import androidx.room.Room
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneOffset
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import com.vaibhav.kharchapaani.domain.roundPaisa
import kotlin.math.abs
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AskMessageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val zone = ZoneOffset.UTC
    private fun on(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(zone).toInstant().toEpochMilli()

    private val hisaab = HisaabItemiser.Hisaab(
        carriedOver = 0.0,
        earlier = 0.0,
        lines = listOf(
            HisaabItemiser.Line("Dinner", Kind.THEIR_SHARE, on(4), 300.0),
            HisaabItemiser.Line("Auto", Kind.MY_SHARE, on(5), -45.0),
            HisaabItemiser.Line("They paid you", Kind.THEY_SENT, on(6), -100.0),
            HisaabItemiser.Line("Shack", Kind.AS_GUEST, on(7), 120.0),
        ),
        total = 275.0,
    )

    @Test
    fun `the whole balance, line by line, with your UPI ID`() {
        val message = AskMessage.build("Aaru Sharma", hisaab, asked = 275.0, upiId = "lal@okaxis", zone = zone)

        assertEquals(
            """
            Hey Aaru, here's our hisaab:
            • Dinner, 4 Oct: your share ₹300
            • Auto, 5 Oct: my share of what you paid -₹45
            • You sent me, 6 Oct: -₹100
            • Shack, 7 Oct: your share as my guest ₹120
            Total: ₹275
            UPI: lal@okaxis
            Sent from Kharcha Paani
            """.trimIndent(),
            message,
        )
    }

    @Test
    fun `asking for part of it says how much of the total`() {
        val message = AskMessage.build("Aaru", hisaab, asked = 200.0, upiId = null, zone = zone)

        assertTrue(message.contains("Total: ₹275\nAsking ₹200 of the ₹275 for now.\n"))
        assertFalse("no UPI ID, no UPI line", message.contains("UPI:"))
    }

    @Test
    fun `a partial ask from the real ledger stays friendly and keeps the full balance`() = runTest {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repository = LedgerRepository(db, selfProfileId = "me")
            val friend = repository.addPerson("Rohan", "#70C1FF")
            repository.saveTransactionWithSplits(
                TransactionEntity(type = "expense", amount = 551.0, category = "Food",
                    merchant = "Dinner", paymentMethod = "UPI", date = on(4)),
                paidByPersonId = -1L,
                shares = listOf(
                    SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 275.50),
                    SplitShareEntity(splitId = 0, personId = friend, shareAmount = 275.50),
                ),
                splitType = "equal",
            )
            val actualHisaab = requireNotNull(HisaabItemiser.itemise(repository.getLedgerForPerson(friend, context).first()))
            val message = AskMessage.build("Rohan", actualHisaab, asked = 125.50, upiId = null, zone = zone)

            assertTrue(message.contains("Total: ₹275.50\nAsking ₹125.50 of the ₹275.50 for now.\n"))
            assertFalse("the outgoing message must not put pressure on a friend", message.contains("you owe", ignoreCase = true))
            assertEquals(275.50, requireNotNull(HisaabItemiser.itemise(repository.getLedgerForPerson(friend, context).first())).total, 0.0)
        } finally {
            db.close()
        }
    }

    @Test
    fun `carried-over and earlier amounts come first`() {
        val folded = hisaab.copy(carriedOver = -50.0, earlier = 400.0, total = 625.0)

        val message = AskMessage.build("Aaru", folded, asked = 625.0, upiId = null, zone = zone)

        assertTrue(message.contains("hisaab:\n• Carried over: -₹50\n• Earlier: ₹400\n• Dinner"))
    }

    @Test
    fun `the asked line counts calendar days`() {
        val now = on(10) + 9 * 3_600_000L
        assertEquals("Asked today", AskMessage.askedLabel(context, on(10), now, zone))
        assertEquals("Asked yesterday", AskMessage.askedLabel(context, on(9) + 23 * 3_600_000L, now, zone))
        assertEquals("Asked 3 days ago", AskMessage.askedLabel(context, on(7), now, zone))
    }

    // --- T11 Helpers for Invariant & Tone Verification ---

    private val bulletAmountRegex = Regex("""(-?₹[0-9,]+(?:\.[0-9]{2})?)$""")

    private fun parseFormattedRupees(formatted: String): Double {
        val isNegative = formatted.startsWith("-")
        val clean = formatted.removePrefix("-").removePrefix("₹").replace(",", "").trim()
        val value = clean.toDouble()
        return if (isNegative) -value else value
    }

    private fun extractBulletAmounts(message: String): List<Double> =
        message.lines()
            .filter { it.startsWith("• ") }
            .map { line ->
                val match = bulletAmountRegex.find(line.trim())
                    ?: throw AssertionError("Could not extract amount from bullet line: $line")
                parseFormattedRupees(match.value)
            }

    private fun extractTotalAmount(message: String): Double {
        val totalLine = message.lines().firstOrNull { it.startsWith("Total: ") }
            ?: throw AssertionError("Message has no Total line:\n$message")
        val match = bulletAmountRegex.find(totalLine.trim())
            ?: throw AssertionError("Could not extract amount from total line: $totalLine")
        return parseFormattedRupees(match.value)
    }

    private fun assertToneSafe(message: String) {
        val banned = listOf("you owe", "overdue", "pay now")
        for (phrase in banned) {
            assertFalse(
                "Outgoing ask message must never contain banned phrase '$phrase':\n$message",
                message.contains(phrase, ignoreCase = true)
            )
        }
    }

    private fun assertLinesAddUpToTotal(
        hisaab: HisaabItemiser.Hisaab,
        friendName: String = "Rohan",
        asked: Double = hisaab.total,
        upiId: String? = "rohan@okhdfcbank"
    ) {
        val message = AskMessage.build(friendName, hisaab, asked = asked, upiId = upiId, zone = zone)

        // 1. Data model invariant: carriedOver + earlier + sum(lines) == total
        val computedSum = roundPaisa(hisaab.carriedOver + hisaab.earlier + hisaab.lines.sumOf { it.amount })
        assertEquals("Data model sum must equal hisaab.total", roundPaisa(hisaab.total), computedSum, 0.0)

        // 2. Extract bullet lines from the formatted message
        val bulletAmounts = extractBulletAmounts(message)
        val extractedTotal = extractTotalAmount(message)

        // 3. Expected count of bullet lines
        var expectedBullets = hisaab.lines.size
        if (hisaab.carriedOver <= -0.005) expectedBullets++
        if (abs(hisaab.earlier) >= 0.005) expectedBullets++
        assertEquals("Number of bullet lines in message must match hisaab entries", expectedBullets, bulletAmounts.size)

        // 4. Formatted text invariant: sum of line amounts equals formatted total
        val formattedLinesSum = roundPaisa(bulletAmounts.sum())
        assertEquals(
            "Sum of extracted bullet amounts ($formattedLinesSum) must equal extracted total ($extractedTotal) in message:\n$message",
            extractedTotal,
            formattedLinesSum,
            0.0
        )

        // 5. Formatted total matches hisaab.total exactly (no paisa lost or gained)
        assertEquals(
            "Extracted total must match hisaab.total",
            roundPaisa(hisaab.total),
            extractedTotal,
            0.0
        )

        // 6. Tone safety: outgoing message must never contain banned phrases
        assertToneSafe(message)
    }

    // --- T11 Test Suite ---

    @Test
    fun `lines add up to total across all five kinds in a single hisaab`() {
        val lines = listOf(
            HisaabItemiser.Line("Dinner at Smoke House", Kind.THEIR_SHARE, on(1), 300.50),
            HisaabItemiser.Line("Airport Cab", Kind.MY_SHARE, on(2), -45.25),
            HisaabItemiser.Line("Goa Beach Shack", Kind.AS_GUEST, on(3), 120.75),
            HisaabItemiser.Line("Emergency cash", Kind.I_SENT, on(4), 500.00),
            HisaabItemiser.Line("Quick settle", Kind.THEY_SENT, on(5), -175.00),
        )
        val carriedOver = -50.00
        val earlier = 125.00
        val expectedTotal = roundPaisa(carriedOver + earlier + lines.sumOf { it.amount }) // 776.00
        val fullHisaab = HisaabItemiser.Hisaab(
            carriedOver = carriedOver,
            earlier = earlier,
            lines = lines,
            total = expectedTotal,
        )

        val message = AskMessage.build("Priya Sharma", fullHisaab, asked = expectedTotal, upiId = "priya@okhdfcbank", zone = zone)

        assertTrue("THEIR_SHARE phrasing", message.contains("• Dinner at Smoke House, 1 Oct: your share ₹300.50"))
        assertTrue("MY_SHARE phrasing", message.contains("• Airport Cab, 2 Oct: my share of what you paid -₹45.25"))
        assertTrue("AS_GUEST phrasing", message.contains("• Goa Beach Shack, 3 Oct: your share as my guest ₹120.75"))
        assertTrue("I_SENT phrasing", message.contains("• I sent you, 4 Oct: ₹500"))
        assertTrue("THEY_SENT phrasing", message.contains("• You sent me, 5 Oct: -₹175"))
        assertTrue("Carried over phrasing", message.contains("• Carried over: -₹50"))
        assertTrue("Earlier phrasing", message.contains("• Earlier: ₹125"))
        assertTrue("Total line", message.contains("Total: ₹776\n"))

        assertLinesAddUpToTotal(fullHisaab, friendName = "Priya Sharma", asked = expectedTotal, upiId = "priya@okhdfcbank")
    }

    @Test
    fun `each of the five kinds individually preserves the sum invariant`() {
        // 1. THEIR_SHARE alone
        val theirShare = HisaabItemiser.Hisaab(0.0, 0.0, listOf(HisaabItemiser.Line("Lunch", Kind.THEIR_SHARE, on(1), 250.0)), 250.0)
        assertLinesAddUpToTotal(theirShare)

        // 2. AS_GUEST alone
        val asGuest = HisaabItemiser.Hisaab(0.0, 0.0, listOf(HisaabItemiser.Line("Resort entry", Kind.AS_GUEST, on(2), 350.0)), 350.0)
        assertLinesAddUpToTotal(asGuest)

        // 3. I_SENT alone
        val iSent = HisaabItemiser.Hisaab(0.0, 0.0, listOf(HisaabItemiser.Line("UPI transfer", Kind.I_SENT, on(3), 500.0)), 500.0)
        assertLinesAddUpToTotal(iSent)

        // 4. MY_SHARE offset against THEIR_SHARE
        val myShareCombined = HisaabItemiser.Hisaab(
            0.0, 0.0,
            listOf(
                HisaabItemiser.Line("Groceries", Kind.THEIR_SHARE, on(4), 400.0),
                HisaabItemiser.Line("Coffee", Kind.MY_SHARE, on(5), -80.0),
            ),
            320.0
        )
        assertLinesAddUpToTotal(myShareCombined)

        // 5. THEY_SENT offset against THEIR_SHARE
        val theySentCombined = HisaabItemiser.Hisaab(
            0.0, 0.0,
            listOf(
                HisaabItemiser.Line("Dinner", Kind.THEIR_SHARE, on(6), 600.0),
                HisaabItemiser.Line("Partial payment", Kind.THEY_SENT, on(7), -250.0),
            ),
            350.0
        )
        assertLinesAddUpToTotal(theySentCombined)
    }

    @Test
    fun `sub-cent and paisa precision ensures no paisa is gained or lost`() {
        // 1. Repeating 1/3 splits (33.34 + 33.33 + 33.33 = 100.00)
        val split3Ways = HisaabItemiser.Hisaab(
            carriedOver = 0.0,
            earlier = 0.0,
            lines = listOf(
                HisaabItemiser.Line("Meal 1", Kind.THEIR_SHARE, on(1), 33.34),
                HisaabItemiser.Line("Meal 2", Kind.THEIR_SHARE, on(2), 33.33),
                HisaabItemiser.Line("Meal 3", Kind.THEIR_SHARE, on(3), 33.33),
            ),
            total = 100.00
        )
        assertLinesAddUpToTotal(split3Ways)

        // 2. Single paisa lines (0.01)
        val singlePaisa = HisaabItemiser.Hisaab(
            carriedOver = 0.0,
            earlier = 0.0,
            lines = listOf(
                HisaabItemiser.Line("Rounding adjustment", Kind.THEIR_SHARE, on(1), 0.01),
                HisaabItemiser.Line("Guest tea", Kind.AS_GUEST, on(2), 0.02),
                HisaabItemiser.Line("Tiny refund", Kind.THEY_SENT, on(3), -0.01),
            ),
            total = 0.02
        )
        assertLinesAddUpToTotal(singlePaisa)

        // 3. Mixed decimal fractions with carried over paise
        val mixedPaise = HisaabItemiser.Hisaab(
            carriedOver = -10.35,
            earlier = 5.25,
            lines = listOf(
                HisaabItemiser.Line("Item A", Kind.THEIR_SHARE, on(4), 19.99),
                HisaabItemiser.Line("Item B", Kind.MY_SHARE, on(5), -4.89),
            ),
            total = roundPaisa(-10.35 + 5.25 + 19.99 - 4.89) // 10.00
        )
        assertLinesAddUpToTotal(mixedPaise)
    }

    @Test
    fun `large amounts preserve Indian digit grouping and add up accurately`() {
        val lines = listOf(
            HisaabItemiser.Line("Deposit for flat", Kind.THEIR_SHARE, on(1), 5_00_000.00),
            HisaabItemiser.Line("Advance rent share", Kind.MY_SHARE, on(2), -1_25_000.50),
            HisaabItemiser.Line("Broker fee", Kind.I_SENT, on(3), 2_50_000.25),
        )
        val carriedOver = -50_000.00
        val earlier = 1_00_000.25
        val expectedTotal = roundPaisa(carriedOver + earlier + lines.sumOf { it.amount }) // 6,75,000.00
        val hisaab = HisaabItemiser.Hisaab(
            carriedOver = carriedOver,
            earlier = earlier,
            lines = lines,
            total = expectedTotal
        )

        val message = AskMessage.build("Vikram", hisaab, asked = expectedTotal, upiId = "vikram@upi", zone = zone)
        assertTrue(message.contains("Total: ₹6,75,000\n"))
        assertTrue(message.contains("• Carried over: -₹50,000"))
        assertTrue(message.contains("• Earlier: ₹1,00,000.25"))
        assertTrue(message.contains("• Deposit for flat, 1 Oct: your share ₹5,00,000"))
        assertTrue(message.contains("• Advance rent share, 2 Oct: my share of what you paid -₹1,25,000.50"))
        assertTrue(message.contains("• Broker fee, 3 Oct: ₹2,50,000.25"))

        assertLinesAddUpToTotal(hisaab, friendName = "Vikram")
    }

    @Test
    fun `carried-over and earlier threshold boundaries are strictly respected`() {
        val baseLine = HisaabItemiser.Line("Chai", Kind.THEIR_SHARE, on(1), 50.00)

        // 1. Exactly 0.0: neither carried over nor earlier bullet appears
        val zeroExtra = HisaabItemiser.Hisaab(0.0, 0.0, listOf(baseLine), 50.00)
        val msgZero = AskMessage.build("Rohan", zeroExtra, 50.00, null, zone)
        assertFalse("No carried over line when 0.0", msgZero.contains("Carried over"))
        assertFalse("No earlier line when 0.0", msgZero.contains("Earlier"))
        assertLinesAddUpToTotal(zeroExtra)

        // 2. Just below threshold (-0.004): suppressed
        val belowThreshold = HisaabItemiser.Hisaab(-0.004, 0.004, listOf(baseLine), 50.00)
        val msgBelow = AskMessage.build("Rohan", belowThreshold, 50.00, null, zone)
        assertFalse("Suppressed when carriedOver > -0.005", msgBelow.contains("Carried over"))
        assertFalse("Suppressed when abs(earlier) < 0.005", msgBelow.contains("Earlier"))

        // 3. Just at threshold: -0.01 carriedOver, 0.01 earlier
        val atThreshold = HisaabItemiser.Hisaab(-0.01, 0.01, listOf(baseLine), 50.00)
        val msgAt = AskMessage.build("Rohan", atThreshold, 50.00, null, zone)
        assertTrue("Present when carriedOver <= -0.005", msgAt.contains("• Carried over: -₹0.01"))
        assertTrue("Present when earlier >= 0.005", msgAt.contains("• Earlier: ₹0.01"))
        assertLinesAddUpToTotal(atThreshold)

        // 4. Negative earlier (when friend overpaid in folded history)
        val negativeEarlier = HisaabItemiser.Hisaab(0.0, -25.50, listOf(HisaabItemiser.Line("Dinner", Kind.THEIR_SHARE, on(2), 100.0)), 74.50)
        val msgNegEarlier = AskMessage.build("Rohan", negativeEarlier, 74.50, null, zone)
        assertTrue("Negative earlier formats with minus sign", msgNegEarlier.contains("• Earlier: -₹25.50"))
        assertLinesAddUpToTotal(negativeEarlier)
    }

    @Test
    fun `partial ask keeps lines adding up to full total and never says you owe`() {
        val hisaab = HisaabItemiser.Hisaab(
            carriedOver = -20.0,
            earlier = 100.0,
            lines = listOf(
                HisaabItemiser.Line("Weekend trip stay", Kind.THEIR_SHARE, on(1), 1200.0),
                HisaabItemiser.Line("Fuel paid by friend", Kind.MY_SHARE, on(2), -300.0),
                HisaabItemiser.Line("Settlement sent earlier", Kind.THEY_SENT, on(3), -180.0),
            ),
            total = 800.0,
        )

        // Asking for partial amount (₹500 out of ₹800)
        val message = AskMessage.build("Ananya", hisaab, asked = 500.0, upiId = "ananya@okaxis", zone = zone)

        assertTrue(message.contains("Total: ₹800\nAsking ₹500 of the ₹800 for now.\n"))
        assertFalse("Must not say 'you owe'", message.contains("you owe", ignoreCase = true))
        assertFalse("Must not say 'overdue'", message.contains("overdue", ignoreCase = true))
        assertFalse("Must not say 'pay now'", message.contains("pay now", ignoreCase = true))

        // Bullet lines must still sum to the FULL total (₹800), not the asked amount (₹500)
        assertLinesAddUpToTotal(hisaab, friendName = "Ananya", asked = 500.0, upiId = "ananya@okaxis")
    }

    @Test
    fun `generative property tests - 100 random hisaabs add up to total for all kinds`() {
        val rng = Random(seed = 20261103)
        val kinds = Kind.values()

        repeat(100) { iteration ->
            val lineCount = rng.nextInt(1, 12)
            val lines = (1..lineCount).map { i ->
                val kind = kinds[rng.nextInt(kinds.size)]
                val rawMagnitude = when (rng.nextInt(4)) {
                    0 -> rng.nextDouble(0.01, 0.99)
                    1 -> rng.nextDouble(1.0, 50.0)
                    2 -> rng.nextDouble(50.0, 2000.0)
                    else -> rng.nextDouble(2000.0, 25000.0)
                }
                val magnitude = roundPaisa(rawMagnitude)
                val signedAmount = when (kind) {
                    Kind.THEIR_SHARE, Kind.AS_GUEST, Kind.I_SENT -> magnitude
                    Kind.MY_SHARE, Kind.THEY_SENT -> -magnitude
                }
                HisaabItemiser.Line(
                    title = "Expense_$i",
                    kind = kind,
                    date = on(1) + i * 86_400_000L,
                    amount = signedAmount
                )
            }.toMutableList()

            val carriedOver = if (rng.nextBoolean()) -roundPaisa(rng.nextDouble(0.01, 500.0)) else 0.0
            val earlier = if (rng.nextBoolean()) {
                val sign = if (rng.nextBoolean()) 1.0 else -1.0
                sign * roundPaisa(rng.nextDouble(0.01, 500.0))
            } else 0.0

            var tentativeTotal = roundPaisa(carriedOver + earlier + lines.sumOf { it.amount })
            if (tentativeTotal <= 0.01) {
                val boost = roundPaisa(abs(tentativeTotal) + rng.nextDouble(10.0, 100.0))
                lines.add(HisaabItemiser.Line("Offset boost", Kind.THEIR_SHARE, on(25), boost))
                tentativeTotal = roundPaisa(carriedOver + earlier + lines.sumOf { it.amount })
            }

            val hisaab = HisaabItemiser.Hisaab(
                carriedOver = carriedOver,
                earlier = earlier,
                lines = lines,
                total = tentativeTotal
            )

            val asked = if (rng.nextBoolean()) tentativeTotal else roundPaisa(tentativeTotal * rng.nextDouble(0.2, 0.8))
            val upiId = if (rng.nextBoolean()) "user$iteration@okaxis" else null

            try {
                assertLinesAddUpToTotal(
                    hisaab = hisaab,
                    friendName = "Friend_$iteration",
                    asked = asked,
                    upiId = upiId
                )
            } catch (e: AssertionError) {
                throw AssertionError("Failed on iteration $iteration with hisaab=$hisaab, asked=$asked: ${e.message}", e)
            }
        }
    }

    @Test
    fun `end-to-end hisaab from ledger rows with all five kinds formats and adds up`() {
        var seq = 0
        fun row(net: Double, date: Long, title: String = "Row", idPrefix: String = "split", isSettlement: Boolean = false) =
            PersonLedgerItem(
                id = "${idPrefix}_${++seq}",
                date = date,
                title = title,
                subtitle = "",
                amount = abs(net),
                netChange = net,
                isSettlement = isSettlement
            )

        // 1. Initial negative balance where you owed friend (carried over)
        // 2. Older expenses folded into earlier (> 8 items)
        // 3. All 5 kinds in the recent history
        val rows = listOf(
            row(-50.0, 1, "Friend paid lunch"), // carried over
            row(30.0, 2, "Chai 1"),             // folded into earlier
            row(40.0, 3, "Chai 2"),             // folded into earlier
            row(50.0, 4, "Chai 3"),             // folded into earlier
            row(60.0, 5, "Chai 4"),             // folded into earlier
            row(100.0, 6, "Dinner", "split", false),                // THEIR_SHARE
            row(-35.0, 7, "Auto", "split", false),                  // MY_SHARE
            row(80.0, 8, "Concert ticket", "guest", false),         // AS_GUEST
            row(200.0, 9, "Loan back", "settlement", true),         // I_SENT
            row(-50.0, 10, "Friend UPI transfer", "settlement", true) // THEY_SENT
        )

        val hisaab = requireNotNull(HisaabItemiser.itemise(rows))
        val kindsPresent = hisaab.lines.map { it.kind }.toSet()
        assertTrue("Contains THEIR_SHARE", kindsPresent.contains(Kind.THEIR_SHARE))
        assertTrue("Contains MY_SHARE", kindsPresent.contains(Kind.MY_SHARE))
        assertTrue("Contains AS_GUEST", kindsPresent.contains(Kind.AS_GUEST))
        assertTrue("Contains I_SENT", kindsPresent.contains(Kind.I_SENT))
        assertTrue("Contains THEY_SENT", kindsPresent.contains(Kind.THEY_SENT))
        assertTrue("Carried over is negative", hisaab.carriedOver < 0)
        assertTrue("Earlier is positive", hisaab.earlier > 0)

        assertLinesAddUpToTotal(hisaab, friendName = "Rohan", asked = hisaab.total, upiId = "rohan@upi")
    }

    @Test
    fun `outgoing ask messages never contain debt-collector language across edge conditions`() {
        val bannedWords = listOf("you owe", "overdue", "pay now")
        val scenarios = listOf(
            // Single line full ask
            HisaabItemiser.Hisaab(0.0, 0.0, listOf(HisaabItemiser.Line("Coffee", Kind.THEIR_SHARE, on(1), 50.0)), 50.0) to 50.0,
            // Single line partial ask
            HisaabItemiser.Hisaab(0.0, 0.0, listOf(HisaabItemiser.Line("Dinner", Kind.THEIR_SHARE, on(1), 500.0)), 500.0) to 100.0,
            // Carried over and earlier only
            HisaabItemiser.Hisaab(-20.0, 120.0, emptyList(), 100.0) to 50.0,
            // Large amount
            HisaabItemiser.Hisaab(0.0, 0.0, listOf(HisaabItemiser.Line("Rent", Kind.THEIR_SHARE, on(1), 50_000.0)), 50_000.0) to 25_000.0,
            // Complex mix with guest and settlements
            HisaabItemiser.Hisaab(
                carriedOver = -40.0,
                earlier = 200.0,
                lines = listOf(
                    HisaabItemiser.Line("Trip", Kind.AS_GUEST, on(2), 300.0),
                    HisaabItemiser.Line("Transfer", Kind.I_SENT, on(3), 400.0),
                    HisaabItemiser.Line("Cash back", Kind.THEY_SENT, on(4), -100.0)
                ),
                total = 760.0
            ) to 300.0
        )

        for ((hisaab, asked) in scenarios) {
            val message = AskMessage.build("Aaru", hisaab, asked = asked, upiId = "aaru@okaxis", zone = zone)
            for (banned in bannedWords) {
                assertFalse(
                    "Message for hisaab ($hisaab) must not contain '$banned':\n$message",
                    message.contains(banned, ignoreCase = true)
                )
            }
        }
    }
}
