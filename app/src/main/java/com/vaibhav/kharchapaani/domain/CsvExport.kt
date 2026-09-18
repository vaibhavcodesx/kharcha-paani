package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Which entries an export covers. */
sealed interface ExportRange {
    fun contains(date: LocalDate): Boolean

    data class Month(val month: YearMonth) : ExportRange {
        override fun contains(date: LocalDate) = YearMonth.from(date) == month
    }

    /** Both ends included. */
    data class Dates(val from: LocalDate, val to: LocalDate) : ExportRange {
        override fun contains(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)
    }

    data object Everything : ExportRange {
        override fun contains(date: LocalDate) = true
    }
}

class CsvFile(val fileName: String, val content: String, val rows: Int)

/**
 * The ledger as a spreadsheet: one header row, then one row per entry, oldest first. It's a plain
 * table so Sheets and Excel open it as-is; the old export wrapped it in "#" banner lines and a second
 * summary table, which imported as junk rows. Amounts are plain numbers with two decimals, never
 * Double's "1.0E7".
 */
object CsvExport {

    val header = listOf(
        "Date", "Time", "Type", "Amount (INR)", "Your share (INR)", "Category", "Merchant / payee",
        "Payment method", "Group", "Split", "Paid by", "Shares", "Note",
    )

    fun entriesIn(transactions: List<FullTransaction>, range: ExportRange, zone: ZoneId): List<FullTransaction> =
        transactions
            .filter { range.contains(Instant.ofEpochMilli(it.transaction.date).atZone(zone).toLocalDate()) }
            .sortedWith(compareBy({ it.transaction.date }, { it.transaction.id }))

    fun build(
        transactions: List<FullTransaction>,
        people: List<PersonEntity>,
        groups: List<GroupEntity>,
        range: ExportRange,
        zone: ZoneId,
        /** A group's name, when the export is one group's entries. */
        fileNameSuffix: String? = null,
    ): CsvFile {
        val names = people.associate { it.id to it.name }
        val groupNames = groups.associate { it.id to it.name }
        fun nameOf(personId: Long) = if (personId == ME) "You" else names[personId] ?: "Former member"

        val rows = entriesIn(transactions, range, zone).map { ft ->
            val tx = ft.transaction
            val at = Instant.ofEpochMilli(tx.date).atZone(zone)
            val split = ft.split
            listOf(
                at.format(DATE),
                at.format(TIME),
                if (tx.type.equals("income", ignoreCase = true)) "Income" else "Expense",
                money(tx.amount),
                money(if (split != null) ft.myPersonalShare else tx.amount),
                tx.category,
                tx.merchant,
                tx.paymentMethod,
                tx.groupId?.let { groupNames[it] }.orEmpty(),
                split?.let { "Split (${it.splitType})" } ?: "Personal",
                split?.let { nameOf(it.paidByPersonId) } ?: "You",
                ft.shares.joinToString("; ") { "${nameOf(it.personId)}: ${money(it.shareAmount)}" },
                tx.note,
            )
        }

        val content = buildString {
            // Excel reads a file without a byte-order mark as the local code page, garbling ₹ and names.
            append('﻿')
            append(line(header))
            rows.forEach { append(line(it)) }
        }
        return CsvFile(fileName(range, fileNameSuffix), content, rows.size)
    }

    fun fileName(range: ExportRange, suffix: String? = null): String {
        val what = when (range) {
            is ExportRange.Month -> range.month.toString()
            is ExportRange.Dates -> "${range.from}_to_${range.to}"
            ExportRange.Everything -> "all"
        }
        // A group name can hold anything; keep the file name to plain characters.
        val group = suffix?.replace(Regex("[^A-Za-z0-9]+"), "_")?.trim('_')?.takeIf { it.isNotEmpty() }
        return listOfNotNull("KharchaPaani", group, what).joinToString("_") + ".csv"
    }

    private fun line(fields: List<String>) = fields.joinToString(",", postfix = "\r\n") { quote(it) }

    // RFC 4180 quoting. A cell starting with = + - @ runs as a formula in a spreadsheet, so a merchant
    // or note like "=HYPERLINK(...)" gets a leading apostrophe; amounts are written unsigned.
    private fun quote(field: String): String {
        val safe = if (field.isNotEmpty() && field[0] in FORMULA_STARTS) "'$field" else field
        return "\"" + safe.replace("\"", "\"\"") + "\""
    }

    private fun money(amount: Double) = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
    private const val ME = -1L
}
