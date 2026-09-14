package com.vaibhav.kharchapaani.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncDoorbellTest {

    @Test
    fun `a burst of changes rings once after things go quiet`() = runTest {
        val events = MutableSharedFlow<Unit>()
        var rings = 0
        val job = launch { SyncDoorbell(changes = { events }, ring = { rings++ }, onRetry = { _, _ -> }).run() }
        runCurrent()

        repeat(5) {
            events.emit(Unit)
            advanceTimeBy(500)
        }
        assertEquals("still inside the quiet period", 0, rings)

        advanceTimeBy(2_001)
        assertEquals(1, rings)

        events.emit(Unit)
        advanceTimeBy(2_001)
        assertEquals(2, rings)
        job.cancel()
    }

    @Test
    fun `a failed subscription is retried with growing waits, and events after it still ring`() = runTest {
        var subscriptions = 0
        var rings = 0
        val waits = mutableListOf<Long>()
        val job = launch {
            SyncDoorbell(
                changes = {
                    flow {
                        subscriptions++
                        if (subscriptions <= 2) throw IOException("socket closed")
                        emit(Unit)
                    }
                },
                ring = { rings++ },
                onRetry = { _, wait -> waits += wait.inWholeSeconds },
            ).run()
        }

        advanceTimeBy(10.seconds.inWholeMilliseconds)

        assertEquals(3, subscriptions)
        assertEquals(listOf(1L, 2L), waits)
        assertEquals(1, rings)
        job.cancel()
    }
}
