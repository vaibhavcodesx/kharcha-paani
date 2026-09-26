package com.vaibhav.kharchapaani.reminder

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.remote.BackgroundAuth
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.util.NotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Once a day at the chosen time: if the signed-in account has logged nothing of its own today,
 * a gentle notification. Then it books tomorrow's run.
 *
 * One-time work that re-enqueues itself rather than a 24-hour periodic request, so the time stays
 * the time the person picked instead of drifting with each late run. WorkManager keeps the booking
 * across reboots and app updates; it is inexact under Doze by design (no exact-alarm permission).
 */
class DailyReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext).reminder.first()
        // Turned off: nothing to show and nothing to book. Turning it back on books a fresh run.
        if (!settings.enabled) return Result.success()
        try {
            remindIfNothingLogged()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A missed reminder is harmless; a worker that stops booking tomorrow is not.
            Log.w(TAG, "Couldn't check today's entries", e)
        }
        ReminderScheduler.bookNext(applicationContext, settings.minuteOfDay, fromWorker = true)
        return Result.success()
    }

    private suspend fun remindIfNothingLogged() {
        if (!NotificationHelper.canNotify(applicationContext)) return
        // Signed out: there's no one's day to remind about. Not auth.awaitInitialization(): in the
        // background that waits until the app is next opened, which is exactly when this isn't needed.
        val userId = BackgroundAuth.signedInUserId(applicationContext) ?: return
        // The same instance the app uses when it's open; never closed here.
        val database = LedgerDatabases.open(applicationContext, userId)
        val zone = ZoneId.systemDefault()
        val transactions = database.transactionDao().getAllTransactionsDirect()
        if (ReminderRule.nothingLoggedToday(transactions, LocalDate.now(zone), zone)) {
            NotificationHelper.showDailyReminder(applicationContext)
        }
    }

    private companion object {
        const val TAG = "DailyReminder"
    }
}

object ReminderScheduler {

    private const val WORK_NAME = "daily-reminder"

    /**
     * Makes the booking match [settings]: off cancels it, on books the next occurrence. [replace]
     * is for a change of time or a switch back on; at app start, KEEP leaves a booking alone.
     */
    fun apply(context: Context, settings: ReminderSettings, replace: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (!settings.enabled) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        enqueue(context, settings.minuteOfDay, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP)
    }

    /**
     * Tomorrow's run, booked from inside today's. APPEND_OR_REPLACE because REPLACE would cancel
     * the very worker doing the booking; appended, the next run waits for this one to finish and
     * then its delay.
     */
    internal fun bookNext(context: Context, minuteOfDay: Int, fromWorker: Boolean) {
        enqueue(context, minuteOfDay, if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE)
    }

    private fun enqueue(context: Context, minuteOfDay: Int, policy: ExistingWorkPolicy) {
        val delay = ReminderSchedule.delayUntilNext(ZonedDateTime.now(), minuteOfDay)
        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }
}
