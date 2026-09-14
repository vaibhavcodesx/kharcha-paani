package com.vaibhav.kharchapaani.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vaibhav.kharchapaani.MainActivity
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.ui.format.Money
import io.github.jan.supabase.auth.auth
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SplitFlowWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        scope.launch {
            try {
                val auth = SupabaseClientProvider.get(context).auth
                auth.awaitInitialization()
                val userId = auth.currentUserOrNull()?.id
                if (userId == null) {
                    // Signed out: never show a previous account's numbers on the home screen.
                    val views = RemoteViews(context.packageName, R.layout.widget_splitflow).apply {
                        setTextViewText(R.id.widget_month_spent, "—")
                        setTextViewText(R.id.widget_month_income, "—")
                        setTextViewText(R.id.widget_net_owed_badge, "Sign in")
                        bindClickIntents(context)
                    }
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    return@launch
                }
                val db = LedgerDatabases.open(context, userId)
                // The same numbers the app shows (Home's monthly spend and net balance), from the
                // same code, instead of a second copy of the ledger math that could drift.
                val repository = LedgerRepository(db)
                val analytics = repository.monthlyAnalytics.first()
                val monthlySpent = analytics.totalExpense
                val monthlyIncome = analytics.totalIncome
                val netOwedToMe = repository.personBalances.first().sumOf { it.netBalance }

                val spentStr = Money.format(monthlySpent)
                val incomeStr = Money.format(monthlyIncome)
                val netBadgeStr = if (netOwedToMe > 0) {
                    "${Money.format(netOwedToMe, showPlus = true)} owed"
                } else if (netOwedToMe < 0) {
                    "${Money.format(-netOwedToMe)} you owe"
                } else {
                    "Balanced"
                }

                val views = RemoteViews(context.packageName, R.layout.widget_splitflow).apply {
                    setTextViewText(R.id.widget_month_spent, spentStr)
                    setTextViewText(R.id.widget_month_income, incomeStr)
                    setTextViewText(R.id.widget_net_owed_badge, netBadgeStr)

                    bindClickIntents(context)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun RemoteViews.bindClickIntents(context: Context) {
        // PendingIntent: Main Root Click
        val rootIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val rootPendingIntent = PendingIntent.getActivity(
            context, 0, rootIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_root, rootPendingIntent)

        // PendingIntent: Quick Add
        val addIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_QUICK_ADD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val addPendingIntent = PendingIntent.getActivity(
            context, 1, addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_btn_add, addPendingIntent)

        // PendingIntent: Voice Log
        val voiceIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VOICE_LOG
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val voicePendingIntent = PendingIntent.getActivity(
            context, 2, voiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_btn_voice, voicePendingIntent)

        // PendingIntent: Splits Tab
        val splitsIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_SPLITS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val splitsPendingIntent = PendingIntent.getActivity(
            context, 3, splitsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setOnClickPendingIntent(R.id.widget_btn_splits, splitsPendingIntent)
    
    }

    companion object {
        const val ACTION_QUICK_ADD = "com.vaibhav.kharchapaani.ACTION_QUICK_ADD"
        const val ACTION_VOICE_LOG = "com.vaibhav.kharchapaani.ACTION_VOICE_LOG"
        const val ACTION_VIEW_SPLITS = "com.vaibhav.kharchapaani.ACTION_VIEW_SPLITS"

        fun notifyWidgetUpdate(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, SplitFlowWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds.isNotEmpty()) {
                    val intent = Intent(context, SplitFlowWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
                    }
                    context.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
