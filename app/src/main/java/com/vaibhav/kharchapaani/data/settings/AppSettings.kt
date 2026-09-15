package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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

    private companion object {
        val ASK_BEFORE_DISCARDING = booleanPreferencesKey("ask_before_discarding")
    }
}
