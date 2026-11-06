package com.vaibhav.kharchapaani.domain

/**
 * The longest text the server accepts, per field (plan T2.2, migration 20261106000100_text_limits).
 * The app caps typing at these and trims anything filled in for it (AI, statements), so a long note
 * can never become an expense that refuses to sync.
 */
object TextLimits {
    const val MERCHANT = 120
    const val NOTE = 1000
    const val CATEGORY = 60
    const val PAYMENT_METHOD = 60
    const val DISPLAY_NAME = 60
    const val GROUP_DESCRIPTION = 500
    const val SETTLEMENT_NOTE = 500

    fun merchant(text: String) = text.take(MERCHANT)
    fun note(text: String) = text.take(NOTE)
    fun category(text: String) = text.take(CATEGORY)
    fun paymentMethod(text: String) = text.take(PAYMENT_METHOD)
}
