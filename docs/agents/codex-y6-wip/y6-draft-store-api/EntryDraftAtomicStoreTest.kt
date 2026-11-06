package com.vaibhav.kharchapaani.data.drafts

import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.domain.DraftEditTarget
import com.vaibhav.kharchapaani.domain.DraftPhotoState
import com.vaibhav.kharchapaani.domain.EntryDraft
import com.vaibhav.kharchapaani.domain.EntrySaveCheckpoint
import com.vaibhav.kharchapaani.domain.RecoverableEntryDraft
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Actual disk and AtomicFile operations; no fake persistence, Room, VM or Main dispatcher. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryDraftAtomicStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val uuid = UUID.randomUUID().toString()
    private val ledger = "ledger-$uuid.db"
    private val hash = "a".repeat(64)
    private fun entry() = EntryDraft(
        type = "expense", amount = "100.", merchant = "  unfinished merchant  ", category = "Food",
        paymentMethod = "Cash", note = "first line\nsecond line", date = 1_700_000_000_123L,
        groupId = 7, isSplit = true, splitMode = SplitMode.EXACT, paidByPersonId = 1,
        shares = listOf(
            ShareInput(-1, "You", amount = 20.0, percentage = 20.0),
            ShareInput(1, "Rohan", amount = 30.0, percentage = 30.0),
            ShareInput(2, "Guest", amount = 50.0, percentage = 50.0, isGuest = true),
        ),
    )
    private fun snapshot(revision: Long = 1, draftId: String = UUID.randomUUID().toString()) =
        RecoverableEntryDraft(draftId = draftId, revision = revision,
            saveRemoteId = UUID.randomUUID().toString(), entry = entry())

    /** The actual sole durable file after the store completed its write, not a store cache/test hook. */
    private fun durableFile(directory: File): File = directory.walkTopDown()
        .filter { it.isFile && !it.name.endsWith(".bak") && !it.name.endsWith(".new") }.toList().single()

    private suspend fun loaded(store: EntryDraftAtomicStore): RecoverableEntryDraft {
        val result = store.load()
        assertTrue("Expected a real decoded draft, got $result", result is DraftLoadResult.Loaded)
        return (result as DraftLoadResult.Loaded).snapshot
    }

    @Test fun `round trip retains raw fields edit guest locks photo and independent saving checkpoint`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val baseline = entry().copy(amount = "99.00", note = "original saved note")
        val edit = DraftEditTarget(41, UUID.randomUUID().toString(), 1_600_000_000_000L, hash, baseline)
        val locks = listOf(SplitShareEntity(id = 5, splitId = 8, personId = -1,
            shareAmount = 50.0, percentage = 50.0, guestName = "Locked guest", guestPersonId = null))
        val photo = DraftPhotoState.Staged("draft-photo.jpg", 10, 20, 123, hash)
        val before = snapshot(revision = 4).copy(edit = edit, lockedShares = locks,
            groupChoiceIsExplicit = true, hadBillPhotoOnOpen = true, photo = photo,
            savingEntry = EntrySaveCheckpoint(revision = 3, transactionRemoteId = edit.remoteId,
                entry = entry().copy(note = "captured before later typing"), photo = DraftPhotoState.None,
                edit = edit, lockedShares = locks, hadBillPhotoOnOpen = true, groupChoiceIsExplicit = true))
        val first = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        first.save(before).await().getOrThrow()
        first.retire().await().getOrThrow()
        assertTrue(durableFile(directory).length() > 0)
        val second = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        assertEquals(before, loaded(second))
        second.retire().await().getOrThrow()
    }

    @Test fun `income custom choices and unicode raw text survive an actual disk round trip`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val before = snapshot().copy(entry = entry().copy(type = "income", amount = "00012.30",
            category = "Custom â‚¹ income", paymentMethod = "Cash à¤¦à¤¾à¤¨", merchant = "  à¤šà¤¾à¤¯ â˜•  ",
            note = "à¤¨à¥‹à¤Ÿ\nç¬¬äºŒè¡Œ", isSplit = false))
        val first = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        first.save(before).await().getOrThrow()
        first.retire().await().getOrThrow()
        val next = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        assertEquals(before, loaded(next))
        next.retire().await().getOrThrow()
    }

    @Test fun `invalid UTF8 in otherwise intact JSON is rejected without replacement or rewriting`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val marker = "UTF8_DRAFT_MARKER"
        owner.save(snapshot().copy(entry = entry().copy(note = marker))).await().getOrThrow()
        val file = durableFile(directory)
        val bytes = file.readBytes()
        val index = bytes.toString(Charsets.UTF_8).indexOf(marker)
        assertTrue("ASCII-only fixture must expose its note bytes", index >= 0)
        // This fixture is entirely ASCII, so the character offset is also the byte offset.
        bytes[index] = 0xc3.toByte()
        bytes[index + 1] = 0x28.toByte()
        file.writeBytes(bytes)
        assertEquals(DraftLoadResult.Unreadable, owner.load())
        assertArrayEquals(bytes, file.readBytes())
        owner.retire().await().getOrThrow()
    }

    @Test fun `load reads current disk rather than an in memory snapshot`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        owner.save(snapshot()).await().getOrThrow()
        val file = durableFile(directory)
        val corrupt = "not a JSON draft".toByteArray()
        file.writeBytes(corrupt)
        assertEquals(DraftLoadResult.Unreadable, owner.load())
        assertArrayEquals("A bad read must not wipe the recoverable file", corrupt, file.readBytes())
        owner.retire().await().getOrThrow()
    }

    @Test fun `conditional clear compares both identity and revision on disk`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot(revision = 2)
        owner.save(before).await().getOrThrow()
        owner.conditionalClear(UUID.randomUUID().toString(), 2).await().getOrThrow()
        assertEquals(before, loaded(owner))
        owner.conditionalClear(before.draftId, 1).await().getOrThrow()
        assertEquals(before, loaded(owner))
        owner.conditionalClear(before.draftId, 2).await().getOrThrow()
        assertEquals(DraftLoadResult.Missing, owner.load())
        owner.retire().await().getOrThrow()
    }

    @Test fun `old owner accepted write finishes before new owner loads but further old writes fail`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot()
        val accepted = old.save(before)
        assertFalse("Controlled IO queue must still hold the accepted write", accepted.isCompleted)
        val retired = old.retire()
        val fresh = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val reading = async { loaded(fresh) }
        assertFalse(reading.isCompleted)
        runCurrent()
        accepted.await().getOrThrow()
        retired.await().getOrThrow()
        assertEquals(before, reading.await())
        assertTrue(old.save(before.copy(revision = 2, entry = entry().copy(note = "late old owner"))).await().isFailure)
        assertTrue(old.conditionalClear(before.draftId, 1).await().isFailure)
        assertEquals(before, loaded(fresh))
        fresh.retire().await().getOrThrow()
    }

    @Test fun `cancelling the caller cannot cancel an already accepted disk write`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot()
        lateinit var accepted: Deferred<Result<Unit>>
        val caller = launch(start = CoroutineStart.UNDISPATCHED) {
            accepted = owner.save(before)
            accepted.await()
        }
        assertFalse(accepted.isCompleted)
        caller.cancel()
        assertFalse("Accepted work belongs to the store, not its awaiting caller", accepted.isCancelled)
        runCurrent()
        caller.join()
        accepted.await().getOrThrow()
        owner.retire().await().getOrThrow()
        val fresh = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        assertEquals(before, loaded(fresh))
        fresh.retire().await().getOrThrow()
    }

    @Test fun `queued writes clear and replacement draft retain acceptance order`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot()
        val writes = (1L..12L).map { revision ->
            owner.save(before.copy(revision = revision, entry = entry().copy(note = "revision $revision")))
        }
        val clearing = owner.conditionalClear(before.draftId, 12)
        val replacement = snapshot().copy(entry = entry().copy(note = "new draft"))
        val replacementWrite = owner.save(replacement)
        writes.forEach { it.await().getOrThrow() }
        clearing.await().getOrThrow()
        replacementWrite.await().getOrThrow()
        assertEquals(replacement, loaded(owner))
        owner.retire().await().getOrThrow()
    }

    @Test fun `older revisions and a cleared draft cannot resurrect after a successful newer write`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot(revision = 3)
        owner.save(before).await().getOrThrow()
        assertTrue(owner.save(before.copy(revision = 2)).await().isFailure)
        assertEquals(before, loaded(owner))
        owner.conditionalClear(before.draftId, 3).await().getOrThrow()
        assertTrue(owner.save(before.copy(revision = 4)).await().isFailure)
        assertEquals(DraftLoadResult.Missing, owner.load())
        owner.retire().await().getOrThrow()
    }

    @Test fun `malformed and oversized files are reported without modifying their bytes`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        owner.save(snapshot()).await().getOrThrow()
        val file = durableFile(directory)
        for (bytes in listOf("{broken".toByteArray(), ByteArray(EntryDraftAtomicStore.MAX_BYTES + 1) { 32 })) {
            file.writeBytes(bytes)
            assertEquals(DraftLoadResult.Unreadable, owner.load())
            assertArrayEquals(bytes, file.readBytes())
        }
        owner.retire().await().getOrThrow()
    }

    @Test fun `invalid shapes and oversized output leave the last good draft untouched`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot()
        owner.save(before).await().getOrThrow()
        val invalid = listOf(
            before.copy(draftId = "not-a-uuid", revision = 2),
            before.copy(revision = 0),
            before.copy(revision = 2, saveRemoteId = "not-a-uuid"),
            before.copy(revision = 2, entry = entry().copy(note = "x".repeat(EntryDraftAtomicStore.MAX_BYTES))),
            before.copy(revision = 2, entry = entry().copy(shares = entry().shares + entry().shares.first())),
            before.copy(revision = 2, entry = entry().copy(shares = listOf(entry().shares.first().copy(amount = Double.NaN)))),
            before.copy(revision = 2, entry = entry().copy(shares = listOf(entry().shares.first().copy(percentage = Double.POSITIVE_INFINITY)))),
            before.copy(revision = 2, photo = DraftPhotoState.Staged("../other.jpg", 1, 1, 1, hash)),
            before.copy(revision = 2, photo = DraftPhotoState.Staged("/absolute.jpg", 1, 1, 1, hash)),
            before.copy(revision = 2, photo = DraftPhotoState.Staged("sub/file.jpg", 1, 1, 1, hash)),
            before.copy(revision = 2, photo = DraftPhotoState.Staged("file.jpg", 0, 1, 1, hash)),
            before.copy(revision = 2, photo = DraftPhotoState.Staged("file.jpg", 1, 1, 1, "bad-hash")),
            before.copy(revision = 2, edit = DraftEditTarget(41, UUID.randomUUID().toString(), -1, hash, entry())),
            before.copy(revision = 2, edit = DraftEditTarget(0, UUID.randomUUID().toString(), 0, hash, entry())),
            before.copy(revision = 2, edit = DraftEditTarget(41, UUID.randomUUID().toString(), 0, hash,
                entry().copy(shares = listOf(entry().shares.first().copy(amount = Double.NEGATIVE_INFINITY))))),
        )
        for (bad in invalid) {
            assertTrue("Invalid draft must be refused without changing previous disk state", owner.save(bad).await().isFailure)
            assertEquals(before, loaded(owner))
        }
        owner.retire().await().getOrThrow()
    }

    @Test fun `invalid UUID in valid serialized JSON is rejected when loading actual disk`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot()
        owner.save(before).await().getOrThrow()
        val file = durableFile(directory)
        val validJson = file.readText(Charsets.UTF_8)
        assertTrue("The portable snapshot must persist its real draft identity", validJson.contains(before.draftId))
        val badJson = validJson.replace(before.draftId, "not-a-valid-draft-identity")
        file.writeText(badJson, Charsets.UTF_8)
        assertEquals(DraftLoadResult.Unreadable, owner.load())
        assertEquals("Loading invalid metadata must not rewrite or clear it", badJson, file.readText(Charsets.UTF_8))
        owner.retire().await().getOrThrow()
    }

    @Test fun `incomplete raw amount and finite invalid working percentages are preserved`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val before = snapshot().copy(entry = entry().copy(amount = ".",
            shares = listOf(ShareInput(-1, "You", amount = -1.0, percentage = 125.0))))
        owner.save(before).await().getOrThrow()
        assertEquals(before, loaded(owner))
        owner.retire().await().getOrThrow()
    }

    @Test fun `cloud and local ledger names with identical UUID remain independent`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cloud = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        val local = EntryDraftAtomicStore.open(directory, "ledger-local-$uuid.db", dispatcher)
        val first = snapshot().copy(entry = entry().copy(note = "cloud only"))
        val second = snapshot().copy(entry = entry().copy(note = "local only"))
        cloud.save(first).await().getOrThrow()
        assertEquals(DraftLoadResult.Missing, local.load())
        local.save(second).await().getOrThrow()
        assertEquals(first, loaded(cloud))
        assertEquals(second, loaded(local))
        cloud.conditionalClear(first.draftId, first.revision).await().getOrThrow()
        assertEquals(second, loaded(local))
        cloud.retire().await().getOrThrow()
        local.retire().await().getOrThrow()
    }

    @Test fun `explicit quarantine retains corrupt bytes and permits a new recoverable draft`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        owner.save(snapshot()).await().getOrThrow()
        val original = durableFile(directory)
        val corrupt = "{malformed private draft".toByteArray()
        original.writeBytes(corrupt)
        assertEquals(DraftLoadResult.Unreadable, owner.load())
        owner.quarantineUnreadable().await().getOrThrow()
        assertEquals(DraftLoadResult.Missing, owner.load())
        val retained = directory.walkTopDown().filter { it.isFile }.toList().single()
        assertNotEquals(original.canonicalPath, retained.canonicalPath)
        assertArrayEquals(corrupt, retained.readBytes())
        val fresh = snapshot().copy(entry = entry().copy(note = "explicit new entry"))
        owner.save(fresh).await().getOrThrow()
        assertEquals(fresh, loaded(owner))
        assertArrayEquals("Saving fresh work must not discard retained corrupted bytes", corrupt, retained.readBytes())
        owner.retire().await().getOrThrow()
    }

    @Test fun `quarantine does nothing to a valid draft or missing file and stale owner is refused`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val old = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        old.quarantineUnreadable().await().getOrThrow()
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        val before = snapshot()
        old.save(before).await().getOrThrow()
        val bytes = durableFile(directory).readBytes()
        old.quarantineUnreadable().await().getOrThrow()
        assertEquals(before, loaded(old))
        assertEquals(1, directory.walkTopDown().count { it.isFile })
        assertArrayEquals(bytes, durableFile(directory).readBytes())
        val fresh = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        assertTrue(old.quarantineUnreadable().await().isFailure)
        assertEquals(before, loaded(fresh))
        old.retire().await().getOrThrow()
        fresh.retire().await().getOrThrow()
    }

    @Test fun `unknown version stays unreadable and retained until an explicit quarantine`() = runTest(timeout = 10.seconds) {
        val directory = temporary.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val owner = EntryDraftAtomicStore.open(directory, ledger, dispatcher)
        owner.save(snapshot()).await().getOrThrow()
        val file = durableFile(directory)
        val parsed = Json.parseToJsonElement(file.readText()).jsonObject
        val future = JsonObject(parsed + ("version" to JsonPrimitive(999))).toString().toByteArray()
        file.writeBytes(future)
        assertEquals(DraftLoadResult.Unreadable, owner.load())
        assertArrayEquals(future, file.readBytes())
        assertTrue("A new write must not silently replace the unsupported version", owner.save(snapshot()).await().isFailure)
        assertArrayEquals(future, file.readBytes())
        owner.quarantineUnreadable().await().getOrThrow()
        assertEquals(DraftLoadResult.Missing, owner.load())
        assertArrayEquals(future, directory.walkTopDown().filter { it.isFile }.toList().single().readBytes())
        owner.retire().await().getOrThrow()
    }

    @Test fun `ledger key cannot escape its private directory`() {
        val directory = temporary.newFolder()
        for (name in listOf("../ledger.db", "/tmp/ledger.db", "nested/ledger.db", "nested\\ledger.db", "", ".", "..")) {
            assertTrue("Database identity must be a leaf filename", runCatching {
                EntryDraftAtomicStore.open(directory, name)
            }.isFailure)
        }
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
