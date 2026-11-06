package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pull-to-refresh spinner follows the real sync instead of a fixed five seconds. */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncRefreshTest {

    private fun set(state: androidx.compose.runtime.MutableState<Boolean>, value: Boolean) {
        state.value = value
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `lets go shortly when no sync starts`() = runTest {
        val syncing = mutableStateOf(false)
        assertEquals(RequestedSync.NeverStarted, awaitRequestedSync { syncing.value })
        assertEquals(SYNC_START_WAIT_MS, currentTime)
    }

    @Test
    fun `waits for a sync that starts late and stops when it finishes`() = runTest {
        val syncing = mutableStateOf(false)
        var done = false
        var seen: RequestedSync? = null
        launch { seen = awaitRequestedSync { syncing.value }; done = true }
        runCurrent()
        advanceTimeBy(500); set(syncing, true); runCurrent()
        advanceTimeBy(3_000); runCurrent()
        assertFalse("still syncing, the spinner stays", done)
        set(syncing, false); runCurrent()
        assertTrue(done)
        assertEquals(RequestedSync.Finished, seen)
        assertEquals(3_500L, currentTime)
    }

    @Test
    fun `a sync already running holds the spinner until it ends`() = runTest {
        val syncing = mutableStateOf(true)
        var done = false
        launch { awaitRequestedSync { syncing.value }; done = true }
        advanceTimeBy(2_000); runCurrent()
        assertFalse(done)
        set(syncing, false); runCurrent()
        assertTrue(done)
    }

    @Test
    fun `a stuck sync never holds the spinner forever`() = runTest {
        val syncing = mutableStateOf(true)
        assertEquals(RequestedSync.StillRunning, awaitRequestedSync { syncing.value })
        assertEquals(SYNC_FINISH_WAIT_MS, currentTime)
    }
}
