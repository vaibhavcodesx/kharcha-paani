package com.vaibhav.kharchapaani.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A UPI payment request in the NPCI deep-link format (`upi://pay?pa=…&pn=…&cu=INR`), which GPay,
 * PhonePe, Paytm and BHIM open, and which the same string as a QR code scans into.
 */
object UpiLink {

    /** `name@bank`: the handle is 2-256 of letters, digits, dot, dash, underscore; the bank starts with a letter. */
    private val UPI_ID = Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$")

    /** The ID as it should be saved (trimmed; UPI IDs aren't case-sensitive, so lowercased), or null. */
    fun cleanId(raw: String): String? = raw.trim().lowercase().takeIf { UPI_ID.matches(it) }

    fun build(upiId: String, payeeName: String, amount: Double? = null, note: String? = null): String {
        val params = buildList {
            add("pa" to upiId)
            add("pn" to payeeName.trim())
            if (amount != null && amount > 0) add("am" to BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString())
            add("cu" to "INR")
            note?.trim()?.takeIf { it.isNotEmpty() }?.let { add("tn" to it.take(MAX_NOTE)) }
        }
        return "upi://pay?" + params.joinToString("&") { (key, value) -> "$key=${encode(value)}" }
    }

    // RFC 3986 percent-encoding: spaces as %20, not URLEncoder's '+', which some UPI apps show literally.
    private fun encode(value: String): String = buildString {
        value.toByteArray(Charsets.UTF_8).forEach { byte ->
            val c = byte.toInt() and 0xFF
            if (c.toChar().isLetterOrDigit() && c < 128 || c.toChar() in "-._~" || c.toChar() == '@') {
                append(c.toChar())
            } else {
                append('%').append("%02X".format(c))
            }
        }
    }

    private const val MAX_NOTE = 50
}

/** Where friends pay this account: its UPI ID and the name UPI apps show. Kept only on this phone. */
data class UpiPayee(val upiId: String, val name: String)
