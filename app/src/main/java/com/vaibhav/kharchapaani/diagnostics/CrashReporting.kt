package com.vaibhav.kharchapaani.diagnostics

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.vaibhav.kharchapaani.BuildConfig

/**
 * Crash reports through Firebase Crashlytics (plan T1.6, REL H4).
 *
 * Firebase is set up by hand from `.env` (FIREBASE_APP_ID, FIREBASE_API_KEY, FIREBASE_PROJECT_ID)
 * instead of the google-services plugin, so CI and local builds without a Firebase project still
 * build, and simply never report. The person can turn reports off (on unless they do; the privacy
 * policy says so). The setting is read synchronously at start, before anything can crash, so it lives
 * in plain SharedPreferences rather than DataStore.
 *
 * What's sent for a handled problem is only its reason and where in the code it happened: never an
 * amount, a name, a note or anything read from a document. Exceptions from the server or Gemini can
 * carry such things in their messages, so they're never passed on; only their class name is.
 */
object CrashReporting {

    private const val TAG = "CrashReporting"
    private const val PREFS = "diagnostics"
    private const val KEY_ENABLED = "crash_reports_enabled"

    @Volatile private var started = false

    /** Whether this build has a Firebase project at all; without one the switch isn't offered. */
    val isAvailable: Boolean get() = options() != null

    /** Called once from KharchaPaaniApp.onCreate. Cheap and silent when there's no Firebase project. */
    fun start(context: Context) {
        val options = options() ?: return
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, options)
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = isEnabled(context)
            started = true
        } catch (e: Exception) {
            // Never let crash reporting be what crashes the app.
            Log.w(TAG, "Crash reporting not started", e)
        }
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (started) FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = enabled
    }

    /**
     * A problem the app handled but someone should look at: a sync that keeps failing, a ledger that
     * wouldn't open. [reason] is a fixed description written in the code ("Sync failed: HTTP 403"),
     * and [cause] contributes only its class name.
     */
    fun recordProblem(reason: String, cause: Throwable? = null) {
        val report = HandledProblem(if (cause == null) reason else "$reason (${cause.javaClass.simpleName})")
        Log.w(TAG, report.message)
        if (!started) return
        try {
            FirebaseCrashlytics.getInstance().recordException(report)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't record a handled problem", e)
        }
    }

    private fun options(): FirebaseOptions? {
        val appId = BuildConfig.FIREBASE_APP_ID.takeUnless { it.isPlaceholder() } ?: return null
        val apiKey = BuildConfig.FIREBASE_API_KEY.takeUnless { it.isPlaceholder() } ?: return null
        val projectId = BuildConfig.FIREBASE_PROJECT_ID.takeUnless { it.isPlaceholder() } ?: return null
        return FirebaseOptions.Builder().setApplicationId(appId).setApiKey(apiKey).setProjectId(projectId).build()
    }

    private fun String.isPlaceholder() = isBlank() || startsWith("MY_FIREBASE_")
}

/** What a handled problem is reported as: its stack trace is where it was recorded. */
class HandledProblem(message: String) : Exception(message)
