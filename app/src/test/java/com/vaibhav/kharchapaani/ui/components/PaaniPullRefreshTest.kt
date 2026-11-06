package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pull asks for one sync and then says what actually happened, not a fixed spinner. The line is
 * read through the indicator's description, which is what TalkBack announces (a polite live region).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaaniPullRefreshTest {
    @get:Rule val compose = createComposeRule()

    private var status by mutableStateOf(SyncStatus())
    private var requests = 0

    private fun show() {
        compose.setContent {
            SplitFlowTheme {
                PaaniPullRefresh(syncStatus = status, onRequestSync = { requests++ }, tagPrefix = "test") {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) {
                        items(30) { Text("Row $it") }
                    }
                }
            }
        }
        // The ripple never ends, so the clock is driven by hand rather than waiting for idle.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
    }

    private fun pull() {
        compose.onNodeWithTag("list").performTouchInput { swipeDown(startY = top + 10f, endY = bottom, durationMillis = 400) }
        compose.mainClock.advanceTimeBy(500)
    }

    @Test fun `nothing is drawn above the list until someone pulls`() {
        show()
        compose.onNodeWithTag("test_pull_refresh_indicator").assertDoesNotExist()
    }

    @Test fun `a finished sync ends on all caught up`() {
        show()
        pull()
        assertEquals(1, requests)
        compose.onNodeWithContentDescription("Syncing…").assertExists()

        status = status.copy(isSyncing = true)
        compose.mainClock.advanceTimeBy(300)
        status = status.copy(isSyncing = false, lastSyncedAt = 1L)
        compose.mainClock.advanceTimeBy(300)

        compose.onNodeWithContentDescription("All caught up").assertExists()
    }

    @Test fun `a sync that never starts says the entries are safe on the phone`() {
        show()
        pull()
        compose.mainClock.advanceTimeBy(SYNC_START_WAIT_MS + 200)
        compose.onNodeWithContentDescription("Saved on this phone. It'll sync when it can.").assertExists()
    }

    @Test fun `changes that were refused are counted, not hidden`() {
        show()
        pull()
        status = status.copy(isSyncing = true)
        compose.mainClock.advanceTimeBy(300)
        status = status.copy(isSyncing = false, failedChanges = 2)
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithContentDescription("2 changes couldn't sync").assertExists()
    }

    @Test fun `the result line goes away after a moment`() {
        show()
        pull()
        compose.mainClock.advanceTimeBy(SYNC_START_WAIT_MS + 200)
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("test_pull_refresh_indicator").assertDoesNotExist()
    }

    @Test fun `the list follows the finger past the threshold, but less and less`() {
        assertEquals(0f, pullResistance(-0.2f))
        assertEquals(0.5f, pullResistance(0.5f))
        assertEquals(1f, pullResistance(1f))
        val a = pullResistance(1.5f) - 1f
        val b = pullResistance(2f) - pullResistance(1.5f)
        assertTrue("keeps moving", a > 0f && b > 0f)
        assertTrue("but each step gives less", b < a)
        assertTrue("and never runs away", pullResistance(50f) < 1.5f)
    }
@Test fun `without an account the pull asks for nothing and says there's nothing to sync`() {
        compose.setContent {
            SplitFlowTheme {
                PaaniPullRefresh(syncStatus = status, onRequestSync = { requests++ }, tagPrefix = "test", localOnly = true) {
                    LazyColumn(Modifier.fillMaxSize().testTag("list")) { items(30) { Text("Row $it") } }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(100)
        pull()
        compose.mainClock.advanceTimeBy(600)
        assertEquals(0, requests)
        compose.onNodeWithContentDescription("Everything's on this phone. Nothing to sync.").assertExists()
    }
}