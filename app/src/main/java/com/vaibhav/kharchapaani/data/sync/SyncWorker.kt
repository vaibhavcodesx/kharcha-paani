package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.remote.BackgroundAuth
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.reminder.SettlementReminders
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Syncs one account's ledger. Queued work only acts while that account is the one signed in:
 * another account's rows stay queued on disk until its owner signs back in.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val supabase = SupabaseClientProvider.get(applicationContext)
        // Not auth.awaitInitialization(): in the background that waits until the app is next opened.
        val signedInUserId = BackgroundAuth.sessionForNetwork(applicationContext)

        return syncIfSignedIn(requestedUserId = userId, signedInUserId = signedInUserId) {
            val database = LedgerDatabases.open(applicationContext, userId)
            TransactionSyncEngine(
                store = RoomTransactionSyncStore(database),
                remote = SupabaseTransactionRemote(supabase, ownerId = userId),
                ownerId = userId,
            ).sync()
            GroupSyncEngine(database, SupabaseGroupRemote(supabase), selfProfileId = userId).sync()
            // After personal sync, so a private split's expense is here before its split is pulled.
            if (LocalFriendSync.ENABLED) LocalLedgerSyncEngine(database, SupabaseLocalLedgerRemote(supabase, userId)).sync()
            // Only a pass that got through both engines counts as "last synced".
            database.syncDao().putMeta(SyncMetaEntity(TransactionSyncEngine.LAST_SYNCED_AT, System.currentTimeMillis().toString()))
            // A pull can change the figures (a groupmate's expense, a restore after reinstalling),
            // and nothing else would tell the home-screen widgets: they'd keep the old numbers.
            SplitFlowWidgetProvider.notifyWidgetUpdate(applicationContext)
            // A groupmate may have chosen a new colour: fit everyone else on this phone around it.
            LedgerRepository(database, selfProfileId = userId).repairPersonColours()
            // Payments a groupmate recorded that wait for my answer: the first nudge, then day 3, 6…
            SettlementReminders.remind(applicationContext, database, userId)
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        private const val TAG = "SyncWorker"

        /**
         * One pass at a time. The periodic and the one-time work are separate unique works, so both
         * can run at once, and the server stamps every write with its own clock: a pass that read a
         * row before an edit could upload the old value after another pass uploaded the new one, and
         * its pull would then overwrite the edit (Codex review, 2026-10-23).
         */
        private val passLock = Mutex()

        internal suspend fun syncIfSignedIn(
            requestedUserId: String,
            signedInUserId: String?,
            sync: suspend () -> Unit,
        ): ListenableWorker.Result {
            if (signedInUserId != requestedUserId) return ListenableWorker.Result.success()
            return try {
                passLock.withLock { sync() }
                ListenableWorker.Result.success()
            } catch (e: IOException) {
                // Offline, DNS, timeout (supabase-kt's HttpRequestException is an IOException).
                ListenableWorker.Result.retry()
            } catch (e: RestException) {
                Log.w(TAG, "Sync request failed (${e.statusCode}): ${e.error}")
                ListenableWorker.Result.retry()
            } catch (e: IllegalStateException) {
                // Expected only when a sign-out closes the database mid-run; anything else is a bug,
                // so it's logged loudly. Rows stay queued either way and the next trigger resyncs.
                Log.e(TAG, "Sync stopped", e)
                ListenableWorker.Result.failure()
            }
        }
    }
}
