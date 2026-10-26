package com.vaibhav.kharchapaani.ui.session

import android.content.Context
import android.util.Log
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.data.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What deleting an account removes from this phone, once the server has deleted it (plan T1.1,
 * decision D1). Signing out keeps a ledger for next time; a deleted account has no next time, so
 * everything of it goes: its ledger file, its bill photos, its settings, its scheduled sync, and what
 * [AccountLeftovers] clears on any account change (notifications, shared files).
 */
object AccountDeletion {

    private const val TAG = "AccountDeletion"

    suspend fun wipeLocal(context: Context, userId: String) {
        val app = context.applicationContext
        SyncScheduler.cancelFor(app, userId)
        AccountLeftovers.clear(app)
        // The ledger has to be closed before its file can go; sign-out has usually closed it already.
        LedgerDatabases.close()
        withContext(Dispatchers.IO) {
            // deleteDatabase takes the -wal, -shm and -journal files with it.
            if (!app.deleteDatabase(LedgerDatabases.databaseName(userId)) && app.getDatabasePath(LedgerDatabases.databaseName(userId)).exists()) {
                Log.w(TAG, "Couldn't delete the ledger file")
            }
            val receipts = ReceiptStore.forAccount(app, userId).dir
            if (receipts.exists() && !receipts.deleteRecursively()) Log.w(TAG, "Couldn't delete the bill photos")
        }
        AppSettings(app).clearAccount(userId)
    }
}
