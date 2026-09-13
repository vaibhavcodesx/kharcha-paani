package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStatusTextTest {

    private val now = 10_000_000L
    private fun text(status: SyncStatus) = syncStatusText(status, now) { "5 min. ago" }

    @Test
    fun `failures are reported before anything else`() {
        assertEquals("2 changes couldn't sync", text(SyncStatus(unsyncedChanges = 3, failedChanges = 2, isSyncing = true)))
        assertEquals("1 change couldn't sync", text(SyncStatus(failedChanges = 1)))
    }

    @Test
    fun `in-progress and queued states`() {
        assertEquals("Syncing…", text(SyncStatus(unsyncedChanges = 3, isSyncing = true)))
        assertEquals("1 change waiting to sync", text(SyncStatus(unsyncedChanges = 1)))
        assertEquals("4 changes waiting to sync", text(SyncStatus(unsyncedChanges = 4)))
    }

    @Test
    fun `synced states`() {
        assertEquals("All changes synced", text(SyncStatus(lastSyncedAt = now - 10_000)))
        assertEquals("All changes synced · 5 min. ago", text(SyncStatus(lastSyncedAt = now - 300_000)))
        assertEquals("Not synced yet", text(SyncStatus()))
    }
}
