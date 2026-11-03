package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** A reversible review hint: matching never removes a statement row or changes the ledger. */
object StatementReimports {
    private val spaces = Regex("\\s+")
    private data class Key(val type: String, val paisa: Long, val merchant: String)

    fun review(
        statement: StatementParseResult,
        ledger: List<TransactionEntity>,
        alreadyLabel: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): StatementParseResult {
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        fun key(type: String, amount: Double, merchant: String) = Key(
            type.trim().lowercase(Locale.ROOT),
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact(),
            merchant.trim().replace(spaces, " ").lowercase(Locale.ROOT),
        )
        val inventory = ledger.filter { it.isOwnedByMe && isSavableAmount(it.amount) }
            .groupBy { key(it.type, it.amount, it.merchant) }
            .mapValues { (_, rows) -> rows.map { day(it.date) }.toMutableList() }
        val matched = BooleanArray(statement.allItems.size)

        // Reserve every exact date first. An earlier neighbouring row must not consume a
        // saved payment that belongs to a later exact row. Each saved row is used only once.
        for (exactOnly in listOf(true, false)) {
            statement.allItems.forEachIndexed { index, item ->
                if (matched[index] || !isSavableAmount(item.amount)) return@forEachIndexed
                val dates = inventory[key(item.type, item.amount, item.merchant)] ?: return@forEachIndexed
                val date = day(item.date)
                val match = if (exactOnly) {
                    dates.indexOf(date).takeIf { it >= 0 }
                } else {
                    dates.indices.filter { abs(ChronoUnit.DAYS.between(dates[it], date)) <= 1 }
                        .minByOrNull { abs(ChronoUnit.DAYS.between(dates[it], date)) }
                } ?: return@forEachIndexed
                dates.removeAt(match)
                matched[index] = true
            }
        }
        val items = statement.allItems.mapIndexed { index, item ->
            if (!matched[index]) item else item.copy(
                isSelected = false,
                flag = listOfNotNull(alreadyLabel, item.flag).distinct().joinToString(" · "),
            )
        }
        val byId = items.associateBy { it.id }
        return statement.copy(allItems = items, monthGroups = statement.monthGroups.map { group ->
            group.copy(items = group.items.map { byId[it.id] ?: it })
        })
    }
}
