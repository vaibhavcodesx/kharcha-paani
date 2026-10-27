package com.vaibhav.kharchapaani.data.model

import android.content.Context
import com.vaibhav.kharchapaani.R

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus

/** One row of Recent Activity: an expense or income, or money that changed hands (settlement / IOU). */
sealed interface ActivityItem {
    val key: String
    val date: Long
    val createdAt: Long

    /** [isReadOnly]: a groupmate added it, or its shared group is archived; it can't be edited or deleted here. */
    data class Transaction(val fullTransaction: FullTransaction, val isReadOnly: Boolean = false) : ActivityItem {
        override val key get() = "tx_${fullTransaction.transaction.id}"
        override val date get() = fullTransaction.transaction.date
        override val createdAt get() = fullTransaction.transaction.createdAt
    }

    data class Settlement(
        val settlement: SettlementEntity,
        val fromName: String,
        val toName: String,
        val groupName: String?,
        /** From the actual recorded-by id, not inferred from who paid or received. */
        val recordedByName: String? = null,
    ) : ActivityItem {
        override val key get() = "settlement_${settlement.id}"
        override val date get() = settlement.date
        override val createdAt get() = settlement.createdAt

        val involvesMe: Boolean get() = settlement.fromPersonId == ME || settlement.toPersonId == ME

        /** The other party's name when I'm one of the two, else null. */
        val otherPartyName: String? get() = when (ME) {
            settlement.fromPersonId -> toName
            settlement.toPersonId -> fromName
            else -> null
        }

        /** I recorded it (or it predates confirmation and so needs nothing from anyone). */
        val recordedByMe: Boolean get() = settlement.recordedByPersonId == ME

        /** Someone else recorded it with me as the other party, and I haven't answered. */
        val awaitsMyAnswer: Boolean
            get() = involvesMe && settlement.status == SettlementStatus.PENDING &&
                settlement.recordedByPersonId != null && settlement.recordedByPersonId != ME

        /** The same age threshold, resolved only when a waiting description is displayed. */
        private fun waitedFor(context: Context, now: Long): String {
            val days = ((now - settlement.createdAt) / 86_400_000L).toInt()
            return if (days >= 7) context.getString(R.string.activity_description_waited, days) else ""
        }

        /** Plain words for a settlement that hasn't counted, or null once it has. */
        fun statusLine(context: Context, now: Long = System.currentTimeMillis()): String? = when (settlement.status) {
            SettlementStatus.CONFIRMED -> null
            SettlementStatus.PENDING -> when {
                awaitsMyAnswer -> context.getString(R.string.activity_description_waiting_you)
                recordedByMe -> context.getString(R.string.activity_description_waiting_named,
                    otherPartyName ?: context.getString(R.string.activity_description_them)) + waitedFor(context, now)
                else -> context.getString(R.string.activity_description_waiting)
            }
            SettlementStatus.REJECTED -> {
                val note = settlement.responseNote?.let { context.getString(R.string.activity_description_response, it) }.orEmpty()
                if (recordedByMe) context.getString(R.string.activity_description_rejected_named,
                    otherPartyName ?: context.getString(R.string.activity_description_they), note)
                else context.getString(R.string.activity_description_rejected_you, note)
            }
        }

        /** e.g. "You paid Bob", "Bob lent you", "Carol paid Bob". */
        fun title(context: Context): String {
            val to = if (settlement.toPersonId == ME) context.getString(R.string.activity_description_you_object) else toName
            val from = if (settlement.fromPersonId == ME) context.getString(R.string.activity_description_you) else fromName
            return context.getString(if (settlement.isIou) R.string.activity_description_lent else R.string.activity_description_paid, from, to)
        }

    }

    private companion object {
        const val ME = -1L
    }
}
