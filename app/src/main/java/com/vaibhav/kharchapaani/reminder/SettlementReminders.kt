package com.vaibhav.kharchapaani.reminder

import android.content.Context
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import java.time.Instant
import java.time.ZoneId
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.util.NotificationHelper

/**
 * Reminders for settlements a groupmate recorded that are waiting for my answer (design item 10,
 * owner decision 2026-09-27): never auto-confirmed, pending for as long as it takes, with a nudge
 * when it arrives and then escalating ones on day 3, 6, 9 and so on.
 *
 * Worked out from when the settlement was recorded, so every phone of the same person agrees on the
 * stage, and each stage is shown once per phone ([SettlementEntity.remindedStage]). Runs after every
 * sync, which the periodic sync makes at least a few times a day.
 */
object SettlementReminders {

    private const val DAY_MILLIS = 86_400_000L
    const val EVERY_DAYS = 3

    /** 0 when it arrives, then 1 on day 3, 2 on day 6… */
    fun stageFor(createdAt: Long, now: Long): Int = (((now - createdAt).coerceAtLeast(0) / DAY_MILLIS) / EVERY_DAYS).toInt()

    fun textFor(context: Context, settlement: SettlementEntity, otherName: String, stage: Int, now: Long): Pair<String, String> {
        val amount = Money.format(settlement.amount)
        val theyPaidMe = settlement.toPersonId == -1L
        val what = when {
            settlement.isIou && theyPaidMe -> context.getString(R.string.notification_lent_to_you, otherName, amount)
            settlement.isIou -> context.getString(R.string.notification_you_lent, otherName, amount)
            theyPaidMe -> context.getString(R.string.notification_paid_to_you, otherName, amount)
            else -> context.getString(R.string.notification_you_paid, otherName, amount)
        }
        if (stage == 0) return what to context.getString(R.string.notification_payment_first_body)
        val days = ((now - settlement.createdAt) / DAY_MILLIS).toInt()
        return context.getString(R.string.notification_payment_later_title, amount, otherName) to context.getString(R.string.notification_payment_later_body, what, days)
    }

    /**
     * Shows whatever is due in [userId]'s ledger [db], and takes down the ones that no longer wait
     * for me; returns how many it showed. With the "Payments to confirm" channel blocked, the switch
     * off or quiet hours on ([prefs]), nothing is counted as shown, so the reminder comes later.
     */
    suspend fun remind(
        context: Context,
        db: AppDatabase,
        userId: String,
        now: Long = System.currentTimeMillis(),
        prefs: NotificationPrefs = NotificationPrefs(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val waiting = db.groupSyncDao().settlementsAwaitingMyAnswer()
        NotificationHelper.clearStaleSettlementReminders(context, waiting.map { it.id }.toSet())
        if (!NotificationHelper.canNotify(context, NotificationHelper.CHANNEL_SETTLEMENTS)) return 0
        // Turned off on the Notifications screen, or quiet hours: nothing is shown and no stage is
        // used up, so the nudge comes at the first sync after they end (or once they're back on).
        if (!prefs.paymentsToConfirm) return 0
        val local = Instant.ofEpochMilli(now).atZone(zone)
        if (prefs.quietHours.isQuietAt(local.hour * 60 + local.minute)) return 0
        var shown = 0
        for (settlement in waiting) {
            val stage = stageFor(settlement.createdAt, now)
            if (stage <= settlement.remindedStage) continue
            val otherId = if (settlement.fromPersonId == -1L) settlement.toPersonId else settlement.fromPersonId
            val name = db.personDao().getPersonById(otherId)?.name ?: context.getString(R.string.notification_groupmate)
            val (title, body) = textFor(context, settlement, name, stage, now)
            if (NotificationHelper.showSettlementReminder(context, userId, settlement.id, title, body)) {
                db.groupSyncDao().setRemindedStage(settlement.id, stage)
                shown++
            }
        }
        return shown
    }
}
