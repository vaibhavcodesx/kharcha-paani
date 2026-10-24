package com.vaibhav.kharchapaani.ask

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.PaymentAskEntity
import com.vaibhav.kharchapaani.data.remote.BackgroundAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Called by the share sheet when an app is picked for an ask (see [AskShare.chooser]); logs the ask
 * in the account's ledger so the friend's row can say "Asked today". Not exported: only the share
 * sheet, through our own PendingIntent, reaches it.
 */
class AskSentReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                record(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AskSentReceiver"

        /**
         * Writes the ask; false when the intent doesn't describe one, or when its account is no longer
         * the one signed in. Opening another account's ledger would close the one the app is using.
         */
        internal suspend fun record(
            context: Context,
            intent: Intent,
            now: Long = System.currentTimeMillis(),
            signedInUserId: suspend () -> String? = { BackgroundAuth.signedInUserId(context) },
        ): Boolean {
            val personId = intent.getLongExtra(AskShare.EXTRA_PERSON_ID, 0L)
            val amount = intent.getDoubleExtra(AskShare.EXTRA_AMOUNT, 0.0)
            val userId = intent.getStringExtra(AskShare.EXTRA_USER_ID)
            if (personId <= 0L || amount <= 0.0 || userId == null) return false
            return try {
                if (signedInUserId() != userId) {
                    Log.i(TAG, "Ask picked after switching accounts; not logged")
                    return false
                }
                // The same instance the app uses when it's open (the ask was made from it a moment ago).
                LedgerDatabases.open(context, userId).paymentAskDao()
                    .insert(PaymentAskEntity(personId = personId, amount = amount, sentAt = now))
                true
            } catch (e: Exception) {
                // A missing "Asked" line is harmless; a crash in the share sheet's callback is not.
                Log.w(TAG, "Couldn't log the ask", e)
                false
            }
        }
    }
}
