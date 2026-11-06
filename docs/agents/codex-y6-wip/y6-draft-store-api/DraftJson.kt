package com.vaibhav.kharchapaani.data.drafts

import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

/** Internal wire DTOs keep existing ledger/draft models free of persistence annotations. */
internal object DraftJson {
    private val json = Json { encodeDefaults = true }
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val hash = Regex("[0-9a-f]{64}")

    fun requireIdentity(value: String) = require(uuid.matches(value)) { "Invalid draft identity" }

    fun encode(snapshot: RecoverableEntryDraft): ByteArray {
        validate(snapshot)
        val text = json.encodeToString(Envelope.serializer(), Envelope(1, SnapshotPayload.from(snapshot)))
        require(text.length <= EntryDraftAtomicStore.MAX_BYTES) { "Draft is too large" }
        val encoded = Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(CharBuffer.wrap(text))
        require(encoded.remaining() <= EntryDraftAtomicStore.MAX_BYTES) { "Draft is too large" }
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    fun decode(text: String): RecoverableEntryDraft {
        require(text.length <= EntryDraftAtomicStore.MAX_BYTES) { "Draft is too large" }
        // Avoid recursively parsing attacker/corruption-created nesting up to the whole byte cap.
        var depth = 0
        var quoted = false
        var escaped = false
        for (char in text) {
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
            } else when (char) {
                '"' -> quoted = true
                '{', '[' -> { depth++; require(depth <= 32) { "Draft nesting is too deep" } }
                '}', ']' -> { depth--; require(depth >= 0) { "Invalid draft nesting" } }
            }
        }
        val envelope = json.decodeFromString(Envelope.serializer(), text)
        require(envelope.version == 1) { "Unsupported draft version" }
        return envelope.snapshot.domain().also(::validate)
    }

    private fun validate(snapshot: RecoverableEntryDraft) {
        var characters = 0L
        fun text(value: String) {
            characters += value.length
            require(characters <= EntryDraftAtomicStore.MAX_BYTES) { "Draft is too large" }
            var index = 0
            while (index < value.length) {
                val char = value[index]
                if (Character.isHighSurrogate(char)) {
                    require(index + 1 < value.length && Character.isLowSurrogate(value[index + 1])) { "Invalid draft text encoding" }
                    index++
                } else require(!Character.isLowSurrogate(char)) { "Invalid draft text encoding" }
                index++
            }
        }
        fun fields(entry: EntryDraft) {
            listOf(entry.type, entry.amount, entry.merchant, entry.category, entry.paymentMethod, entry.note).forEach(::text)
            require(entry.shares.size <= EntryDraftAtomicStore.MAX_BYTES / 16) { "Too many draft participants" }
            entry.shares.forEach { text(it.personName) }
        }
        fun editFields(edit: DraftEditTarget?) { edit?.let { fields(it.baseline) } }
        fun lockedFields(shares: List<SplitShareEntity>?) {
            require((shares?.size ?: 0) <= EntryDraftAtomicStore.MAX_BYTES / 16) { "Too many locked shares" }
            shares?.forEach { it.guestName?.let(::text) }
        }
        fields(snapshot.entry); editFields(snapshot.edit); lockedFields(snapshot.lockedShares)
        snapshot.savingEntry?.let { fields(it.entry); editFields(it.edit); lockedFields(it.lockedShares) }
        requireIdentity(snapshot.draftId)
        requireIdentity(snapshot.saveRemoteId)
        require(snapshot.revision > 0) { "Invalid draft revision" }
        validateEntry(snapshot.entry)
        snapshot.edit?.let(::validateEdit)
        snapshot.lockedShares?.let(::validateLocked)
        validatePhoto(snapshot.photo)
        snapshot.savingEntry?.let {
            require(it.revision in 1..snapshot.revision) { "Invalid save checkpoint revision" }
            requireIdentity(it.transactionRemoteId)
            validateEntry(it.entry)
            it.edit?.let(::validateEdit)
            it.lockedShares?.let(::validateLocked)
            validatePhoto(it.photo)
        }
    }

    private fun person(id: Long) = require(id == -1L || id > 0) { "Invalid participant id" }

    private fun validateEntry(entry: EntryDraft) {
        require(entry.type in setOf("expense", "income")) { "Invalid draft type" }
        require(entry.groupId == null || entry.groupId > 0) { "Invalid draft group" }
        person(entry.paidByPersonId)
        require(entry.shares.map { it.personId }.toSet().size == entry.shares.size) { "Duplicate draft participants" }
        entry.shares.forEach {
            person(it.personId)
            // Working input can be incomplete or temporarily out of range, but never NaN/infinite.
            require(it.amount.isFinite() && it.percentage.isFinite()) { "Invalid draft share number" }
        }
    }

