package com.vaibhav.kharchapaani.ui.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncBadgeTest {

    @Test
    fun `a failure outranks syncing, which outranks waiting`() {
        assertEquals(SyncBadge.Failed, SyncBadge.of(SyncStatus(unsyncedChanges = 3, failedChanges = 1, isSyncing = true)))
        assertEquals(SyncBadge.Syncing, SyncBadge.of(SyncStatus(unsyncedChanges = 3, isSyncing = true)))
        assertEquals(SyncBadge.Waiting, SyncBadge.of(SyncStatus(unsyncedChanges = 3)))
        assertEquals(SyncBadge.None, SyncBadge.of(SyncStatus(lastSyncedAt = 1L)))
    }

    @Test
    fun `the spoken state matches the account sheet's wording, and is silent when synced`() {
        assertEquals("1 change couldn't sync", SyncBadge.describe(SyncStatus(failedChanges = 1)))
        assertEquals("2 changes waiting to sync", SyncBadge.describe(SyncStatus(unsyncedChanges = 2)))
        assertEquals("Syncing", SyncBadge.describe(SyncStatus(isSyncing = true)))
        assertNull(SyncBadge.describe(SyncStatus()))
    }
}
