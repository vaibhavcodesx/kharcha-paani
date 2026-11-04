package com.vaibhav.kharchapaani.diagnostics

import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.vaibhav.kharchapaani.BuildConfig

/**
 * Debug-only StrictMode configuration (plan Y11, REL audit line 371).
 *
 * Catches accidental disk I/O and network operations on the main thread, as well
 * as leaked SQLite objects and unclosed resources. Uses [StrictMode.enableDefaults]
 * which applies penaltyLog() rather than penaltyDeath(), ensuring development and
 * testing aren't halted by AndroidX/Compose/Room framework internal reads.
 */
object AppStrictMode {

    private const val TAG = "AppStrictMode"

    /**
     * Installs StrictMode policies for debug builds on real devices.
     * Skipped in release builds and in Robolectric unit test environments.
     */
    fun configure(
        isDebug: Boolean = BuildConfig.DEBUG,
        isRobolectric: Boolean = Build.FINGERPRINT == "robolectric",
    ) {
        if (!isDebug || isRobolectric) return
        try {
            StrictMode.enableDefaults()
        } catch (e: Exception) {
            Log.w(TAG, "StrictMode.enableDefaults failed to initialize", e)
        }
    }
}
