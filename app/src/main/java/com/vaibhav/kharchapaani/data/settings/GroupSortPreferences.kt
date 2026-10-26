package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import com.vaibhav.kharchapaani.domain.GroupSort

/** A device-wide display choice, kept separate from account and ledger settings. */
internal class GroupSortPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("group_list_preferences", Context.MODE_PRIVATE)

    fun read(): GroupSort = GroupSort.entries.firstOrNull { it.name == preferences.getString("sort", null) }
        ?: GroupSort.LatestActivity

    fun write(sort: GroupSort) {
        preferences.edit().putString("sort", sort.name).apply()
    }
}
