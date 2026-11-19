package com.vaibhav.kharchapaani.data.sync

import androidx.work.ListenableWorker.Result
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the current behaviour at three sync corners identified in audit ENG-15.
 * These tests document *what happens today*. Where the comment says "current behaviour", do not
 * fix the behaviour here: S-06 is the Lead's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncCornersTest {

    private val owner = "owner-1"
    private val store = FakeSyncStore()
    private val remote = FakeTransactionRemote(owner)
    private val engine = TransactionSyncEngine(store, remote, owner)

    // -------------------------------------------------------------------------
    // Corner 1 (ENG-15 / audit note): server-deleted row with a pending local edit
    // -------------------------------------------------------------------------

    /**
     * Current behaviour: a server-side delete does NOT remove a locally-edited (PENDING) row.
     * On the next push the row is re-uploaded and therefore "comes back" on the server.
     *
     * This matches the local-wins rule in TransactionSyncEngine.pull(): a pulled deletedAt is only
     * applied when syncState == SYNCED. A PENDING row is intentionally left untouched.
     */
    @Test
    fun `a row deleted on the server while edited locally comes back on the next push`() = runBlocking {
        // Set up: upload the row so it exists on the server.
        val row = store.add(personalTransaction("Coffee"))
        engine.sync()
        assertEquals(SyncState.SYNCED, store.rows.getValue(row.id).syncState)

        // A different device deletes it on the server.
        remote.deleteFromOtherDevice(row.remoteId)
        assertNotNull(remote.server.getValue(row.remoteId).deletedAt)

        // Meanwhile, the user edits the same row locally before the next sync (PENDING again).
        store.rows[row.id] = store.rows.getValue(row.id).copy(merchant = "Edited Coffee", updatedAt = 99, syncState = SyncState.PENDING)

        // Pull: current behaviour — the PENDING local row is NOT deleted.
        engine.pull()
        assertNotNull("PENDING row survives a pulled server delete", store.rows[row.id])
        assertEquals(SyncState.PENDING, store.rows.getValue(row.id).syncState)

        // Next push: the row comes back on the server.
        engine.push()
        assertNull(
            "re-pushed row no longer shows a deletedAt on the server",
            remote.server.getValue(row.remoteId).deletedAt,
        )
        assertEquals("Edited Coffee", remote.server.getValue(row.remoteId).merchant)
    }

    // -------------------------------------------------------------------------
    // Corner 2 (ENG-15): markSyncedIfUnchanged respects one-millisecond edits
    // -------------------------------------------------------------------------

    /**
     * Current behaviour: markSyncedIfUnchanged uses an exact timestamp match.
     * A row whose updatedAt moved by even one millisecond during the push window stays PENDING.
     * A row whose updatedAt did not change is marked SYNCED.
     */
    @Test
    fun `markSyncedIfUnchanged marks synced only when updatedAt is unchanged`() = runBlocking {
        val tBefore = 1_000L
        val tAfter  = 1_001L   // 1 ms later — simulates an edit that arrived mid-push

        val unchanged = store.add(personalTransaction("Chai",    updatedAt = tBefore))
        val edited    = store.add(personalTransaction("Samosa",  updatedAt = tBefore))

        // Simulate: push captured updatedAt = tBefore for both rows, then the user edited "Samosa".
        store.rows[edited.id] = store.rows.getValue(edited.id).copy(updatedAt = tAfter)

        // markSyncedIfUnchanged is called with the snapshot timestamp from before the edit.
        val unchangedMarked = store.markSyncedIfUnchanged(unchanged.id, expectedUpdatedAt = tBefore)
        val editedMarked    = store.markSyncedIfUnchanged(edited.id,    expectedUpdatedAt = tBefore)

        assertEquals("unedited row is marked synced", true, unchangedMarked)
        assertEquals("edited row is NOT marked synced", false, editedMarked)
        assertEquals(SyncState.SYNCED,  store.rows.getValue(unchanged.id).syncState)
        assertEquals(SyncState.PENDING, store.rows.getValue(edited.id).syncState)
    }

    // -------------------------------------------------------------------------
    // Corner 3 (ENG-15): SyncWorker result for a non-network repository exception
    // -------------------------------------------------------------------------

    /**
     * Current behaviour: a non-network, non-state exception from the sync body causes the worker
     * to return Result.retry(), not Result.failure(). This means a corrupt-payload or decoding
     * error is retried on the next scheduled run rather than silently dropped.
     *
     * Do not change this without updating S-06 (the Lead's issue).
     */
    @Test
    fun `SyncWorker retries on a non-network non-state repository exception`() = runBlocking {
        // A ClassCastException from e.g. a serialisation mismatch is not an IOException or
        // IllegalStateException. Current behaviour is retry (not failure).
        val result = SyncWorker.syncIfSignedIn("alice", "alice") {
            throw ClassCastException("unexpected server schema change")
        }
        assertEquals(
            "current behaviour: non-network exception retries rather than failing permanently",
            Result.retry(),
            result,
        )
    }
}
