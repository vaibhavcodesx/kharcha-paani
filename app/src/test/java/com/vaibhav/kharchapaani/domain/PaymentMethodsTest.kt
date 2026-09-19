package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMethodsTest {

    @Test
    fun `every payment method the app ships with lands on a real icon`() {
        // "Other" is the one that deliberately has none.
        val defaults = Categories.defaultPaymentMethods - Categories.OTHER

        for (method in defaults) {
            assertEquals("$method has no icon", false, PaymentMethods.kindOf(method) == PaymentKind.Other)
        }
    }

    @Test
    fun `the UPI apps people actually use are all recognised as UPI`() {
        for (name in listOf("PopUPI", "GPay", "Google Pay", "PhonePe", "Paytm", "BHIM UPI", "CRED")) {
            assertEquals(name, PaymentKind.Upi, PaymentMethods.kindOf(name))
        }
    }

    @Test
    fun `a payment method somebody made up still lands somewhere sensible`() {
        // Categories are user-extendable, so matching is on what a name contains rather than on a
        // fixed list — "Amex Card" is a card even though nothing ships called that.
        assertEquals(PaymentKind.Card, PaymentMethods.kindOf("Amex Card"))
        assertEquals(PaymentKind.Card, PaymentMethods.kindOf("HDFC rupay"))
        assertEquals(PaymentKind.Cash, PaymentMethods.kindOf("Cash (office)"))
        assertEquals(PaymentKind.Bank, PaymentMethods.kindOf("NEFT"))
        assertEquals(PaymentKind.Upi, PaymentMethods.kindOf("My UPI"))
    }

    @Test
    fun `something genuinely unclassifiable gets no icon rather than a wrong one`() {
        assertEquals(PaymentKind.Other, PaymentMethods.kindOf("Voucher"))
        assertEquals(PaymentKind.Other, PaymentMethods.kindOf(""))
    }

    @Test
    fun `the label drops what the icon already says`() {
        assertEquals("CREDIT", PaymentMethods.shortLabel("Credit Card"))
        assertEquals("DEBIT", PaymentMethods.shortLabel("Debit Card"))
        assertEquals("BANK", PaymentMethods.shortLabel("Bank Transfer"))
        assertEquals("POPUPI", PaymentMethods.shortLabel("PopUPI"))
    }

    @Test
    fun `no label is long enough to push the merchant name off the row`() {
        for (method in Categories.defaultPaymentMethods) {
            assertEquals("$method is too long for the pill", true, PaymentMethods.shortLabel(method).length <= 8)
        }
    }
}
