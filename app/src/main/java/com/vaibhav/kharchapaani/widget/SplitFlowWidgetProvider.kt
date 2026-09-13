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
import com.vaibhav.kharchapaani.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

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
                val db = AppDatabase.getInstance(context)
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val monthStart = cal.timeInMillis

                val transactions = db.transactionDao().getAllTransactionsDirect()
                val people = db.personDao().getAllPeopleDirect()
                val allSplits = db.splitDao().getAllSplitsDirect()
                val allShares = db.splitDao().getAllSplitSharesDirect()
                val allSettlements = db.settlementDao().getAllSettlementsDirect()

                val splitMap = allSplits.associateBy { it.transactionId }
                val shareMap = allShares.groupBy { it.splitId }

                var monthlySpent = 0.0
                var monthlyIncome = 0.0

                for (tx in transactions) {
                    if (tx.date >= monthStart) {
                        if (tx.type.equals("income", ignoreCase = true)) {
                            monthlyIncome += tx.amount
                        } else {
                            val split = splitMap[tx.id]
                            val personalShare = if (split != null) {
                                val splitShares = shareMap[split.id] ?: emptyList()
                                if (split.paidByPersonId == -1L) {
                                    val myShare = splitShares.find { it.personId == -1L }?.shareAmount
                                    if (myShare != null) myShare else {
                                        val friendsShare = splitShares.filter { it.personId != -1L }.sumOf { it.shareAmount }
                                        (tx.amount - friendsShare).coerceAtLeast(0.0)
                                    }
                                } else {
                                    splitShares.find { it.personId == -1L }?.shareAmount ?: 0.0
                                }
                            } else {
                                tx.amount
                            }
                            monthlySpent += personalShare
                        }
                    }
                }

                var netOwedToMe = 0.0
                for (person in people) {
                    var owedToMe = 0.0
                    var iOwe = 0.0

                    // splits where I paid (paidByPersonId == -1) and person has a share
                    for (split in allSplits) {
                        val shares = allShares.filter { it.splitId == split.id }
                        if (split.paidByPersonId == -1L) {
                            val personShare = shares.find { it.personId == person.id }
                            if (personShare != null) {
                                owedToMe += personShare.shareAmount
                            }
                        } else if (split.paidByPersonId == person.id) {
                            val myShare = shares.find { it.personId == -1L }
                            if (myShare != null) {
                                iOwe += myShare.shareAmount
                            }
                        }
                    }

                    var settledToMe = 0.0
                    var settledByMe = 0.0
                    for (s in allSettlements) {
                        if (s.fromPersonId == person.id && s.toPersonId == -1L) {
                            settledToMe += s.amount
                        } else if (s.fromPersonId == -1L && s.toPersonId == person.id) {
                            settledByMe += s.amount
                        }
                    }

                    val net = (owedToMe - settledToMe) - (iOwe - settledByMe)
                    netOwedToMe += net
                }

                val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).apply {
                    maximumFractionDigits = 0
                }

                val spentStr = currencyFormatter.format(monthlySpent)
                val incomeStr = currencyFormatter.format(monthlyIncome)
                val netBadgeStr = if (netOwedToMe > 0) {
                    "+${currencyFormatter.format(netOwedToMe)} owed"
                } else if (netOwedToMe < 0) {
                    "-${currencyFormatter.format(-netOwedToMe)} owe"
                } else {
                    "Balanced"
                }

                val views = RemoteViews(context.packageName, R.layout.widget_splitflow).apply {
                    setTextViewText(R.id.widget_month_spent, spentStr)
                    setTextViewText(R.id.widget_month_income, incomeStr)
                    setTextViewText(R.id.widget_net_owed_badge, netBadgeStr)

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

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
