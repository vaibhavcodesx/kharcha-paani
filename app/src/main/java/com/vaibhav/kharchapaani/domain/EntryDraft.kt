package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import kotlin.math.roundToLong

/** What the quick-add sheet holds, to tell whether closing it would lose anything. */
data class EntryDraft(
    val type: String,
    val amount: String,
    val merchant: String,
    val category: String,
    val paymentMethod: String,
    val note: String,
    val date: Long,
    val groupId: Long?,
    val isSplit: Boolean,
    val splitMode: SplitMode,
    val paidByPersonId: Long,
    val shares: List<ShareInput>,
) {
    companion object {
        /**
         * A new entry is unsaved once something's been typed or read into it: an amount, a name or a
         * note. Its defaults (type, category, today) aren't worth a question. An edit is unsaved once
         * it differs from the expense as it was.
         */
        fun isUnsaved(current: EntryDraft, original: EntryDraft?): Boolean =
            if (original == null) {
                current.amount.isNotBlank() || current.merchant.isNotBlank() || current.note.isNotBlank()
            } else {
                current.comparable() != original.comparable()
            }

        private fun EntryDraft.comparable() = listOf(
            type,
            amount.toDoubleOrNull()?.let { (it * 100).roundToLong() } ?: amount.trim(),
            merchant.trim(),
            category,
            paymentMethod,
            note.trim(),
            date,
            groupId,
            isSplit,
            if (isSplit) splitComparable() else null,
        )

        // Only what the split mode lets someone change: equal shares are derived from who's in.
        private fun EntryDraft.splitComparable() = listOf(
            splitMode,
            paidByPersonId,
            shares.filter { it.isSelected }.map { share ->
                when (splitMode) {
                    SplitMode.EQUAL -> share.personId to null
                    SplitMode.EXACT -> share.personId to (share.amount * 100).roundToLong()
                    SplitMode.PERCENTAGE -> share.personId to (share.percentage * 100).roundToLong()
                }
            }.sortedBy { it.first },
        )
    }
}
