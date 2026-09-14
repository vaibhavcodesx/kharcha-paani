package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException

/**
 * Syncs one account's ledger. Queued work only acts while that account is the one signed in:
 * another account's rows stay queued on disk until its owner signs back in.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val userId = inputData.getString(KEY_USER_ID) ?: return Result.failure()
        val supabase = SupabaseClientProvider.get(applicationContext)
        val auth = supabase.auth
        auth.awaitInitialization()

        return syncIfSignedIn(requestedUserId = userId, signedInUserId = auth.currentUserOrNull()?.id) {
            val database = LedgerDatabases.open(applicationContext, userId)
            TransactionSyncEngine(
                store = RoomTransactionSyncStore(database),
                remote = SupabaseTransactionRemote(supabase, ownerId = userId),
                ownerId = userId,
            ).sync()
            GroupSyncEngine(database, SupabaseGroupRemote(supabase), selfProfileId = userId).sync()
            // Only a pass that got through both engines counts as "last synced".
            database.syncDao().putMeta(SyncMetaEntity(TransactionSyncEngine.LAST_SYNCED_AT, System.currentTimeMillis().toString()))
        }
    }

    companion object {
        const val KEY_USER_ID = "user_id"
        private const val TAG = "SyncWorker"

        internal suspend fun syncIfSignedIn(
            requestedUserId: String,
            signedInUserId: String?,
            sync: suspend () -> Unit,
        ): ListenableWorker.Result {
            if (signedInUserId != requestedUserId) return ListenableWorker.Result.success()
            return try {
                sync()
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
