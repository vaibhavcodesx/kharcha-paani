package com.vaibhav.kharchapaani.data.drafts

import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.domain.DraftEditTarget
import com.vaibhav.kharchapaani.domain.EntryDraft
import com.vaibhav.kharchapaani.domain.EntrySaveCheckpoint
import com.vaibhav.kharchapaani.domain.SplitRounding
import com.vaibhav.kharchapaani.ui.format.Money
import org.json.JSONArray
import java.security.MessageDigest

/** Direct Room reads: initial screen StateFlow values cannot validate a recovered money edit. */
internal class EntryDraftRecoveryLedger(private val db: AppDatabase) {
    suspend fun full(id: Long): FullTransaction? {
        val tx = db.transactionDao().getTransactionByIdDirect(id) ?: return null
        val split = db.splitDao().getSplitByTransactionIdDirect(id)
        return FullTransaction(tx, split, split?.let { db.splitDao().getSharesForSplitDirect(it.id) }.orEmpty())
    }

    suspend fun byRemoteId(remoteId: String): FullTransaction? =
        db.transactionDao().getAllTransactionsDirect().firstOrNull { it.remoteId == remoteId }?.let { full(it.id) }

    suspend fun problem(entry: EntryDraft, edit: DraftEditTarget?, locked: List<SplitShareEntity>?): DraftRecoveryIssue? {
        val existing = edit?.let { full(it.transactionId) }
        if (edit != null && (existing == null || existing.transaction.remoteId != edit.remoteId ||
                existing.transaction.createdAt != edit.createdAt || fingerprint(existing) != edit.originalFingerprint || !existing.canEdit)) {
            return DraftRecoveryIssue.StaleEdit
        }
        val group = entry.groupId?.let { db.groupDao().getGroupByIdDirect(it) }
        if (entry.groupId != null && (group == null || group.isShared && group.isArchived)) return DraftRecoveryIssue.GroupUnavailable
        if (existing != null && !existing.transaction.isOwnedByMe &&
            (entry.groupId != existing.transaction.groupId || !entry.isSplit || entry.paidByPersonId != -1L)) return DraftRecoveryIssue.StaleEdit
        if (locked != null && edit != null) {
            val baseline = edit.baseline
            val splitOnly = entry.copy(type = baseline.type, amount = baseline.amount, merchant = baseline.merchant,
                category = baseline.category, paymentMethod = baseline.paymentMethod, note = baseline.note, date = baseline.date)
            if (EntryDraft.isUnsaved(splitOnly, baseline)) return DraftRecoveryIssue.StaleEdit
        }
        if (group?.isShared == true && db.groupDao().getMembersForGroupDirect(group.id).none { it.personId == -1L })
            return DraftRecoveryIssue.GroupUnavailable
        if (!entry.isSplit || entry.type == "income") return null
        val people = db.personDao().getAllPeopleDirect().associateBy { it.id }
        val selected = entry.shares.filter { it.isSelected }
        if (selected.any { it.personId != -1L && it.personId !in people } ||
            entry.paidByPersonId != -1L && entry.paidByPersonId !in people) return DraftRecoveryIssue.MissingPerson
        if (group?.isShared == true) {
            val members = db.groupDao().getMembersForGroupDirect(group.id).map { it.personId }.toSet()
            if (-1L !in members) return DraftRecoveryIssue.GroupUnavailable
            if (selected.any { share ->
                share.personId != -1L && if (share.isGuest) people[share.personId]?.profileId != null
                else share.personId !in members
            } || entry.paidByPersonId != -1L &&
                (entry.paidByPersonId !in members || people[entry.paidByPersonId]?.profileId == null)) return DraftRecoveryIssue.MissingPerson
        } else if (selected.any { it.isGuest }) return DraftRecoveryIssue.GroupUnavailable
        return null
    }

