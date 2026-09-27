package com.vaibhav.kharchapaani.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vaibhav.kharchapaani.MainActivity
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider

/**
 * The app's notifications. There is one: the daily reminder.
 *
 * This file used to hold an AI-Studio-era "monthly spending digest" that nothing ever called, on a
 * high-importance channel with lights and vibration — the wrong weight for anything this app says.
 */
object NotificationHelper {

    const val CHANNEL_DAILY_REMINDER = "daily_reminder"
    const val CHANNEL_SETTLEMENTS = "settlements_to_confirm"
    private const val NOTIFICATION_ID_DAILY_REMINDER = 2001
    /** One notification per settlement, so a newer stage replaces the older one for the same payment. */
    private const val NOTIFICATION_ID_SETTLEMENT_BASE = 30_000
    const val ACTION_ANSWER_SETTLEMENT = "com.vaibhav.kharchapaani.ACTION_ANSWER_SETTLEMENT"
    const val EXTRA_SETTLEMENT_ID = "settlement_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_DAILY_REMINDER,
            "Daily reminder",
            // Default, not high: it can make a sound, but it never pops over whatever someone is doing.
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A nudge in the evening if nothing's been logged that day"
        }
        val settlements = NotificationChannel(
            CHANNEL_SETTLEMENTS,
            "Payments to confirm",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "When a groupmate says money changed hands with you, until you confirm it or say it isn't right"
        }
        context.getSystemService(NotificationManager::class.java)?.let {
            it.createNotificationChannel(channel)
            it.createNotificationChannel(settlements)
        }
    }

    /** A settlement waiting for my answer (design item 10); tapping it opens the answer sheet. */
    fun showSettlementReminder(context: Context, settlementId: Long, title: String, text: String): Boolean {
        if (!canNotify(context)) return false
        createChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            action = ACTION_ANSWER_SETTLEMENT
            putExtra(EXTRA_SETTLEMENT_ID, settlementId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val id = NOTIFICATION_ID_SETTLEMENT_BASE + (settlementId % 100_000).toInt()
        val pendingIntent = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_SETTLEMENTS)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(0xFFFF7A3D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(id, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Takes a settlement's reminder down once it's answered here. */
    fun clearSettlementReminder(context: Context, settlementId: Long) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SETTLEMENT_BASE + (settlementId % 100_000).toInt())
    }

    /** False when notifications are off for the app: not granted on Android 13+, or turned off. */
    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Posts the reminder; tapping it opens straight into adding an expense. Returns whether it posted. */
    fun showDailyReminder(context: Context): Boolean {
        if (!canNotify(context)) return false
        createChannels(context)

        // The widget's quick-add action: MainActivity already routes it to a new expense on Home.
        val open = Intent(context, MainActivity::class.java).apply {
            action = SplitFlowWidgetProvider.ACTION_QUICK_ADD
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_DAILY_REMINDER,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(0xFFFF7A3D.toInt())
            .setContentTitle("Nothing logged today")
            .setContentText("Add today's spending before it slips your mind.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_DAILY_REMINDER, notification)
            true
        } catch (e: SecurityException) {
            // Permission withdrawn between the check and the post.
            false
        }
    }

    /** Takes the reminder down once something has been logged, so it doesn't sit there stale. */
    fun clearDailyReminder(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_DAILY_REMINDER)
    }
}
