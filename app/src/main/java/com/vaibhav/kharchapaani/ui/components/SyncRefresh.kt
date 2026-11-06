package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long a pull waits for the requested sync to start before letting go of the spinner. */
internal const val SYNC_START_WAIT_MS = 1_500L

/** The longest a pull keeps its spinner while a sync runs; the sync itself carries on after. */
internal const val SYNC_FINISH_WAIT_MS = 10_000L

/**
 * Holds a pull-to-refresh spinner until the sync it asked for has finished.
 *
 * [isSyncing] must read Compose state (for example a `rememberUpdatedState` holder), so that
 * later changes are seen. When the sync never starts, because the phone is offline, signed out
 * or has nothing to send, the spinner lets go after [SYNC_START_WAIT_MS] instead of hanging.
 */
suspend fun awaitRequestedSync(isSyncing: () -> Boolean) {
    val started = isSyncing() ||
        withTimeoutOrNull(SYNC_START_WAIT_MS) { snapshotFlow(isSyncing).first { it } } != null
    if (started) withTimeoutOrNull(SYNC_FINISH_WAIT_MS) { snapshotFlow(isSyncing).first { !it } }
}
