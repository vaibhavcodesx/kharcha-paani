package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.vaibhav.kharchapaani.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** The one `public.app_config` row: a server-driven minimum app version (S-06, owner answer C-08). */
@Serializable
data class RemoteAppConfig(
    @SerialName("min_version_code") val minVersionCode: Int = 0,
    @SerialName("recommended_version_code") val recommendedVersionCode: Int = 0,
)

/**
 * Below the server's minimum version the app stops syncing; reading and exporting keep working. The
 * last value read is cached so the rule still holds offline and when the config can't be reached.
 */
object AppVersionGate {
    private const val PREFS = "app_version_gate"
    private const val KEY_MIN = "min_version_code"
    private const val KEY_RECOMMENDED = "recommended_version_code"
    private const val KEY_DISMISSED_DATE = "recommended_dismissed_date"
    private const val TAG = "AppVersionGate"

    /** Pure rule: a minimum of 0 (or less) means no minimum. */
    fun syncAllowed(installedVersionCode: Int, minVersionCode: Int): Boolean =
        minVersionCode <= 0 || installedVersionCode >= minVersionCode

    /** True when the person should be asked to update to keep syncing. */
    fun updateRequired(context: Context, installedVersionCode: Int = BuildConfig.VERSION_CODE): Boolean =
        !syncAllowed(installedVersionCode, cachedMinVersion(context))

    fun cachedMinVersion(context: Context): Int =
        prefs(context).getInt(KEY_MIN, 0)

    fun cachedRecommendedVersion(context: Context): Int =
        prefs(context).getInt(KEY_RECOMMENDED, 0)

    enum class Notice { REQUIRED, RECOMMENDED, NONE }

    fun notice(
        context: Context,
        installedVersionCode: Int = BuildConfig.VERSION_CODE,
        today: LocalDate = LocalDate.now(),
    ): Notice = when {
        updateRequired(context, installedVersionCode) -> Notice.REQUIRED
        cachedRecommendedVersion(context) > installedVersionCode &&
            prefs(context).getString(KEY_DISMISSED_DATE, null) != today.toString() -> Notice.RECOMMENDED
        else -> Notice.NONE
    }

    fun observeNotice(context: Context) = callbackFlow {
        val preferences = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(notice(context)) }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(notice(context))
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun dismissRecommendation(context: Context, today: LocalDate = LocalDate.now()) {
        prefs(context).edit().putString(KEY_DISMISSED_DATE, today.toString()).apply()
    }

    /**
     * Reads the config and caches it. Any failure (offline, table missing before the migration is
     * applied, a decoding error) leaves the cached value alone: a broken config must never block syncing.
     */
    suspend fun refreshQuietly(context: Context, supabase: SupabaseClient) {
        try {
            val row = supabase.from("app_config").select().decodeList<RemoteAppConfig>().firstOrNull() ?: return
            prefs(context).edit()
                .putInt(KEY_MIN, row.minVersionCode.coerceAtLeast(0))
                .putInt(KEY_RECOMMENDED, row.recommendedVersionCode.coerceAtLeast(0))
                .apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read app_config; keeping the cached minimum", e)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
