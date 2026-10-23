package com.vaibhav.kharchapaani.data.sync

import androidx.work.ListenableWorker.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Covers the worker's decision logic. doWork() itself isn't run here: supabase-kt's auth
 * initialisation never completes under Robolectric (Android startup initializers don't run).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncWorkerTest {

    @Test
    fun `queued work for an account that isn't signed in waits without syncing`() = runBlocking {
        var synced = false

        val signedOut = SyncWorker.syncIfSignedIn("alice", signedInUserId = null) { synced = true }
        val otherAccount = SyncWorker.syncIfSignedIn("alice", signedInUserId = "bob") { synced = true }

        assertEquals(Result.success(), signedOut)
        assertEquals(Result.success(), otherAccount)
        assertFalse(synced)
    }

    @Test
    fun `the signed-in account syncs and network failures retry with backoff`() = runBlocking {
        assertEquals(Result.success(), SyncWorker.syncIfSignedIn("alice", "alice") {})
        assertEquals(Result.retry(), SyncWorker.syncIfSignedIn("alice", "alice") { throw IOException("offline") })
    }

    @Test
    fun `an unexpected state error stops this run instead of retrying forever`() = runBlocking {
        assertEquals(Result.failure(), SyncWorker.syncIfSignedIn("alice", "alice") { error("database closed") })
    }

    @Test
    fun `two passes started together run one after the other`() = runBlocking {
        // The periodic and the one-time work can both start; overlapping, an older upload could land
        // after a newer one and its pull would overwrite the edit.
        val running = AtomicInteger(0)
        val mostAtOnce = AtomicInteger(0)
        val passes = List(4) {
            async(Dispatchers.Default) {
                SyncWorker.syncIfSignedIn("alice", "alice") {
                    mostAtOnce.accumulateAndGet(running.incrementAndGet()) { a, b -> maxOf(a, b) }
                    delay(50)
                    running.decrementAndGet()
                }
            }
        }

        assertEquals(List(4) { Result.success() }, passes.awaitAll())
        assertEquals(1, mostAtOnce.get())
    }
}
