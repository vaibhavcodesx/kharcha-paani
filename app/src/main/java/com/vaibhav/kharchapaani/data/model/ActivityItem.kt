package com.vaibhav.kharchapaani.data.model

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity

/** One row of Recent Activity: an expense or income, or money that changed hands (settlement / IOU). */
sealed interface ActivityItem {
    val key: String
    val date: Long
    val createdAt: Long

    data class Transaction(val fullTransaction: FullTransaction) : ActivityItem {
        override val key get() = "tx_${fullTransaction.transaction.id}"
        override val date get() = fullTransaction.transaction.date
        override val createdAt get() = fullTransaction.transaction.createdAt
    }

    data class Settlement(
        val settlement: SettlementEntity,
        val fromName: String,
        val toName: String,
        val groupName: String?,
    ) : ActivityItem {
        override val key get() = "settlement_${settlement.id}"
        override val date get() = settlement.date
        override val createdAt get() = settlement.createdAt

        val involvesMe: Boolean get() = settlement.fromPersonId == ME || settlement.toPersonId == ME

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
