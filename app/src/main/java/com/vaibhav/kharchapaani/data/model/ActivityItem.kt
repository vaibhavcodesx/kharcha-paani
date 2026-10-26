package com.vaibhav.kharchapaani.data.model

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

        /** " · 9 days" once it has waited a week: the point where withdrawing it is worth offering. */
        private fun waitedFor(now: Long = System.currentTimeMillis()): String {
            val days = ((now - settlement.createdAt) / 86_400_000L).toInt()
            return if (days >= 7) " · $days days. Withdraw it if it's stuck." else ""
        }

        /** Plain words for a settlement that hasn't counted, or null once it has. */
        val statusLine: String?
            get() = when (settlement.status) {
                SettlementStatus.CONFIRMED -> null
                SettlementStatus.PENDING -> when {
                    awaitsMyAnswer -> "Waiting for you to confirm"
                    recordedByMe -> "Waiting for ${otherPartyName ?: "them"} to confirm" + waitedFor()
                    else -> "Waiting to be confirmed"
                }
                SettlementStatus.REJECTED -> {
                    val who = if (recordedByMe) "${otherPartyName ?: "They"} says" else "You said"
                    val note = settlement.responseNote?.let { ": “$it”" }.orEmpty()
                    "$who this isn't right$note"
                }
            }

        /** e.g. "You paid Bob", "Bob lent you", "Carol paid Bob". */
        val title: String
            get() {
                val verb = if (settlement.isIou) "lent" else "paid"
                val to = if (settlement.toPersonId == ME) "you" else toName
                val from = if (settlement.fromPersonId == ME) "You" else fromName
                return "$from $verb $to"
            }
    }

    private companion object {
        const val ME = -1L
    }
}
