package com.vaibhav.kharchapaani.domain

/** The kind of thing a payment method is, which is what decides its icon. */
enum class PaymentKind { Upi, Card, Cash, Bank, Other }

/**
 * Reading a payment method well enough to draw it.
 *
 * On a list where nearly every row says "PopUPI", the words are the same on every line and the
 * eye has to read them all to find the one that differs. An icon is told apart without reading,
 * so the label can shrink to a tag beside it.
 *
 * Matching is on what the name contains rather than on a fixed list, because a person can add
 * their own payment methods and "Amex Card" or "Cash (office)" should still land somewhere sensible.
 */
object PaymentMethods {

    fun kindOf(method: String): PaymentKind {
        val name = method.trim().lowercase()
        return when {
            name.isEmpty() -> PaymentKind.Other
            UPI_NAMES.any { it in name } -> PaymentKind.Upi
            "card" in name || "visa" in name || "amex" in name || "rupay" in name -> PaymentKind.Card
            "cash" in name -> PaymentKind.Cash
            "bank" in name || "neft" in name || "imps" in name || "rtgs" in name -> PaymentKind.Bank
            else -> PaymentKind.Other
        }
    }

    /**
     * The pill's text: short enough to sit beside an icon without pushing the merchant name out.
     *
     * "Credit Card" and "Debit Card" both become "CREDIT" and "DEBIT" — the card part is already
     * the icon, so repeating it in the label is the reading load this was meant to remove.
     */
    fun shortLabel(method: String): String {
        val name = method.trim()
        if (name.isEmpty()) return ""
        val trimmed = when {
            name.equals("Bank Transfer", ignoreCase = true) -> "Bank"
            name.endsWith(" Card", ignoreCase = true) -> name.dropLast(5)
            else -> name
        }
        return trimmed.uppercase()
    }

    private val UPI_NAMES = listOf("upi", "gpay", "google pay", "phonepe", "paytm", "bhim", "cred")
}
