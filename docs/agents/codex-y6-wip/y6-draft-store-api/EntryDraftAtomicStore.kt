package com.vaibhav.kharchapaani.data.drafts

import android.util.AtomicFile
import com.vaibhav.kharchapaani.domain.RecoverableEntryDraft
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

sealed interface DraftLoadResult {
    data object Missing : DraftLoadResult
    data object Unreadable : DraftLoadResult
    data class Loaded(val snapshot: RecoverableEntryDraft) : DraftLoadResult
}

/**
 * One owner of a private ledger draft. Acceptance is serialized independently of caller lifetime.
 * The registry retains operation ordering/retirement metadata, never a last-snapshot payload cache.
 */
class EntryDraftAtomicStore private constructor(private val writer: Writer) {
    private var active = true // guarded by registryLock

    suspend fun load(): DraftLoadResult = synchronized(registryLock) {
        if (!ownsWriter()) CompletableDeferred<DraftLoadResult>(DraftLoadResult.Unreadable)
        else writer.enqueue { writer.read() }
    }.await()

    fun save(snapshot: RecoverableEntryDraft): Deferred<Result<Unit>> {
        // List ownership is frozen at acceptance. Encoding, validation and all disk writes stay on IO.
        val frozen = snapshot.frozenCopy()
        return synchronized(registryLock) {
            if (!ownsWriter()) refused() else writer.enqueue {
                operation {
                    val bytes = DraftJson.encode(frozen)
                    require(frozen.draftId !in writer.endedDrafts) { "Draft was already cleared" }
                    val previous = when (val current = writer.read()) {
                        DraftLoadResult.Missing -> null
                        DraftLoadResult.Unreadable -> throw IOException("Existing draft is unreadable")
                        is DraftLoadResult.Loaded -> current.snapshot
                    }
                    if (previous?.draftId == frozen.draftId) {
                        require(frozen.revision >= previous.revision) { "Draft revision is stale" }
                        if (frozen.revision == previous.revision) {
                            require(frozen == previous) { "Draft revision has different contents" }
                            return@operation
                        }
                    }
                    writer.write(bytes)
                    check(writer.read() == DraftLoadResult.Loaded(frozen)) { "Draft write was not verified" }
                    if (previous != null && previous.draftId != frozen.draftId) writer.endedDrafts += previous.draftId
                }
            }
        }
    }

    /** A Save at revision N must never clear typing persisted at N+1. */
    fun conditionalClear(expectedDraftId: String, expectedRevision: Long): Deferred<Result<Unit>> =
        synchronized(registryLock) {
            if (!ownsWriter()) refused() else writer.enqueue {
                operation {
                    DraftJson.requireIdentity(expectedDraftId)
                    require(expectedRevision > 0) { "Invalid draft revision" }
                    when (val current = writer.read()) {
                        DraftLoadResult.Unreadable -> throw IOException("Existing draft is unreadable")
                        DraftLoadResult.Missing -> writer.endedDrafts.add(expectedDraftId)
                        is DraftLoadResult.Loaded -> {
                            if (current.snapshot.draftId == expectedDraftId && current.snapshot.revision == expectedRevision) {
                                writer.atomic.delete()
                                check(!writer.exists()) { "Draft could not be cleared" }
                                writer.endedDrafts += expectedDraftId
                            }
                        }
                    }
                }
            }
        }

    /** Explicit New Entry/discard only: retain unsupported/corrupt bytes instead of overwriting them. */
    fun quarantineUnreadable(): Deferred<Result<Unit>> = synchronized(registryLock) {
        if (!ownsWriter()) refused() else writer.enqueue {
            operation {
                if (writer.read() == DraftLoadResult.Unreadable) {
                    val source = writer.file
                    val destination = File(source.parentFile, source.name.take(160) + ".quarantine-" + UUID.randomUUID())
                    require(source.isFile && !destination.exists()) { "Draft quarantine is unavailable" }
                    // Same-directory rename retains the original bytes atomically without reading them into memory.
                    check(source.renameTo(destination)) { "Draft could not be retained" }
                    check(!writer.exists() && destination.isFile) { "Draft quarantine was not completed" }
                }
            }
        }
    }

