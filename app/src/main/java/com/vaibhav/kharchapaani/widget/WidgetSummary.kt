package com.vaibhav.kharchapaani.widget

import com.vaibhav.kharchapaani.ui.format.Money
import kotlin.math.abs

/** What the home-screen widget says, in the app's own words and formats. */
data class WidgetSummary(
    val spent: String,
    val income: String,
    val balance: String,
    /** ARGB: lime when friends owe you, off-white when you owe, grey when square or signed out. */
    val balanceColor: Int,
) {
    companion object {
        private const val LIME = 0xFFC6F24C.toInt()
        private const val OFF_WHITE = 0xFFF4F0E8.toInt()
        private const val MUTED = 0xFFA09A8F.toInt()

        fun of(monthSpent: Double, monthIncome: Double, netOwedToMe: Double) = WidgetSummary(
            spent = Money.format(monthSpent),
            income = Money.format(monthIncome),
            balance = when {
                netOwedToMe >= 0.005 -> "Owed ${Money.format(netOwedToMe)}"
                netOwedToMe <= -0.005 -> "You owe ${Money.format(abs(netOwedToMe))}"
                else -> "All square"
            },
            balanceColor = when {
                netOwedToMe >= 0.005 -> LIME
                netOwedToMe <= -0.005 -> OFF_WHITE
                else -> MUTED
            },
        )

        // Signed out: never show a previous account's numbers on the home screen.
        fun signedOut() = WidgetSummary(spent = "—", income = "—", balance = "Sign in", balanceColor = MUTED)
    }
}
