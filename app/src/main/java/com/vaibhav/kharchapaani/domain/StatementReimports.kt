package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** A reversible review hint, not a ban on importing two genuinely identical purchases. */
object StatementReimports {
    const val ALREADY = "Already in your ledger"
    private val spaces = Regex("\\s+")
    private data class Key(val type: String, val paisa: Long, val merchant: String)

    fun review(statement: StatementParseResult, ledger: List<TransactionEntity>, zone: ZoneId = ZoneId.systemDefault()): StatementParseResult {
        fun day(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        fun key(type: String, amount: Double, merchant: String) = Key(
            type.trim().lowercase(Locale.ROOT),
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact(),
            merchant.trim().replace(spaces, " ").lowercase(Locale.ROOT),
        )
        val inventory = ledger.filter { it.isOwnedByMe && isSavableAmount(it.amount) }
            .groupBy { key(it.type, it.amount, it.merchant) }
            .mapValues { (_, rows) -> rows.map { day(it.date) }.toMutableList() }
        val items = statement.allItems.map { item ->
            if (!isSavableAmount(item.amount)) return@map item
            val dates = inventory[key(item.type, item.amount, item.merchant)] ?: return@map item
            val date = day(item.date)
            val match = dates.indices.filter { abs(ChronoUnit.DAYS.between(dates[it], date)) <= 1 }
                .minByOrNull { abs(ChronoUnit.DAYS.between(dates[it], date)) } ?: return@map item
            dates.removeAt(match)
            item.copy(isSelected = false, flag = listOfNotNull(ALREADY, item.flag).distinct().joinToString(" · "))
        }
        val byId = items.associateBy { it.id }
        return statement.copy(allItems = items, monthGroups = statement.monthGroups.map { group ->
            group.copy(items = group.items.map { byId[it.id] ?: it })
        })
    }
}
