package com.vaibhav.kharchapaani.ui.format

import com.vaibhav.kharchapaani.domain.HisaabItemiser
import com.vaibhav.kharchapaani.domain.HisaabItemiser.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class AskMessageTest {

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

        assertTrue(message.contains("Total: ₹275\nAsking ₹200 of the ₹275 you owe.\n"))
        assertFalse("no UPI ID, no UPI line", message.contains("UPI:"))
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
        assertEquals("Asked today", AskMessage.askedLabel(on(10), now, zone))
        assertEquals("Asked yesterday", AskMessage.askedLabel(on(9) + 23 * 3_600_000L, now, zone))
        assertEquals("Asked 3 days ago", AskMessage.askedLabel(on(7), now, zone))
    }
}