    /** Reject future calls immediately; already accepted operations finish before this completes. */
    fun retire(): Deferred<Result<Unit>> = synchronized(registryLock) {
        active = false
        if (writer.owner === this) writer.owner = null
        writer.enqueue { Result.success(Unit) }
    }

    private fun ownsWriter() = active && writer.owner === this
    private fun refused() = CompletableDeferred(Result.failure<Unit>(IllegalStateException("Draft owner has retired")))

    private class Writer(val file: File, dispatcher: CoroutineDispatcher) {
        val atomic = AtomicFile(file)
        private val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var owner: EntryDraftAtomicStore? = null
        var tail: Job? = null
        val endedDrafts = mutableSetOf<String>()

        // Caller holds registryLock, including when installing this job as the accepted tail.
        fun <T> enqueue(block: () -> T): Deferred<T> {
            val previous = tail
            // The registry keeps a Unit barrier, not a result Deferred that could retain a draft.
            val finished = CompletableDeferred<Unit>()
            tail = finished
            // Cancelling the exposed result must not cancel accepted IO or strand the barrier.
            val result = CompletableDeferred<T>()
            scope.launch {
                try {
                    previous?.join() // A failed operation never cancels the next operation.
                    result.complete(block())
                } catch (failure: Exception) {
                    result.completeExceptionally(failure)
                } finally {
                    finished.complete(Unit)
                }
            }
            return result
        }

        fun exists() = file.exists() || File(file.path + ".bak").exists()

        fun read(): DraftLoadResult {
            if (!exists()) return DraftLoadResult.Missing
            return try {
                val bytes = atomic.openRead().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var size = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        require(size <= MAX_BYTES) { "Draft is too large" }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                val text = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
                DraftLoadResult.Loaded(DraftJson.decode(text))
            } catch (_: Exception) {
                // No payload or parse exception is logged, exposed, rewritten or automatically cleared.
                DraftLoadResult.Unreadable
            }
        }

        fun write(bytes: ByteArray) {
            require(bytes.size <= MAX_BYTES) { "Draft is too large" }
            val parent = requireNotNull(file.parentFile)
            check(parent.mkdirs() || parent.isDirectory) { "Draft directory is unavailable" }
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                output.fd.sync()
                atomic.finishWrite(output)
            } catch (failure: Exception) {
                atomic.failWrite(output)
                throw failure
            }
        }
    }

    companion object {
        const val MAX_BYTES = 512 * 1024
        private val registryLock = Any()
        private val writers = mutableMapOf<String, Writer>()

        /** Pass the actual Room database filename; never an account display name or external path. */
        fun open(directory: File, databaseFileName: String,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO): EntryDraftAtomicStore {
            require(databaseFileName.length <= 200 &&
                Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.db").matches(databaseFileName)) { "Invalid ledger filename" }
            val root = directory.canonicalFile
            val file = File(root, "$databaseFileName.draft.json")
            require(file.canonicalFile.parentFile == root &&
                File(file.path + ".bak").canonicalFile.parentFile == root &&
                File(file.path + ".new").canonicalFile.parentFile == root) { "Draft path leaves its private directory" }
            return synchronized(registryLock) {
                val writer = writers.getOrPut(file.canonicalPath) { Writer(file, ioDispatcher) }
                writer.owner?.active = false
                EntryDraftAtomicStore(writer).also { writer.owner = it }
            }
        }

        private inline fun operation(block: () -> Unit): Result<Unit> = try {
            block()
            Result.success(Unit)
        } catch (_: Exception) {
            // Validation/IO failures do not poison the queue or disclose user-controlled strings.
            Result.failure(IOException("Entry draft could not be persisted"))
        }
    }
}
