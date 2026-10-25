package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaibhav.kharchapaani.domain.AiUsage
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Preferences that stay on this phone and aren't part of the ledger. */
class AppSettings(context: Context) {

    private val store = context.applicationContext.appSettings

    /** Whether closing a sheet with unsaved input asks first (backlog item 2). On unless turned off. */
    val askBeforeDiscarding: Flow<Boolean> = store.data.map { it[ASK_BEFORE_DISCARDING] ?: true }

    suspend fun setAskBeforeDiscarding(ask: Boolean) {
        store.edit { it[ASK_BEFORE_DISCARDING] = ask }
    }

    /** The daily "nothing logged today" reminder. On at 9 PM unless changed; see ReminderSettings. */
    val reminder: Flow<ReminderSettings> = store.data.map { prefs ->
        ReminderSettings(
            enabled = prefs[REMINDER_ENABLED] ?: true,
            minuteOfDay = ReminderSettings.clampMinute(prefs[REMINDER_MINUTE] ?: ReminderSettings.DEFAULT_MINUTE_OF_DAY),
        )
    }

    suspend fun setReminderEnabled(enabled: Boolean) {
        store.edit { it[REMINDER_ENABLED] = enabled }
    }

    suspend fun setReminderMinute(minuteOfDay: Int) {
        store.edit { it[REMINDER_MINUTE] = ReminderSettings.clampMinute(minuteOfDay) }
    }

    /**
     * Whether the app has already asked for notification permission on its own. It asks once, after
     * the first entry; after that only the settings row asks, so a "no" isn't met with the same
     * dialog on every launch.
     */
    val notificationPermissionAsked: Flow<Boolean> = store.data.map { it[NOTIFICATION_PERMISSION_ASKED] ?: false }

    suspend fun markNotificationPermissionAsked() {
        store.edit { it[NOTIFICATION_PERMISSION_ASKED] = true }
    }

    /** This account's UPI ID and payee name (backlog item 9). Per account, since it's theirs to be paid on. */
    fun upiPayee(userId: String): Flow<UpiPayee?> = store.data.map { prefs ->
        val id = prefs[upiIdKey(userId)]
        val name = prefs[upiNameKey(userId)]
        if (id != null && name != null) UpiPayee(id, name) else null
    }

    suspend fun setUpiPayee(userId: String, payee: UpiPayee?) {
        store.edit { prefs ->
            if (payee == null) {
                prefs.remove(upiIdKey(userId))
                prefs.remove(upiNameKey(userId))
            } else {
                prefs[upiIdKey(userId)] = payee.upiId
                prefs[upiNameKey(userId)] = payee.name
            }
        }
    }

    /** Removes what's kept here for [userId] alone, after that account was deleted (plan T1.1). */
    suspend fun clearAccount(userId: String) {
        store.edit { prefs ->
            prefs.remove(upiIdKey(userId))
            prefs.remove(upiNameKey(userId))
            prefs.remove(categoryPrefsKey(userId))
        }
    }

    /** This account's categories and payment methods (backlog item 7). Per account, on this phone. */
    fun categoryPrefs(userId: String): Flow<CategoryPrefs> = store.data.map { prefs ->
        prefs[categoryPrefsKey(userId)]?.let { runCatching { json.decodeFromString(CategoryPrefs.serializer(), it) }.getOrNull() } ?: CategoryPrefs()
    }

    suspend fun updateCategoryPrefs(userId: String, change: (CategoryPrefs) -> CategoryPrefs) {
        store.edit { prefs ->
            val current = prefs[categoryPrefsKey(userId)]?.let { runCatching { json.decodeFromString(CategoryPrefs.serializer(), it) }.getOrNull() } ?: CategoryPrefs()
            prefs[categoryPrefsKey(userId)] = json.encodeToString(CategoryPrefs.serializer(), change(current))
        }
    }

    /**
     * How many AI answers have been generated today (backlog item 16).
     *
     * A backstop against a loop or a stuck retry rather than a budget — the key is on a free tier
     * for this volume. Stored as the day plus a count, so it resets by itself when the date moves
     * on and needs no scheduled clean-up.
     */
    val aiUsage: Flow<AiUsage> = store.data.map { prefs ->
        val day = prefs[AI_USAGE_DAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (day == null) AiUsage.none else AiUsage(day, prefs[AI_USAGE_COUNT] ?: 0)
    }

    /** Records one answer against [today], rolling the count over if the day has changed. */
    suspend fun recordAiAsk(today: LocalDate) {
        store.edit { prefs ->
            val day = prefs[AI_USAGE_DAY]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val next = (if (day == null) AiUsage.none else AiUsage(day, prefs[AI_USAGE_COUNT] ?: 0)).afterAsking(today)
            prefs[AI_USAGE_DAY] = next.day.toString()
            prefs[AI_USAGE_COUNT] = next.used
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val AI_USAGE_DAY = stringPreferencesKey("ai_usage_day")
        val AI_USAGE_COUNT = intPreferencesKey("ai_usage_count")
        fun categoryPrefsKey(userId: String) = stringPreferencesKey("category_prefs_$userId")
        val ASK_BEFORE_DISCARDING = booleanPreferencesKey("ask_before_discarding")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute_of_day")
        val NOTIFICATION_PERMISSION_ASKED = booleanPreferencesKey("notification_permission_asked")
        fun upiIdKey(userId: String) = stringPreferencesKey("upi_id_$userId")
        fun upiNameKey(userId: String) = stringPreferencesKey("upi_name_$userId")
    }
}
