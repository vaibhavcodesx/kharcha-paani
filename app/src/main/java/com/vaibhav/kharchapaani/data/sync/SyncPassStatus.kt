package com.vaibhav.kharchapaani.data.sync

import android.util.Log
import com.vaibhav.kharchapaani.data.db.dao.SyncDao
import com.vaibhav.kharchapaani.data.db.entity.SyncMetaEntity
import io.github.jan.supabase.exceptions.RestException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

internal object SyncPassStatus {
    const val LAST_SYNC_ERROR = "last_sync_error"
    const val OFFLINE = "offline"
    const val SERVER = "server"
    const val OTHER = "other"

    suspend fun record(dao: SyncDao, pass: suspend () -> Unit) {
        try {
            pass()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val reason = when (e) {
                is IOException -> OFFLINE
                is RestException -> SERVER
                else -> OTHER
            }
            try {
                dao.putMeta(SyncMetaEntity(LAST_SYNC_ERROR, reason))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // An account switch can close Room. Preserve the original sync failure.
                Log.w("SyncPassStatus", "Couldn't store sync failure")
            }
            throw e
        }
        dao.deleteMeta(LAST_SYNC_ERROR)
    }
}
