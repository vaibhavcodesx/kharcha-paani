package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
