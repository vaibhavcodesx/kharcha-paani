package com.vaibhav.kharchapaani.reminder

import android.content.Context
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

    fun textFor(settlement: SettlementEntity, otherName: String, stage: Int, now: Long): Pair<String, String> {
        val amount = Money.format(settlement.amount)
        val theyPaidMe = settlement.toPersonId == -1L
        val what = when {
            settlement.isIou && theyPaidMe -> "$otherName says they lent you $amount"
            settlement.isIou -> "$otherName says you lent them $amount"
            theyPaidMe -> "$otherName says they paid you $amount"
            else -> "$otherName says you paid them $amount"
        }
        if (stage == 0) return what to "Confirm it, or say it isn't right. It doesn't count until you do."
        val days = ((now - settlement.createdAt) / DAY_MILLIS).toInt()
        return "Still waiting on you: $amount with $otherName" to "$what, $days days ago. It won't count until you answer."
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
            val name = db.personDao().getPersonById(otherId)?.name ?: "A groupmate"
            val (title, body) = textFor(settlement, name, stage, now)
            if (NotificationHelper.showSettlementReminder(context, userId, settlement.id, title, body)) {
                db.groupSyncDao().setRemindedStage(settlement.id, stage)
                shown++
            }
        }
        return shown
    }
}
