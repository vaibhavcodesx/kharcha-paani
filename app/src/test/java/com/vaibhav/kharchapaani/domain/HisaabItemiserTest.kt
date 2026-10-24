package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The lines of an ask-to-pay. Whatever the history, the lines (with the carried-over and earlier
 * figures) must add up to what the friend owes, or the message asks for a total its own lines
 * contradict.
 */
class HisaabItemiserTest {

    private var seq = 0

    private fun expense(net: Double, date: Long, title: String = "Dinner") =
        PersonLedgerItem("split_${++seq}", date, title, "", kotlin.math.abs(net), net, isSettlement = false)

    private fun guest(net: Double, date: Long) =
        PersonLedgerItem("guest_${++seq}", date, "Shack", "", net, net, isSettlement = false)

    private fun settlement(net: Double, date: Long) =
        PersonLedgerItem("settlement_${++seq}", date, if (net > 0) "You paid them" else "They paid you", "", kotlin.math.abs(net), net, isSettlement = true)

    private fun sum(h: HisaabItemiser.Hisaab) = roundPaisa(h.carriedOver + h.earlier + h.lines.sumOf { it.amount })

    @Test
    fun `from a clean start every line is listed and they add up to what's owed`() {
        val rows = listOf(expense(300.0, 1), expense(40.0, 2, "Chai"), settlement(-100.0, 3))

        val h = HisaabItemiser.itemise(rows)!!

        assertEquals(240.0, h.total, 0.0)
        assertEquals(0.0, h.carriedOver, 0.0)
        assertEquals(listOf(300.0, 40.0, -100.0), h.lines.map { it.amount })
        assertEquals(listOf(HisaabItemiser.Kind.THEIR_SHARE, HisaabItemiser.Kind.THEIR_SHARE, HisaabItemiser.Kind.THEY_SENT), h.lines.map { it.kind })
        assertEquals(h.total, sum(h), 0.0)
    }

    @Test
    fun `only the stretch since they were last square is listed`() {
        val rows = listOf(expense(500.0, 1), settlement(-500.0, 2), expense(120.0, 3, "Auto"))

        val h = HisaabItemiser.itemise(rows)!!

        assertEquals(listOf("Auto"), h.lines.map { it.title })
        assertEquals(120.0, h.total, 0.0)
        assertEquals(2L, HisaabItemiser.lastZeroAt(rows))
    }

    @Test
    fun `when you owed them first, what you owed is carried over and still adds up`() {
        // They paid; my share was 50. Then I paid 300 for them.
        val rows = listOf(expense(-50.0, 1, "Lunch"), expense(300.0, 2))

        val h = HisaabItemiser.itemise(rows)!!

        assertEquals(-50.0, h.carriedOver, 0.0)
        assertEquals(listOf(300.0), h.lines.map { it.amount })
        assertEquals(250.0, h.total, 0.0)
        assertEquals(h.total, sum(h), 0.0)
    }

    @Test
    fun `money you sent them adds to what they owe, money they sent comes off`() {
        val rows = listOf(expense(200.0, 1), settlement(500.0, 2), settlement(-100.0, 3))

        val h = HisaabItemiser.itemise(rows)!!

        assertEquals(listOf(HisaabItemiser.Kind.THEIR_SHARE, HisaabItemiser.Kind.I_SENT, HisaabItemiser.Kind.THEY_SENT), h.lines.map { it.kind })
        assertEquals(600.0, h.total, 0.0)
        assertEquals(h.total, sum(h), 0.0)
    }

    @Test
    fun `a payment waiting to be confirmed isn't a line`() {
        val pending = PersonLedgerItem("settlement_99", 2, "They paid you", "Settled up · waiting to be confirmed", 300.0, 0.0, isSettlement = true)

        val h = HisaabItemiser.itemise(listOf(expense(300.0, 1), pending))!!

        assertEquals(1, h.lines.size)
        assertEquals(300.0, h.total, 0.0)
    }

    @Test
    fun `guest shares are lines of their own`() {
        val h = HisaabItemiser.itemise(listOf(guest(120.0, 1), expense(80.0, 2)))!!

        assertEquals(HisaabItemiser.Kind.AS_GUEST, h.lines.first().kind)
        assertEquals(200.0, h.total, 0.0)
    }

    @Test
    fun `a long history folds its oldest lines into one Earlier line, keeping the total`() {
        val rows = (1..12).map { expense(10.0 * it, it.toLong()) }

        val h = HisaabItemiser.itemise(rows)!!

        assertEquals(HisaabItemiser.MAX_LINES - 1, h.lines.size)
        assertEquals((1..5).sumOf { 10.0 * it }, h.earlier, 0.0)
        assertEquals(780.0, h.total, 0.0)
        assertEquals(h.total, sum(h), 0.0)
    }

    @Test
    fun `shares that don't divide evenly still add up to the paisa`() {
        val rows = listOf(expense(33.34, 1), expense(33.33, 2), expense(33.33, 3))

        val h = HisaabItemiser.itemise(rows)!!

        assertEquals(100.0, h.total, 0.0)
        assertEquals(h.total, sum(h), 0.0)
    }

    @Test
    fun `nothing to ask when they don't owe you`() {
        assertNull(HisaabItemiser.itemise(emptyList()))
        assertNull(HisaabItemiser.itemise(listOf(expense(100.0, 1), settlement(-100.0, 2))))
        assertNull(HisaabItemiser.itemise(listOf(expense(-80.0, 1))))
    }

    @Test
    fun `rows on the same date keep a stable order`() {
        val rows = listOf(expense(50.0, 5, "B"), expense(-50.0, 5, "A"))

        // split_1 (B, +50) before split_2 (A, -50) by id: square after both, so nothing owed.
        assertNull(HisaabItemiser.itemise(rows))
    }
}
