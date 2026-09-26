package com.vaibhav.kharchapaani.data.remote

import android.content.Context
import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Who is signed in, for code that runs while the app is in the background: sync, the widgets and
 * the daily reminder.
 *
 * `auth.awaitInitialization()` can't be used there. supabase-kt 3.6 (androidMain setupPlatform.kt)
 * sets the session status back to Initializing whenever the app goes to the background, and only
 * moves it on at the next foreground. So in a process that is still alive, every background caller
 * waited until the app was next opened: widgets never filled in, the 9 PM reminder arrived when the
 * app was opened the next morning, and a background sync did nothing. The session itself is still
 * in memory, and it's always in storage.
 */
object BackgroundAuth {

    private const val TAG = "BackgroundAuth"

    /**
     * The signed-in account's id without waiting on anything: from memory, else from the stored
     * session (a cold process still loading it). Null when signed out. No network.
     */
    suspend fun signedInUserId(context: Context): String? {
        val auth = SupabaseClientProvider.get(context).auth
        auth.currentUserOrNull()?.id?.let { return it }
        return auth.sessionManager.loadSessionOrNull()?.user?.id
    }

    /**
     * Makes sure requests carry a live session before background network work, then returns the
     * signed-in account's id. When the app is in the background it re-imports the stored session
     * the way supabase-kt does on returning to the foreground, which refreshes it if it's close to
     * expiring.
     */
    suspend fun sessionForNetwork(context: Context): String? {
        val auth = SupabaseClientProvider.get(context).auth
        if (withTimeoutOrNull(INIT_WAIT_MS) { auth.awaitInitialization() } == null) {
            try {
                auth.loadFromStorage()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't reload the stored session", e)
            }
            if (withTimeoutOrNull(REFRESH_WAIT_MS) { auth.awaitInitialization() } == null) {
                Log.w(TAG, "Session still initialising; using what's in memory")
            }
        }
        return signedInUserId(context)
    }

    private const val INIT_WAIT_MS = 3_000L
    private const val REFRESH_WAIT_MS = 15_000L
}