    companion object {
        fun fingerprint(full: FullTransaction): String {
            val t = full.transaction
            val data = JSONArray().put(t.type).put(t.amount).put(t.merchant).put(t.category).put(t.paymentMethod)
                .put(t.note).put(t.date).put(t.groupId).put(t.isSplit).put(t.ownerProfileId)
                .put(full.split?.paidByPersonId).put(full.split?.splitType)
                .put(shareSignature(full.shares))
            return MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }

        private fun shareSignature(shares: List<SplitShareEntity>): String = JSONArray(shares.map {
            JSONArray().put(it.personId).put(it.shareAmount).put(it.percentage).put(it.guestName).put(it.guestPersonId).toString()
        }.sorted()).toString()

        fun shares(entry: EntryDraft, locked: List<SplitShareEntity>?): List<SplitShareEntity> {
            if (locked != null) return locked.map { it.copy(id = 0, splitId = 0) }
            if (!entry.isSplit || entry.type == "income") return emptyList()
            val total = Money.parseInput(entry.amount) ?: return emptyList()
            val selected = entry.shares.filter { it.isSelected && it.amount > 0 }
            val rounded = if (entry.splitMode == SplitMode.PERCENTAGE && selected.isNotEmpty() && selected.sumOf { it.percentage } > 0)
                SplitRounding.byWeights(total, selected.associate { it.personId to it.percentage }, entry.paidByPersonId) else null
            return selected.map { share ->
                val amount = rounded?.get(share.personId) ?: share.amount
                if (share.isGuest) SplitShareEntity(splitId = 0, personId = -1L, shareAmount = amount,
                    percentage = share.percentage, guestName = share.personName, guestPersonId = share.personId)
                else SplitShareEntity(splitId = 0, personId = share.personId, shareAmount = amount, percentage = share.percentage)
            }
        }

        fun transaction(entry: EntryDraft, id: Long, remoteId: String) = TransactionEntity(
            id = id, remoteId = remoteId, type = entry.type, amount = Money.parseInput(entry.amount) ?: 0.0,
            category = entry.category, merchant = entry.merchant.ifBlank { if (entry.type == "income") "Income" else entry.category },
            paymentMethod = entry.paymentMethod, date = entry.date, note = entry.note, groupId = entry.groupId,
            isSplit = entry.isSplit && entry.type != "income",
        )

        fun matches(full: FullTransaction, checkpoint: EntrySaveCheckpoint): Boolean {
            val expected = transaction(checkpoint.entry, full.transaction.id, full.transaction.remoteId)
            val actual = full.transaction
            val inputShares = shares(checkpoint.entry, checkpoint.lockedShares)
            val expectedShares = if (actual.syncState != SyncState.LOCAL_ONLY && inputShares.isNotEmpty()) {
                val keys = inputShares.mapIndexed { index, share -> if (share.isGuest) -1_000_000L - index else share.personId }
                try {
                    val rounded = SplitRounding.normalize(expected.amount, keys.zip(inputShares).associate { (key, share) -> key to share.shareAmount }, checkpoint.entry.paidByPersonId)
                    inputShares.mapIndexed { index, share -> share.copy(shareAmount = rounded.getValue(keys[index])) }
                } catch (_: IllegalArgumentException) { inputShares }
            } else inputShares
            return actual.type == expected.type && actual.amount == expected.amount && actual.category == expected.category &&
                actual.merchant == expected.merchant && actual.paymentMethod == expected.paymentMethod && actual.date == expected.date &&
                actual.note == expected.note && actual.groupId == expected.groupId && actual.isSplit == expectedShares.isNotEmpty() &&
                (expectedShares.isEmpty() || full.split?.let { split ->
                    split.paidByPersonId == checkpoint.entry.paidByPersonId &&
                        split.splitType == checkpoint.entry.splitMode.name.lowercase(java.util.Locale.ROOT) &&
                        shareSignature(full.shares) == shareSignature(expectedShares)
                } == true)
        }
    }
}

internal enum class DraftRecoveryIssue { StaleEdit, MissingPerson, GroupUnavailable, PhotoMissing }
