package com.vaibhav.kharchapaani.widget

import android.content.Context
import com.vaibhav.kharchapaani.data.settings.BackgroundLedger
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.domain.DailySpend
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * The figures both widgets show, from the same code the app uses (Home's month spend and net
 * balance, Insights' per-day rule for today) rather than a second copy of the ledger maths that
 * could drift from it.
 */
internal object WidgetData {

    suspend fun load(context: Context): WidgetSummary = withSummary(context) { it }

    /** Keep rendering/publication inside the same operation as its financial reads. */
    suspend fun <T> withSummary(context: Context, consume: suspend (WidgetSummary) -> T): T {
        val ledger = BackgroundLedger.current(context) ?: return consume(WidgetSummary.signedOut(context))
        return ledger.withDatabase(context) { database ->
            val repository = LedgerRepository(database)
            val analytics = repository.monthlyAnalytics.first()
            val netOwedToMe = repository.personBalances.first().sumOf { it.netBalance }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val todaySpent = DailySpend.of(repository.fullTransactions.first(), YearMonth.from(today), today, zone)
                .firstOrNull { it.date == today }?.amount ?: 0.0
            consume(WidgetSummary.of(context, analytics.totalExpense, analytics.totalIncome, netOwedToMe, todaySpent))
        }
    }
}
