package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpiLinkTest {

    @Test
    fun `real UPI ID shapes are accepted and tidied`() {
        assertEquals("vaibhav@okaxis", UpiLink.cleanId("  Vaibhav@OKAXIS "))
        assertEquals("98765.43210@ybl", UpiLink.cleanId("98765.43210@ybl"))
        assertEquals("priya_s-1@paytm", UpiLink.cleanId("priya_s-1@paytm"))
    }

    @Test
    fun `things that aren't UPI IDs are refused`() {
        listOf("", "vaibhav", "@okaxis", "vaibhav@", "v@okaxis", "vaibhav@ok axis", "vai bhav@okaxis", "vaibhav@1bank", "a@b@c").forEach {
            assertNull("accepted: $it", UpiLink.cleanId(it))
        }
    }

    @Test
    fun `a request to be paid names the payee and currency`() {
        assertEquals("upi://pay?pa=vaibhav@okaxis&pn=Vaibhav%20Chauhan&cu=INR", UpiLink.build("vaibhav@okaxis", " Vaibhav Chauhan "))
    }

    @Test
    fun `an amount is sent with two decimals, never in exponent form`() {
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=1250.50&cu=INR", UpiLink.build("v@okaxis", "V", amount = 1250.5))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=10000000.00&cu=INR", UpiLink.build("v@okaxis", "V", amount = 10_000_000.0))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&cu=INR", UpiLink.build("v@okaxis", "V", amount = 0.0))
    }

    @Test
    fun `names and notes with symbols are percent-encoded`() {
        assertEquals(
            "upi://pay?pa=v@okaxis&pn=Ria%20%26%20Co&am=90.00&cu=INR&tn=Goa%20trip%20%E2%80%94%20dinner",
            UpiLink.build("v@okaxis", "Ria & Co", amount = 90.0, note = "Goa trip — dinner"),
        )
    }

    @Test
    fun `encoding leaves unreserved characters and at symbol untouched`() {
        val link = UpiLink.build("user@bank", "Alpha-Beta_1.0~v2@node")
        assertTrue(link.contains("pn=Alpha-Beta_1.0~v2@node"))
    }

    @Test
    fun `spaces are percent twenty and never plus signs`() {
        val link = UpiLink.build("v@okaxis", "First Middle Last", amount = 100.0, note = "Coffee and samosa")
        assertTrue(link.contains("pn=First%20Middle%20Last"))
        assertTrue(link.contains("tn=Coffee%20and%20samosa"))
        assertFalse(link.contains("+"))
    }

    @Test
    fun `reserved URI characters are strictly percent-encoded`() {
        val link = UpiLink.build("v@okaxis", "A & B = C ? D # E / F % G + H $ I", note = "item: 1, 2; 3")
        assertTrue(link.contains("%26")) // &
        assertTrue(link.contains("%3D")) // =
        assertTrue(link.contains("%3F")) // ?
        assertTrue(link.contains("%23")) // #
        assertTrue(link.contains("%2F")) // /
        assertTrue(link.contains("%25")) // %
        assertTrue(link.contains("%2B")) // +
        assertTrue(link.contains("%24")) // $
        assertTrue(link.contains("%3A")) // :
        assertTrue(link.contains("%2C")) // ,
        assertTrue(link.contains("%3B")) // ;
    }

    @Test
    fun `multibyte unicode characters, currency symbols, and emojis are utf8 percent-encoded`() {
        val link = UpiLink.build("v@okaxis", "राहुल शर्मा", amount = 250.0, note = "चाय ₹500 🍕")
        // Check rupee symbol %E2%82%B9
        assertTrue("Must encode ₹ as %E2%82%B9", link.contains("%E2%82%B9"))
        // Check pizza emoji %F0%9F%8D%95
        assertTrue("Must encode 🍕 emoji as %F0%9F%8D%95", link.contains("%F0%9F%8D%95"))
        // Check Devanagari text
        assertTrue("Must encode Hindi payee name", link.contains("%E0%A4%B0"))
    }

    @Test
    fun `note truncation respects MAX_NOTE of 50 characters`() {
        val exact50 = "12345678901234567890123456789012345678901234567890"
        val linkExact = UpiLink.build("v@okaxis", "V", note = exact50)
        assertTrue(linkExact.contains("tn=$exact50"))

        val over50 = exact50 + "EXTRA1234567890"
        val linkTruncated = UpiLink.build("v@okaxis", "V", note = over50)
        assertTrue(linkTruncated.contains("tn=$exact50"))
        assertFalse(linkTruncated.contains("EXTRA"))
    }

    @Test
    fun `blank or empty notes are completely omitted from the query string`() {
        val linkEmpty = UpiLink.build("v@okaxis", "V", note = "")
        assertFalse(linkEmpty.contains("tn="))

        val linkBlank = UpiLink.build("v@okaxis", "V", note = "   ")
        assertFalse(linkBlank.contains("tn="))

        val linkNull = UpiLink.build("v@okaxis", "V", note = null)
        assertFalse(linkNull.contains("tn="))
    }

    @Test
    fun `amounts round half up to two decimal places`() {
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=0.01&cu=INR", UpiLink.build("v@okaxis", "V", amount = 0.01))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=0.10&cu=INR", UpiLink.build("v@okaxis", "V", amount = 0.1))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=1.00&cu=INR", UpiLink.build("v@okaxis", "V", amount = 1.0))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=99.99&cu=INR", UpiLink.build("v@okaxis", "V", amount = 99.99))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=10.55&cu=INR", UpiLink.build("v@okaxis", "V", amount = 10.554))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=10.56&cu=INR", UpiLink.build("v@okaxis", "V", amount = 10.555))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=10.56&cu=INR", UpiLink.build("v@okaxis", "V", amount = 10.556))
        assertEquals("upi://pay?pa=v@okaxis&pn=V&am=1234567.89&cu=INR", UpiLink.build("v@okaxis", "V", amount = 1234567.89))
    }

    @Test
    fun `zero, negative, and null amounts omit the am parameter`() {
        val linkNull = UpiLink.build("v@okaxis", "V", amount = null)
        assertFalse(linkNull.contains("am="))

        val linkZero = UpiLink.build("v@okaxis", "V", amount = 0.0)
        assertFalse(linkZero.contains("am="))

        val linkNegZero = UpiLink.build("v@okaxis", "V", amount = -0.0)
        assertFalse(linkNegZero.contains("am="))

        val linkNegative = UpiLink.build("v@okaxis", "V", amount = -250.0)
        assertFalse(linkNegative.contains("am="))

        val linkSmallNegative = UpiLink.build("v@okaxis", "V", amount = -0.01)
        assertFalse(linkSmallNegative.contains("am="))
    }

    @Test
    fun `buildOrNull builds valid links and refuses invalid IDs`() {
        val valid = UpiLink.buildOrNull(" vaibhav@okaxis ", "Vaibhav", 500.0)
        assertEquals("upi://pay?pa=vaibhav@okaxis&pn=Vaibhav&am=500.00&cu=INR", valid)

        val invalid = UpiLink.buildOrNull("not-a-valid-vpa", "Vaibhav", 500.0)
        assertNull(invalid)

        val injection = UpiLink.buildOrNull("vaibhav@okaxis&am=99999", "Vaibhav", 500.0)
        assertNull(injection)
    }

}
