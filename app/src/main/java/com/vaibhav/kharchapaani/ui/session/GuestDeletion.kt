package com.vaibhav.kharchapaani.ui.session

import android.content.Context
import android.util.Log
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.data.settings.GuestSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What "Delete this phone's ledger" removes: the guest ledger's file, its bill photos and its settings
 * (UX-01, owner answer A-09). The caller has already ended the guest session; the account ledger and
 * everything of any signed-in account are untouched. Afterwards [com.vaibhav.kharchapaani.data.settings.LedgerMode.discardGuest]
 * returns the phone to sign-in, and a later "Use without signing in" starts a new, empty ledger.
 */
object GuestDeletion {

    private const val TAG = "GuestDeletion"

    suspend fun wipeLocal(context: Context, guest: GuestSession) {
        val app = context.applicationContext
        AccountLeftovers.clear(app)
        // The ledger has to be closed before its file can go.
        LedgerDatabases.close()
        withContext(Dispatchers.IO) {
            val name = LedgerDatabases.localDatabaseName(guest)
            // deleteDatabase takes the -wal, -shm and -journal files with it.
            if (!app.deleteDatabase(name) && app.getDatabasePath(name).exists()) {
                Log.w(TAG, "Couldn't delete the local ledger file")
            }
            val receipts = ReceiptStore.forLocal(app, guest).dir
            if (receipts.exists() && !receipts.deleteRecursively()) Log.w(TAG, "Couldn't delete the local bill photos")
        }
        AppSettings(app).clearAccount(guest.settingsId)
    }
}
