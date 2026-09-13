package com.vaibhav.kharchapaani.ui.sync

data class SyncStatus(
    /** Pending uploads plus queued deletes. */
    val unsyncedChanges: Int = 0,
    val failedChanges: Int = 0,
    val lastSyncedAt: Long? = null,
    val isSyncing: Boolean = false,
)
