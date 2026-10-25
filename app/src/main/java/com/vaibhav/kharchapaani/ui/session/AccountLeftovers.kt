package com.vaibhav.kharchapaani.ui.session

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import java.io.File

/**
 * What one account leaves outside its own ledger file, cleared when the phone moves to another
 * account or signs out (security audit, 2026-10-24).
 *
 * - Its notifications. A settlement reminder carries a settlement id from that account's ledger,
 *   and the same number means a different payment in the next account's.
 * - Files written for sharing: CSV exports (MainViewModel, `cache/exports`) and ask-to-pay QR
 *   images (AskShare, `cache/asks`). Each names people and amounts from that account.
 */
object AccountLeftovers {

    private const val TAG = "AccountLeftovers"

    /** The cache folders above, relative to cacheDir. */
    internal val SHARED_FILE_DIRS = listOf("exports", "asks")

    fun clear(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
        for (name in SHARED_FILE_DIRS) {
            val dir = File(context.cacheDir, name)
            if (dir.exists() && !dir.deleteRecursively()) Log.w(TAG, "Couldn't clear cache/$name")
        }
    }
}
