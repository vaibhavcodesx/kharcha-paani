package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Statement dates as Gemini returns them, ISO or not (correctness audit, 2026-10-24). */
class LocalStatementParserDateTest {

    private fun shown(raw: String) = LocalStatementParser.parseDateToTimestamp(raw).second

    @Test
    fun `an ISO date reads as itself`() {
        assertEquals("05 Oct 2026", shown("2026-10-05"))
        assertEquals("05 Oct 2026", shown("2026-10-05T14:30:00"))
    }

    @Test
    fun `a day-first date isn't rolled into another century`() {
        // Leniently, year-first parsing took this as year 5, day 2026 and landed in 2011.
        assertEquals("05 Oct 2026", shown("05-10-2026"))
        assertEquals("05 Oct 2026", shown("05/10/2026"))
    }

    @Test
    fun `a two-digit year is this century`() {
        assertEquals("05 Oct 2026", shown("05-10-26"))
    }

    @Test
    fun `a month name reads either way round`() {
        assertEquals("05 Oct 2026", shown("05 Oct 2026"))
        assertEquals("05 Oct 2026", shown("05-Oct-2026"))
    }
}
