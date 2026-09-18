package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatementPdfTest {

    private fun pdf(extra: String = "") =
        ("%PDF-1.7\n1 0 obj\n<< /Type /Catalog >>\nendobj\n$extra\ntrailer\n<< /Root 1 0 R >>\n%%EOF")
            .toByteArray(Charsets.US_ASCII)

    @Test
    fun `a plain PDF is worth sending`() {
        assertNull(StatementPdf.precheck(pdf()))
    }

    @Test
    fun `an empty pick isn't a PDF`() {
        assertEquals(StatementReadFailure.NotAPdf, StatementPdf.precheck(ByteArray(0)))
    }

    @Test
    fun `a CSV picked by mistake isn't a PDF`() {
        assertEquals(
            StatementReadFailure.NotAPdf,
            StatementPdf.precheck("Date,Amount\n2024-08-14,250".toByteArray()),
        )
    }

    @Test
    fun `a header after a little leading junk still counts`() {
        // Some exporters leave a few bytes in front of the header; the file still opens.
        assertTrue(StatementPdf.hasPdfHeader(("\n\n\n" + String(pdf(), Charsets.US_ASCII)).toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `a header buried past the first kilobyte does not count`() {
        assertFalse(StatementPdf.hasPdfHeader(("x".repeat(2000) + "%PDF-1.7").toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `an encrypted statement is caught before it is sent`() {
        // Most Indian bank statements arrive locked; Gemini can't open one, and the old code spent
        // a full round trip finding that out and then blamed the person's connection.
        assertEquals(
            StatementReadFailure.PasswordProtected,
            StatementPdf.precheck(pdf("<< /Filter /Standard /V 2 /Encrypt 9 0 R >>")),
        )
    }

    @Test
    fun `a statement over the inline limit is caught before it is sent`() {
        val huge = ByteArray(StatementPdf.MAX_BYTES + 1)
        "%PDF-1.7".toByteArray(Charsets.US_ASCII).copyInto(huge)

        assertEquals(StatementReadFailure.TooLarge, StatementPdf.precheck(huge))
    }

    @Test
    fun `the inline limit leaves room for base64 inside a 20 MB request`() {
        // Base64 inflates by 4/3, and the prompt and JSON envelope ride along in the same request.
        assertTrue(StatementPdf.MAX_BYTES * 4.0 / 3.0 < 20 * 1024 * 1024)
    }
}
