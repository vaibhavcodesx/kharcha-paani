package com.vaibhav.kharchapaani.widget

import com.vaibhav.kharchapaani.ui.format.Money
import kotlin.math.abs

/** What the home-screen widgets say, in the app's own words and formats. */
data class WidgetSummary(
    val spent: String,
    val income: String,
    /** My share of what went out today: the figure that says whether today has been logged yet. */
    val today: String,
    val balance: String,
    /** ARGB: lime when friends owe you, coral when you owe, grey when square or signed out. */
    val balanceColor: Int,
    /** The quick-add bar's prompt; signed out it says what to do instead. */
    val hint: String,
    val signedIn: Boolean,
) {
    companion object {
        // The app's tokens (ui/theme/Color.kt): ChartreuseSecondary, MoneyOut, MutedWarmGray.
        const val LIME = 0xFFC6F24C.toInt()
        const val MONEY_OUT = 0xFFFF6F61.toInt()
        const val MUTED = 0xFFA09A8F.toInt()

        const val PROMPT = "What did you spend on?"

        fun of(monthSpent: Double, monthIncome: Double, netOwedToMe: Double, todaySpent: Double = 0.0) = WidgetSummary(
            spent = Money.format(monthSpent),
            income = Money.format(monthIncome),
            today = Money.format(todaySpent),
            balance = when {
                netOwedToMe >= 0.005 -> "Owed ${Money.format(netOwedToMe)}"
                netOwedToMe <= -0.005 -> "You owe ${Money.format(abs(netOwedToMe))}"
                else -> "All square"
            },
            // Coral for "you owe", as Home and Splits colour it; off-white read as neutral here.
            balanceColor = when {
                netOwedToMe >= 0.005 -> LIME
                netOwedToMe <= -0.005 -> MONEY_OUT
                else -> MUTED
            },
            hint = PROMPT,
            signedIn = true,
        )

        // Signed out: never show a previous account's numbers on the home screen.
        fun signedOut() = WidgetSummary(
            spent = "—",
            income = "—",
            today = "—",
            balance = "Sign in",
            balanceColor = MUTED,
            hint = "Sign in to start logging",
            signedIn = false,
        )
    }
}
