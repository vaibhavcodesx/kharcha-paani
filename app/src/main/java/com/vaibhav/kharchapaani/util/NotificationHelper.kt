package com.vaibhav.kharchapaani.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vaibhav.kharchapaani.IncomingIntents
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider

/**
 * The app's notifications: the daily reminder, and payments a groupmate recorded that wait for my
 * answer.
 *
 * This file used to hold an AI-Studio-era "monthly spending digest" that nothing ever called, on a
 * high-importance channel with lights and vibration — the wrong weight for anything this app says.
 */
object NotificationHelper {

    const val CHANNEL_DAILY_REMINDER = "daily_reminder"
    const val CHANNEL_SETTLEMENTS = "settlements_to_confirm"
    private const val NOTIFICATION_ID_DAILY_REMINDER = 2001
    /**
     * One notification per settlement, told apart by its tag ("settlement:<id>"), so a newer stage
     * replaces the older one and the stale ones can be found again. Ids used to be computed
     * (30,000 + id % 100,000), which could collide and couldn't be listed.
     */
    private const val NOTIFICATION_ID_SETTLEMENT = 30_000
    private const val SETTLEMENT_TAG_PREFIX = "settlement:"
    /** Before tags: 30,000 + id % 100,000. Still on phones that updated with one showing. */
    private val LEGACY_SETTLEMENT_IDS = 30_000 until 130_000
    const val ACTION_ANSWER_SETTLEMENT = "com.vaibhav.kharchapaani.ACTION_ANSWER_SETTLEMENT"
    const val EXTRA_SETTLEMENT_ID = "settlement_id"
    /** Whose ledger [EXTRA_SETTLEMENT_ID] is in: the same id is another payment in another account's. */
    const val EXTRA_USER_ID = "user_id"

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
    fun showSettlementReminder(context: Context, userId: String, settlementId: Long, title: String, text: String): Boolean {
        // Checked before creating: re-creating a channel never undoes the person blocking it, but
        // the check should read what they chose, not what this call is about to ask for.
        if (!canNotify(context, CHANNEL_SETTLEMENTS)) return false
        createChannels(context)
        val open = IncomingIntents.internal(context, ACTION_ANSWER_SETTLEMENT).apply {
            putExtra(EXTRA_SETTLEMENT_ID, settlementId)
            putExtra(EXTRA_USER_ID, userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        // A request code per settlement keeps each notification's extras its own.
        val pendingIntent = PendingIntent.getActivity(context, settlementId.hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
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
            NotificationManagerCompat.from(context).notify(settlementTag(settlementId), NOTIFICATION_ID_SETTLEMENT, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Takes a settlement's reminder down once it's answered here. */
    fun clearSettlementReminder(context: Context, settlementId: Long) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(settlementTag(settlementId), NOTIFICATION_ID_SETTLEMENT)
        manager.cancel(legacySettlementId(settlementId))
    }

    /**
     * Takes down every "payment to confirm" notification whose payment no longer waits for me: answered
     * on another phone, withdrawn by whoever recorded it, or gone with its group. Before this, only an
     * answer given on this phone cleared one, and tapping a stale one opened a payment that wasn't
     * waiting any more. Runs after every sync.
     */
    fun clearStaleSettlementReminders(context: Context, stillWaiting: Set<Long>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val legacyStillWaiting = stillWaiting.map(::legacySettlementId).toSet()
        for (shown in manager.activeNotifications) {
            val tag = shown.tag
            when {
                tag != null && tag.startsWith(SETTLEMENT_TAG_PREFIX) -> {
                    val id = tag.removePrefix(SETTLEMENT_TAG_PREFIX).toLongOrNull()
                    if (id == null || id !in stillWaiting) manager.cancel(tag, shown.id)
                }
                tag == null && shown.id in LEGACY_SETTLEMENT_IDS && shown.id !in legacyStillWaiting -> manager.cancel(shown.id)
            }
        }
    }

    private fun settlementTag(settlementId: Long) = "$SETTLEMENT_TAG_PREFIX$settlementId"

    private fun legacySettlementId(settlementId: Long) = LEGACY_SETTLEMENT_IDS.first + (settlementId % 100_000).toInt()

    /**
     * False when nothing can show: notifications off for the app (not granted on Android 13+, or
     * turned off), or, given [channelId], that channel blocked. Checking only the app let a blocked
     * "Payments to confirm" channel swallow reminders that were then counted as shown.
     */
    fun canNotify(context: Context, channelId: String? = null): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (channelId == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        // Not created yet means not blocked: posting creates it with its default importance.
        val channel = manager.getNotificationChannel(channelId) ?: return true
        return channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    /** Posts the reminder; tapping it opens straight into adding an expense. Returns whether it posted. */
    fun showDailyReminder(context: Context): Boolean {
        if (!canNotify(context, CHANNEL_DAILY_REMINDER)) return false
        createChannels(context)

        // The widget's quick-add action: MainActivity already routes it to a new expense on Home.
        val open = IncomingIntents.internal(context, SplitFlowWidgetProvider.ACTION_QUICK_ADD).apply {
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
