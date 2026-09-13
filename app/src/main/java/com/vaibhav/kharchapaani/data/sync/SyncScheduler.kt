package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Push-then-pull soon. REPLACE collapses a burst of edits into one run, and replacing a run in
     * flight is safe: unconfirmed rows stay PENDING and re-pushing them is idempotent.
     */
    fun requestSync(context: Context, userId: String) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(workDataOf(SyncWorker.KEY_USER_ID to userId))
            .setConstraints(networkConstraint)
            .setInitialDelay(DEBOUNCE_SECONDS, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(oneTimeName(userId), ExistingWorkPolicy.REPLACE, request)
    }

    /** Also pulls changes made on other devices while this one sits idle. */
    fun schedulePeriodicSync(context: Context, userId: String) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
            .setInputData(workDataOf(SyncWorker.KEY_USER_ID to userId))
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(periodicName(userId), ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** On sign-out. Unsynced rows stay in the account's file and go up after its next sign-in. */
    fun cancelFor(context: Context, userId: String) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(oneTimeName(userId))
            cancelUniqueWork(periodicName(userId))
        }
    }

    fun observeIsSyncing(context: Context, userId: String): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(oneTimeName(userId))
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }

    private fun oneTimeName(userId: String) = "sync-$userId"
    private fun periodicName(userId: String) = "sync-periodic-$userId"

    private const val DEBOUNCE_SECONDS = 2L
    private const val BACKOFF_SECONDS = 30L
    private const val PERIODIC_MINUTES = 15L
}