    private fun validateEdit(edit: DraftEditTarget) {
        require(edit.transactionId > 0 && edit.createdAt >= 0) { "Invalid edit target" }
        requireIdentity(edit.remoteId)
        require(hash.matches(edit.originalFingerprint)) { "Invalid edit fingerprint" }
        validateEntry(edit.baseline)
    }

    private fun validateLocked(shares: List<SplitShareEntity>) {
        require(shares.map { it.id }.toSet().size == shares.size) { "Duplicate locked share identity" }
        shares.forEach {
            require(it.id > 0 && it.splitId > 0) { "Invalid locked share identity" }
            person(it.personId)
            it.guestPersonId?.let(::person)
            require(it.shareAmount.isFinite() && (it.percentage == null || it.percentage.isFinite())) { "Invalid locked share number" }
        }
    }

    private fun validatePhoto(photo: DraftPhotoState) {
        if (photo is DraftPhotoState.Staged) {
            require(photo.fileName.length in 1..128 && photo.fileName != "." && photo.fileName != ".." &&
                photo.fileName.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "._-" }) { "Invalid draft photo leaf" }
            require(photo.width in 1..2000 && photo.height in 1..2000 && photo.bytes > 0) { "Invalid draft photo metadata" }
            require(hash.matches(photo.sha256)) { "Invalid draft photo hash" }
        }
    }
}

private fun EntryDraft.frozen() = copy(shares = shares.toList())
private fun DraftEditTarget.frozen() = copy(baseline = baseline.frozen())
internal fun RecoverableEntryDraft.frozenCopy(): RecoverableEntryDraft = copy(
    entry = entry.frozen(),
    edit = edit?.frozen(),
    lockedShares = lockedShares?.toList(),
    savingEntry = savingEntry?.let { checkpoint -> checkpoint.copy(
        entry = checkpoint.entry.frozen(), edit = checkpoint.edit?.frozen(),
        lockedShares = checkpoint.lockedShares?.toList(),
    ) },
)

@Serializable
private data class Envelope(val version: Int, val snapshot: SnapshotPayload)

@Serializable
private data class EntryPayload(
    val type: String,
    val amount: String,
    val merchant: String,
    val category: String,
    val paymentMethod: String,
    val note: String,
    val date: Long,
    val groupId: Long?,
    val isSplit: Boolean,
    val splitMode: String,
    val paidByPersonId: Long,
    val shares: List<SharePayload>,
) {
    fun domain() = EntryDraft(
        type = type,
        amount = amount,
        merchant = merchant,
        category = category,
        paymentMethod = paymentMethod,
        note = note,
        date = date,
        groupId = groupId,
        isSplit = isSplit,
        splitMode = SplitMode.valueOf(splitMode),
        paidByPersonId = paidByPersonId,
        shares = shares.map { it.domain() },
    )
    companion object {
        fun from(s: EntryDraft) = EntryPayload(
            type = s.type,
            amount = s.amount,
            merchant = s.merchant,
            category = s.category,
            paymentMethod = s.paymentMethod,
            note = s.note,
            date = s.date,
            groupId = s.groupId,
            isSplit = s.isSplit,
            splitMode = s.splitMode.name,
            paidByPersonId = s.paidByPersonId,
            shares = s.shares.map(SharePayload::from),
        )
    }
}

@Serializable
private data class SharePayload(
    val personId: Long,
    val personName: String,
    val amount: Double,
    val percentage: Double,
    val isSelected: Boolean,
    val isGuest: Boolean,
) {
    fun domain() = ShareInput(
        personId = personId,
        personName = personName,
        amount = amount,
        percentage = percentage,
        isSelected = isSelected,
        isGuest = isGuest,
    )
    companion object {
        fun from(s: ShareInput) = SharePayload(
            personId = s.personId,
            personName = s.personName,
            amount = s.amount,
            percentage = s.percentage,
            isSelected = s.isSelected,
            isGuest = s.isGuest,
        )
    }
}

@Serializable
private data class LockedPayload(
    val id: Long,
    val splitId: Long,
    val personId: Long,
    val shareAmount: Double,
    val percentage: Double?,
    val guestName: String?,
    val guestPersonId: Long?,
) {
    fun domain() = SplitShareEntity(
        id = id,
        splitId = splitId,
        personId = personId,
        shareAmount = shareAmount,
        percentage = percentage,
        guestName = guestName,
        guestPersonId = guestPersonId,
    )
    companion object {
        fun from(s: SplitShareEntity) = LockedPayload(
            id = s.id,
            splitId = s.splitId,
            personId = s.personId,
            shareAmount = s.shareAmount,
            percentage = s.percentage,
            guestName = s.guestName,
            guestPersonId = s.guestPersonId,
        )
    }
}

