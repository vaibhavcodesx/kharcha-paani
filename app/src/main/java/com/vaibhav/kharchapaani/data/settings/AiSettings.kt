package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** Device-wide choice, available synchronously before any AI request or disclosure callback. */
class AiSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("ai_settings", Context.MODE_PRIVATE)
    val enabledNow: Boolean get() = preferences.getBoolean("enabled", true)

    val enabled: Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "enabled" || key == null) trySend(enabledNow)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(enabledNow)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    suspend fun setEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        check(preferences.edit().putBoolean("enabled", enabled).commit()) { "Couldn't save AI preference" }
    }
}
