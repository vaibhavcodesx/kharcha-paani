package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.vaibhav.kharchapaani.domain.GroupSort

/** A device-wide display choice, kept separate from account and ledger settings. */
internal class GroupSortPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("group_list_preferences", Context.MODE_PRIVATE)

    /** Observe ordinary picker writes and durable restore writes while the screen stays mounted. */
    val sort: Flow<GroupSort> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "sort" || key == null) trySend(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun read(): GroupSort = GroupSort.entries.firstOrNull { it.name == preferences.getString("sort", null) }
        ?: GroupSort.LatestActivity

    /** Recovery journals may be cleared only after this choice is durable. */
    suspend fun writeDurably(sort: GroupSort) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString("sort", sort.name).commit()) { "Could not save group sort" }
    }

    fun write(sort: GroupSort) {
        preferences.edit().putString("sort", sort.name).apply()
    }
}
