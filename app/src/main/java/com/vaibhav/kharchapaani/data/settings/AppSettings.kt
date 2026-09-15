package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.vaibhav.kharchapaani.domain.UpiPayee
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettings: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

/** Preferences that stay on this phone and aren't part of the ledger. */
class AppSettings(context: Context) {

    private val store = context.applicationContext.appSettings

    /** Whether closing a sheet with unsaved input asks first (backlog item 2). On unless turned off. */
    val askBeforeDiscarding: Flow<Boolean> = store.data.map { it[ASK_BEFORE_DISCARDING] ?: true }

    suspend fun setAskBeforeDiscarding(ask: Boolean) {
        store.edit { it[ASK_BEFORE_DISCARDING] = ask }
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

    private companion object {
        val ASK_BEFORE_DISCARDING = booleanPreferencesKey("ask_before_discarding")
        fun upiIdKey(userId: String) = stringPreferencesKey("upi_id_$userId")
        fun upiNameKey(userId: String) = stringPreferencesKey("upi_name_$userId")
    }
}
