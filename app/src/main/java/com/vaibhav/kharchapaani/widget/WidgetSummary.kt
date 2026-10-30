package com.vaibhav.kharchapaani.widget

import android.content.Context
import com.vaibhav.kharchapaani.R
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

        fun of(context: Context, monthSpent: Double, monthIncome: Double, netOwedToMe: Double, todaySpent: Double = 0.0) = WidgetSummary(
            spent = Money.format(monthSpent),
            income = Money.format(monthIncome),
            today = Money.format(todaySpent),
            balance = when {
                netOwedToMe >= 0.005 -> context.getString(R.string.widget_copy_owed, Money.format(netOwedToMe))
                netOwedToMe <= -0.005 -> context.getString(R.string.widget_copy_owe, Money.format(abs(netOwedToMe)))
                else -> context.getString(R.string.widget_copy_square)
            },
            // Coral for "you owe", as Home and Splits colour it; off-white read as neutral here.
            balanceColor = when {
                netOwedToMe >= 0.005 -> LIME
                netOwedToMe <= -0.005 -> MONEY_OUT
                else -> MUTED
            },
            hint = context.getString(R.string.widget_copy_prompt),
            signedIn = true,
        )

        // Signed out: never show a previous account's numbers on the home screen.
        fun signedOut(context: Context) = WidgetSummary(
            spent = "—",
            income = "—",
            today = "—",
            balance = context.getString(R.string.widget_copy_signin),
            balanceColor = MUTED,
            hint = context.getString(R.string.widget_copy_signin_hint),
            signedIn = false,
        )
    }
}
