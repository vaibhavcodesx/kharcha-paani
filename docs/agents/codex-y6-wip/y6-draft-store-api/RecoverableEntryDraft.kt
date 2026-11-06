package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity

/** One device-local add/edit sheet. Never a queued AI request or an automatic ledger write. */
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

/** Bind an edit to the original birth as well as its id, even if a group move changes remoteId. */
data class DraftEditTarget(
    val transactionId: Long,
    val remoteId: String,
    val createdAt: Long,
    val originalFingerprint: String,
    val baseline: EntryDraft,
)

/** Immutable inputs accepted by Save, independent of any typing that follows that tap. */
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

/** None plus hadBillPhotoOnOpen means removal. Existing/Missing are resolved through Room. */
sealed interface DraftPhotoState {
    data object None : DraftPhotoState
    data object Existing : DraftPhotoState
    data object Missing : DraftPhotoState
    /** A leaf inside this ledger's receipt store; bytes/hash are verified by receipt recovery. */
    data class Staged(val fileName: String, val width: Int, val height: Int,
        val bytes: Long, val sha256: String) : DraftPhotoState
}