@Serializable
private data class EditPayload(
    val transactionId: Long,
    val remoteId: String,
    val createdAt: Long,
    val originalFingerprint: String,
    val baseline: EntryPayload,
) {
    fun domain() = DraftEditTarget(
        transactionId = transactionId,
        remoteId = remoteId,
        createdAt = createdAt,
        originalFingerprint = originalFingerprint,
        baseline = baseline.domain(),
    )
    companion object {
        fun from(s: DraftEditTarget) = EditPayload(
            transactionId = s.transactionId,
            remoteId = s.remoteId,
            createdAt = s.createdAt,
            originalFingerprint = s.originalFingerprint,
            baseline = EntryPayload.from(s.baseline),
        )
    }
}

@Serializable
private data class SavePayload(
    val revision: Long,
    val transactionRemoteId: String,
    val entry: EntryPayload,
    val photo: PhotoPayload,
    val edit: EditPayload?,
    val lockedShares: List<LockedPayload>?,
    val hadBillPhotoOnOpen: Boolean,
    val groupChoiceIsExplicit: Boolean,
) {
    fun domain() = EntrySaveCheckpoint(
        revision = revision,
        transactionRemoteId = transactionRemoteId,
        entry = entry.domain(),
        photo = photo.domain(),
        edit = edit?.domain(),
        lockedShares = lockedShares?.map { it.domain() },
        hadBillPhotoOnOpen = hadBillPhotoOnOpen,
        groupChoiceIsExplicit = groupChoiceIsExplicit,
    )
    companion object {
        fun from(s: EntrySaveCheckpoint) = SavePayload(
            revision = s.revision,
            transactionRemoteId = s.transactionRemoteId,
            entry = EntryPayload.from(s.entry),
            photo = PhotoPayload.from(s.photo),
            edit = s.edit?.let(EditPayload::from),
            lockedShares = s.lockedShares?.map(LockedPayload::from),
            hadBillPhotoOnOpen = s.hadBillPhotoOnOpen,
            groupChoiceIsExplicit = s.groupChoiceIsExplicit,
        )
    }
}

@Serializable
private data class SnapshotPayload(
    val draftId: String,
    val revision: Long,
    val saveRemoteId: String,
    val entry: EntryPayload,
    val edit: EditPayload?,
    val groupChoiceIsExplicit: Boolean,
    val lockedShares: List<LockedPayload>?,
    val hadBillPhotoOnOpen: Boolean,
    val photo: PhotoPayload,
    val savingEntry: SavePayload?,
) {
    fun domain() = RecoverableEntryDraft(
        draftId = draftId,
        revision = revision,
        saveRemoteId = saveRemoteId,
        entry = entry.domain(),
        edit = edit?.domain(),
        groupChoiceIsExplicit = groupChoiceIsExplicit,
        lockedShares = lockedShares?.map { it.domain() },
        hadBillPhotoOnOpen = hadBillPhotoOnOpen,
        photo = photo.domain(),
        savingEntry = savingEntry?.domain(),
    )
    companion object {
        fun from(s: RecoverableEntryDraft) = SnapshotPayload(
            draftId = s.draftId,
            revision = s.revision,
            saveRemoteId = s.saveRemoteId,
            entry = EntryPayload.from(s.entry),
            edit = s.edit?.let(EditPayload::from),
            groupChoiceIsExplicit = s.groupChoiceIsExplicit,
            lockedShares = s.lockedShares?.map(LockedPayload::from),
            hadBillPhotoOnOpen = s.hadBillPhotoOnOpen,
            photo = PhotoPayload.from(s.photo),
            savingEntry = s.savingEntry?.let(SavePayload::from),
        )
    }
}

@Serializable
private data class PhotoPayload(
    val kind: String,
    val fileName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bytes: Long? = null,
    val sha256: String? = null,
) {
    fun domain(): DraftPhotoState {
        if (kind != "STAGED") require(fileName == null && width == null && height == null && bytes == null && sha256 == null) { "Invalid photo intent" }
        return when (kind) {
            "NONE" -> DraftPhotoState.None
            "EXISTING" -> DraftPhotoState.Existing
            "MISSING" -> DraftPhotoState.Missing
            "STAGED" -> DraftPhotoState.Staged(requireNotNull(fileName), requireNotNull(width),
                requireNotNull(height), requireNotNull(bytes), requireNotNull(sha256))
            else -> throw IllegalArgumentException("Invalid photo intent")
        }
    }
    companion object {
        fun from(photo: DraftPhotoState) = when (photo) {
            DraftPhotoState.None -> PhotoPayload("NONE")
            DraftPhotoState.Existing -> PhotoPayload("EXISTING")
            DraftPhotoState.Missing -> PhotoPayload("MISSING")
            is DraftPhotoState.Staged -> PhotoPayload("STAGED", photo.fileName, photo.width,
                photo.height, photo.bytes, photo.sha256)
        }
    }
}
