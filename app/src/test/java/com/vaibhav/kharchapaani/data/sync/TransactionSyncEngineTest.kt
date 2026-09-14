package com.vaibhav.kharchapaani.data.sync

import com.vaibhav.kharchapaani.data.db.entity.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// Robolectric only for android.util.Log; nothing here touches SQLite.
@OptIn(ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TransactionSyncEngineTest {

    private val owner = "owner-1"
    private val store = FakeSyncStore()
    private val remote = FakeTransactionRemote(owner)
    private val engine = TransactionSyncEngine(store, remote, owner, clock = { 42L })

    @Test
    fun `pending personal transactions are pushed and marked synced`() = runBlocking {
        val chai = store.add(personalTransaction("Chai", amount = 40.005))

        engine.sync()

        assertEquals(SyncState.SYNCED, store.rows.getValue(chai.id).syncState)
        val uploaded = remote.server.getValue(chai.remoteId)
        assertEquals("Chai", uploaded.merchant)
        assertEquals(40.01, uploaded.amount, 0.0)
        assertEquals("42", store.meta[TransactionSyncEngine.LAST_SYNCED_AT])
    }

    @Test
    fun `local-only rows are never uploaded`() = runBlocking {
        store.add(personalTransaction("Old history", syncState = SyncState.LOCAL_ONLY))
        store.add(personalTransaction("Goa villa").copy(groupId = 7L))
        store.add(personalTransaction("Dinner split").copy(isSplit = true))

        engine.sync()

        assertTrue(remote.server.isEmpty())
    }

    @Test
    fun `the offline queue drains once connectivity returns`() = runBlocking {
        val rows = List(3) { store.add(personalTransaction("Offline $it", updatedAt = it.toLong())) }
        remote.online = false

        runCatching { engine.sync() }.also { assertTrue(it.exceptionOrNull() is IOException) }
        assertTrue(rows.all { store.rows.getValue(it.id).syncState == SyncState.PENDING })

        remote.online = true
        engine.sync()

        assertTrue(rows.all { store.rows.getValue(it.id).syncState == SyncState.SYNCED })
        assertEquals(3, remote.server.size)
    }

    @Test
    fun `an edit made while its push is in flight stays pending and goes up next time`() = runBlocking {
        val row = store.add(personalTransaction("Before edit", updatedAt = 10))
        remote.onUpsert = {
            remote.onUpsert = {}
            store.rows[row.id] = store.rows.getValue(row.id).copy(merchant = "After edit", updatedAt = 11)
        }

        engine.push()

        assertEquals(SyncState.PENDING, store.rows.getValue(row.id).syncState)
        assertEquals("Before edit", remote.server.getValue(row.remoteId).merchant)

        engine.push()

        assertEquals(SyncState.SYNCED, store.rows.getValue(row.id).syncState)
        assertEquals("After edit", remote.server.getValue(row.remoteId).merchant)
    }

    @Test
    fun `a row the server rejects is marked failed and doesn't block the others`() = runBlocking {
        val good = store.add(personalTransaction("Good", updatedAt = 1))
        val bad = store.add(personalTransaction("Bad", updatedAt = 2))
        remote.rejectIds = setOf(bad.remoteId)

        engine.sync()

        assertEquals(SyncState.SYNCED, store.rows.getValue(good.id).syncState)
        assertEquals(SyncState.FAILED, store.rows.getValue(bad.id).syncState)
        assertNotNull(remote.server[good.remoteId])
        assertNull(remote.server[bad.remoteId])

        engine.sync()
        assertEquals("a failed row isn't retried until it's edited or retried", 1, remote.upsertCalls.count { bad.remoteId in it && it.size == 1 })
    }

    @Test
    fun `local deletes reach the server as soft deletes`() = runBlocking {
        val row = store.add(personalTransaction("To delete"))
        engine.sync()
        store.rows.remove(row.id)
        store.pendingDeletes += row.remoteId

        engine.sync()

        assertNotNull(remote.server.getValue(row.remoteId).deletedAt)
        assertTrue(store.pendingDeletes.isEmpty())
        assertNull(store.byRemoteId(row.remoteId))
    }

    @Test
    fun `a delete the server refuses is dropped without blocking other deletes or the rest of sync`() = runBlocking {
        val refused = store.add(personalTransaction("In an archived group"))
        val fine = store.add(personalTransaction("Normal"))
        engine.sync()
        store.rows.remove(refused.id)
        store.rows.remove(fine.id)
        store.pendingDeletes += listOf(refused.remoteId, fine.remoteId)
        remote.refuseDeleteIds = setOf(refused.remoteId)
        store.meta[GroupSyncEngine.GROUP_EXPENSES_PULL_CURSOR] = "2026-09-14T00:00:00Z"
        val pending = store.add(personalTransaction("Queued after"))

        engine.sync()

        assertNotNull("the other delete still went through", remote.server.getValue(fine.remoteId).deletedAt)
        assertNull(remote.server.getValue(refused.remoteId).deletedAt)
        assertTrue("the refused tombstone must not be retried forever", store.pendingDeletes.isEmpty())
        assertNull(
            "group expenses are re-pulled so the refused row comes back where it still exists",
            store.meta[GroupSyncEngine.GROUP_EXPENSES_PULL_CURSOR],
        )
        assertEquals(SyncState.SYNCED, store.rows.getValue(pending.id).syncState)
    }

    @Test
    fun `pull brings in new rows, applies updates, and removes deletes from other devices`() = runBlocking {
        val edited = store.add(personalTransaction("Original"))
        val deleted = store.add(personalTransaction("Will be deleted", updatedAt = 2))
        engine.sync()

        remote.writeFromOtherDevice(remote.server.getValue(edited.remoteId).copy(merchant = "Edited elsewhere"))
        remote.deleteFromOtherDevice(deleted.remoteId)
        val newRow = remote.server.getValue(edited.remoteId).copy(id = "remote-new", merchant = "Added elsewhere")
        remote.writeFromOtherDevice(newRow)

        engine.sync()

        assertEquals("Edited elsewhere", store.byRemoteId(edited.remoteId)?.merchant)
        assertNull(store.byRemoteId(deleted.remoteId))
        val added = store.byRemoteId("remote-new")
        assertEquals("Added elsewhere", added?.merchant)
        assertEquals(SyncState.SYNCED, added?.syncState)
    }

    @Test
    fun `a still-queued local change beats an incoming remote change`() = runBlocking {
        val row = store.add(personalTransaction("Shared start"))
        engine.sync()
        remote.writeFromOtherDevice(remote.server.getValue(row.remoteId).copy(merchant = "Other device"))
        store.rows[row.id] = store.rows.getValue(row.id).copy(merchant = "This device", updatedAt = 99, syncState = SyncState.PENDING)

        engine.pull()
        assertEquals("This device", store.rows.getValue(row.id).merchant)

        engine.sync()
        assertEquals("This device", remote.server.getValue(row.remoteId).merchant)
        assertEquals("This device", store.rows.getValue(row.id).merchant)
    }

    @Test
    fun `a pulled row does not resurrect a transaction deleted here but not yet pushed`() = runBlocking {
        val row = store.add(personalTransaction("Deleted here"))
        engine.sync()
        store.rows.remove(row.id)
        store.pendingDeletes += row.remoteId
        remote.writeFromOtherDevice(remote.server.getValue(row.remoteId).copy(merchant = "Edited elsewhere"))

        engine.pull()

        assertNull(store.byRemoteId(row.remoteId))
    }

    @Test
    fun `pull pages through more than one page and advances the cursor`() = runBlocking {
        val total = TransactionSyncEngine.PULL_PAGE_SIZE + 250
        repeat(total) { i ->
            remote.writeFromOtherDevice(
                RemoteTransaction(
                    id = "r$i", ownerId = owner, type = "expense", amount = 1.0, category = "Food",
                    merchant = "M$i", paymentMethod = "UPI", date = "2026-09-01T00:00:00Z", note = "",
                    createdAt = "2026-09-01T00:00:00Z", updatedAt = "",
                )
            )
        }

        engine.pull()

        assertEquals(total, store.rows.size)
        // ISO strings don't sort chronologically ("…00Z" > "…00.001Z"), so compare as instants.
        val newest = remote.server.values.maxBy { Instant.parse(it.updatedAt) }.updatedAt
        assertEquals(newest, store.meta[TransactionSyncEngine.PULL_CURSOR])

        engine.pull()
        assertEquals("re-pulling the overlap is idempotent", total, store.rows.size)
        assertFalse(store.rows.values.any { it.syncState != SyncState.SYNCED })
    }
}
