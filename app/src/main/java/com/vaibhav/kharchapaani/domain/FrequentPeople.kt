package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction

object FrequentPeople {
    /**
     * Sorts [people] by split frequency with this account (highest split count first),
     * tie-broken by latest split timestamp descending, then by name ascending.
     */
    fun sortByFrequency(
        people: List<PersonEntity>,
        transactions: List<FullTransaction>,
    ): List<PersonEntity> {
        if (people.size <= 1) return people
        val stats = computeStats(transactions)
        return people.sortedWith(
            compareByDescending<PersonEntity> { stats[it.id]?.count ?: 0 }
                .thenByDescending { stats[it.id]?.latestDate ?: 0L }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    private data class PersonStats(val count: Int, val latestDate: Long)

    private fun computeStats(transactions: List<FullTransaction>): Map<Long, PersonStats> {
        val counts = mutableMapOf<Long, Int>()
        val latestDates = mutableMapOf<Long, Long>()

        for (tx in transactions) {
            if (!tx.transaction.isSplit || tx.split == null) continue
            val date = tx.transaction.date
            val personIds = mutableSetOf<Long>()
            tx.split.paidByPersonId.takeIf { it != -1L }?.let { personIds.add(it) }
            for (share in tx.shares) {
                val id = if (share.isGuest) share.guestPersonId else share.personId
                if (id != null && id != -1L) personIds.add(id)
            }
            for (id in personIds) {
                counts[id] = (counts[id] ?: 0) + 1
                val currentLatest = latestDates[id] ?: 0L
                if (date > currentLatest) latestDates[id] = date
            }
        }

        return counts.mapValues { (id, count) ->
            PersonStats(count, latestDates[id] ?: 0L)
        }
    }
}
