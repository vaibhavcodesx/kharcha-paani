package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class CsvExportTest {

    private val zone = ZoneId.of("Asia/Kolkata")

    private fun tx(id: Long, time: LocalDateTime, amount: Double, merchant: String = "m$id", note: String = "", type: String = "expense", groupId: Long? = null) =
        FullTransaction(
            TransactionEntity(
                id = id, type = type, amount = amount, category = "Food", merchant = merchant, paymentMethod = "UPI",
                note = note, date = time.atZone(zone).toInstant().toEpochMilli(), groupId = groupId,
            ),
        )

    /** A few years of entries: far more than the 10-20 the old share sheet showed. */
    private val manyMonths = (0 until 400).map { i ->
        tx(i.toLong() + 1, LocalDateTime.of(2025, 1, 1, 10, 0).plusDays(i.toLong()), 100.0 + i)
    }

    private fun rows(csv: CsvFile) = csv.content.removePrefix("﻿").split("\r\n").filter { it.isNotEmpty() }

    @Test
    fun `everything exports every entry, oldest first`() {
        val csv = CsvExport.build(manyMonths.shuffled(), emptyList(), emptyList(), ExportRange.Everything, zone)

        assertEquals(400, csv.rows)
        val lines = rows(csv)
        assertEquals(401, lines.size)
        assertTrue(lines[1].startsWith("\"2025-01-01\""))
        assertTrue(lines.last().startsWith("\"2026-02-04\""))
        assertEquals("KharchaPaani_all.csv", csv.fileName)
    }

    @Test
    fun `a month exports all of that month and nothing else`() {
        val csv = CsvExport.build(manyMonths, emptyList(), emptyList(), ExportRange.Month(YearMonth.of(2025, 3)), zone)

        assertEquals(31, csv.rows)
        assertTrue(rows(csv).drop(1).all { it.startsWith("\"2025-03-") })
        assertEquals("KharchaPaani_2025-03.csv", csv.fileName)
    }

    @Test
    fun `a date range includes both ends`() {
        val range = ExportRange.Dates(LocalDate.of(2025, 2, 27), LocalDate.of(2025, 3, 2))
        val csv = CsvExport.build(manyMonths, emptyList(), emptyList(), range, zone)

        assertEquals(listOf("2025-02-27", "2025-02-28", "2025-03-01", "2025-03-02"), rows(csv).drop(1).map { it.substring(1, 11) })
        assertEquals("KharchaPaani_2025-02-27_to_2025-03-02.csv", csv.fileName)
    }

    @Test
    fun `month edges follow the local day, not UTC`() {
        // 00:30 on 1 March in India is still 28 February in UTC.
        val entries = listOf(tx(1, LocalDateTime.of(2025, 3, 1, 0, 30), 50.0), tx(2, LocalDateTime.of(2025, 2, 28, 23, 30), 60.0))

        assertEquals(1, CsvExport.build(entries, emptyList(), emptyList(), ExportRange.Month(YearMonth.of(2025, 3)), zone).rows)
        assertEquals(1, CsvExport.build(entries, emptyList(), emptyList(), ExportRange.Month(YearMonth.of(2025, 2)), zone).rows)
    }

    @Test
    fun `amounts are plain two-decimal numbers`() {
        val entries = listOf(tx(1, LocalDateTime.of(2025, 3, 1, 12, 0), 10_000_000.0), tx(2, LocalDateTime.of(2025, 3, 2, 12, 0), 34.5))
        val lines = rows(CsvExport.build(entries, emptyList(), emptyList(), ExportRange.Everything, zone))

        assertTrue(lines[1], lines[1].contains("\"10000000.00\""))
        assertTrue(lines[2], lines[2].contains("\"34.50\""))
    }

    @Test
    fun `commas, quotes and line breaks stay inside their cell`() {
        val entries = listOf(tx(1, LocalDateTime.of(2025, 3, 1, 12, 0), 10.0, merchant = "Haldiram's, \"Sweets\"", note = "for mom\nand dad"))
        val content = CsvExport.build(entries, emptyList(), emptyList(), ExportRange.Everything, zone).content

        assertTrue(content.contains("\"Haldiram's, \"\"Sweets\"\"\""))
        assertTrue(content.contains("\"for mom\nand dad\"\r\n"))
    }

    @Test
    fun `text that would run as a spreadsheet formula is defused`() {
        val entries = listOf(tx(1, LocalDateTime.of(2025, 3, 1, 12, 0), 10.0, merchant = "=HYPERLINK(\"http://x\")", note = "@SUM(A1)"))
        val content = CsvExport.build(entries, emptyList(), emptyList(), ExportRange.Everything, zone).content

        assertTrue(content.contains("\"'=HYPERLINK(\"\"http://x\"\")\""))
        assertTrue(content.contains("\"'@SUM(A1)\""))
    }

    @Test
    fun `a split shows my share, who paid, the shares and its group`() {
        val dinner = FullTransaction(
            TransactionEntity(id = 7, type = "expense", amount = 900.0, category = "Food", merchant = "Truffles", paymentMethod = "Card", date = LocalDateTime.of(2025, 3, 5, 21, 0).atZone(zone).toInstant().toEpochMilli(), groupId = 3, isSplit = true),
            split = SplitEntity(id = 1, transactionId = 7, totalAmount = 900.0, paidByPersonId = 2),
            shares = listOf(SplitShareEntity(splitId = 1, personId = -1, shareAmount = 300.0), SplitShareEntity(splitId = 1, personId = 2, shareAmount = 600.0)),
        )
        val line = rows(
            CsvExport.build(listOf(dinner), listOf(PersonEntity(id = 2, name = "Priya", avatarColor = "#fff")), listOf(GroupEntity(id = 3, name = "Goa trip", emoji = "🏖️")), ExportRange.Everything, zone),
        )[1]

        assertEquals(
            "\"2025-03-05\",\"21:00\",\"Expense\",\"900.00\",\"300.00\",\"Food\",\"Truffles\",\"Card\",\"Goa trip\",\"Split (equal)\",\"Priya\",\"You: 300.00; Priya: 600.00\",\"\"",
            line,
        )
    }

    // --- Settlements and IOUs, which this export used to omit entirely

    private fun settlement(id: Long, time: LocalDateTime, amount: Double, from: Long, to: Long, isIou: Boolean = false) =
        SettlementEntity(
            id = id, fromPersonId = from, toPersonId = to, amount = amount,
            date = time.atZone(zone).toInstant().toEpochMilli(), isIou = isIou,
        )

    private val rohan = PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF")

    @Test
    fun `a settlement appears in the export at all`() {
        // The bug: "Everything" exported expenses only, so every payment between two people —
        // half the ledger in a settle-up app — was silently missing from the spreadsheet.
        val csv = CsvExport.build(
            emptyList(), listOf(rohan), emptyList(), ExportRange.Everything, zone,
            settlements = listOf(settlement(1, LocalDateTime.of(2025, 3, 4, 9, 0), 250.0, from = 2, to = -1)),
        )

        assertEquals(1, csv.rows)
        assertTrue(rows(csv)[1].contains("Settlement"))
        assertTrue(rows(csv)[1].contains("Rohan paid You"))
    }

    @Test
    fun `an IOU is labelled as one, and says who lent to whom`() {
        val csv = CsvExport.build(
            emptyList(), listOf(rohan), emptyList(), ExportRange.Everything, zone,
            settlements = listOf(settlement(1, LocalDateTime.of(2025, 3, 4, 9, 0), 40.0, from = -1, to = 2, isIou = true)),
        )

        assertTrue(rows(csv)[1].contains("IOU"))
        assertTrue(rows(csv)[1].contains("You lent Rohan"))
    }

    @Test
    fun `settlements obey the range like everything else`() {
        val march = settlement(1, LocalDateTime.of(2025, 3, 4, 9, 0), 10.0, from = 2, to = -1)
        val february = settlement(2, LocalDateTime.of(2025, 2, 4, 9, 0), 10.0, from = 2, to = -1)

        val csv = CsvExport.build(
            emptyList(), listOf(rohan), emptyList(), ExportRange.Month(YearMonth.of(2025, 3)), zone,
            settlements = listOf(march, february),
        )

        assertEquals(1, csv.rows)
    }

    @Test
    fun `expenses and settlements come out as one list in date order`() {
        val csv = CsvExport.build(
            listOf(tx(1, LocalDateTime.of(2025, 3, 5, 9, 0), 100.0)),
            listOf(rohan), emptyList(), ExportRange.Everything, zone,
            settlements = listOf(settlement(1, LocalDateTime.of(2025, 3, 4, 9, 0), 250.0, from = 2, to = -1)),
        )

        val lines = rows(csv)
        assertEquals(3, lines.size)
        assertTrue("the 4th should sort before the 5th", lines[1].contains("2025-03-04"))
        assertTrue(lines[2].contains("2025-03-05"))
    }

    @Test
    fun `the file opens with a byte-order mark so Excel reads rupees and names`() {
        val csv = CsvExport.build(emptyList(), emptyList(), emptyList(), ExportRange.Everything, zone)

        assertTrue(csv.content.startsWith("﻿\"Date\""))
        assertEquals(0, csv.rows)
    }
}
