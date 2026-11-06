# Y6 private draft store APPROVED API (not implementation)

Proposed domain package com.vaibhav.kharchapaani.domain:

```kotlin
data class RecoverableEntryDraft(
    val draftId: String,
    val revision: Long,
    val saveRemoteId: String,
    val entry: EntryDraft,
    val edit: DraftEditTarget? = null,
    val groupChoiceIsExplicit: Boolean = false,
    val lockedShares: List<SplitShareEntity>? = null,
    val hadBillPhotoOnOpen: Boolean = false,
    val photo: DraftPhotoState = DraftPhotoState.None,
    val savingEntry: EntrySaveCheckpoint? = null,
)
data class DraftEditTarget(
    val transactionId: Long,
    val remoteId: String,
    val createdAt: Long,
    val originalFingerprint: String,
    val baseline: EntryDraft,
)
data class EntrySaveCheckpoint(
    val revision: Long,
    val transactionRemoteId: String,
    val entry: EntryDraft,
    val photo: DraftPhotoState,
    val edit: DraftEditTarget? = null,
    val lockedShares: List<SplitShareEntity>? = null,
    val hadBillPhotoOnOpen: Boolean = false,
    val groupChoiceIsExplicit: Boolean = false,
)
sealed interface DraftPhotoState {
    data object None : DraftPhotoState
    data object Existing : DraftPhotoState
    data object Missing : DraftPhotoState
    data class Staged(val fileName: String, val width: Int, val height: Int,
        val bytes: Long, val sha256: String) : DraftPhotoState
}
```

None + hadBillPhotoOnOpen means explicit removal; Existing/Missing resolve only through the original Room edit target. Staged stores safe leaf, dimensions, length and hash, never a path/URI/bitmap. EntryDraft/ShareInput/SplitShareEntity remain ordinary public models; store may use internal DTO/manual Json without shared-model annotations.

Proposed data.drafts package:

```kotlin
sealed interface DraftLoadResult {
    data object Missing : DraftLoadResult
    data object Unreadable : DraftLoadResult
    data class Loaded(val snapshot: RecoverableEntryDraft) : DraftLoadResult
}
class EntryDraftAtomicStore private constructor(...) {
    companion object {
        const val MAX_BYTES: Int = 512 * 1024
        fun open(directory: File, databaseFileName: String,
            ioDispatcher: CoroutineDispatcher = Dispatchers.IO): EntryDraftAtomicStore
    }
    suspend fun load(): DraftLoadResult
    fun save(snapshot: RecoverableEntryDraft): Deferred<Result<Unit>>
    fun conditionalClear(expectedDraftId: String, expectedRevision: Long): Deferred<Result<Unit>>
    fun quarantineUnreadable(): Deferred<Result<Unit>>
    fun retire(): Deferred<Result<Unit>>
}
```

Factory accepts ONLY a database leaf name; same UUID cloud/local actual filenames yield distinct files. Canonical file registry serializes acceptance, ownership changes and disk operations. It owns a SupervisorJob/IO scope independent of VM/caller. New owner retires old handle; all accepted old operations precede new load. Awaiter cancellation does not cancel accepted write. A retired handle rejects new writes/clears. Load awaits accepted tail then reads actual AtomicFile bytes, not an in-memory snapshot cache. ConditionalClear deletes only matching on-disk ID AND revision. Clear/invalid writes cannot resurrect earlier snapshots. Same-ID older revisions are rejected; successful clear ends that draft identity for this live writer. New drafts use new UUIDs. No file contents in diagnostics/errors.

512KiB input and encoded-output cap; unsupported/malformed/oversized content is Unreadable and preserved, never auto-cleared. Save rejects invalid shape/IDs/hashes/nonfinite share amounts/percentages/duplicate participant IDs without damaging prior file. Preserve incomplete raw amount strings and finite but temporarily invalid working numeric inputs; this is not ledger Save validation. Validate baseline/checkpoint too. Draft read/write never touches Room, permissions, auth or AI.

Store tests will use actual temp files + controlled StandardTestDispatcher, no mocked persistence, reflection, sleeps, Room, MainVM or new dependencies. Android AtomicFile implementation requires Robolectric unit runner; tests do not render Compose.

Root approved API including createdAt >= 0 on DraftEditTarget (legacy epoch valid). In-memory Room/null databaseName means VM integration does not create a store. Store accepts actual leaf database filenames; production uses File(application.filesDir, "entry-drafts"). No production implementation yet.

Explicit quarantineUnreadable approved: recheck inside same serialized queue; only unreadable committed bytes move atomically to a unique private quarantine filename, never delete them. Valid/missing is no-op; stale owner refused. VM may invoke only on explicit New Entry/discard, never initialization/load/hidden rewrite. This allows fresh work after a corrupt/unsupported file without losing original bytes.
