package com.vaibhav.kharchapaani.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.sync.personalTransaction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Insights' month picker against real Room: each month's figures come from that month's entries. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerMonthAnalyticsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "months-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val repository = LedgerRepository(db)
    private val zone = ZoneId.systemDefault()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun millis(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    private suspend fun expense(merchant: String, amount: Double, category: String, time: LocalDateTime) =
        repository.saveTransactionWithSplits(
            personalTransaction(merchant, syncState = SyncState.LOCAL_ONLY, amount = amount).copy(category = category, date = millis(time)),
        )

    private suspend fun income(merchant: String, amount: Double, time: LocalDateTime) =
        repository.saveTransactionWithSplits(
            personalTransaction(merchant, syncState = SyncState.LOCAL_ONLY, amount = amount)
                .copy(type = "income", category = "Income (Salary)", date = millis(time)),
        )

    private suspend fun seedThreeMonths() {
        expense("Myntra", 500.0, "Shopping", LocalDateTime.of(2026, 7, 31, 23, 59))
        income("Acme", 55000.0, LocalDateTime.of(2026, 7, 1, 9, 0))
        expense("Chai Point", 120.0, "Food", LocalDateTime.of(2026, 8, 1, 0, 0))
        expense("Landlord", 18000.0, "Rent", LocalDateTime.of(2026, 8, 5, 10, 0))
        income("Acme", 60000.0, LocalDateTime.of(2026, 8, 1, 9, 0))
        val rahul = repository.addPerson("Rahul", "#70C1FF")
        // I paid ₹900 for dinner with Rahul; ₹450 is mine.
        repository.saveTransactionWithSplits(
            personalTransaction("Truffles", syncState = SyncState.LOCAL_ONLY, amount = 900.0).copy(date = millis(LocalDateTime.of(2026, 8, 20, 21, 0))),
            paidByPersonId = -1L,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 450.0), SplitShareEntity(splitId = 0, personId = rahul, shareAmount = 450.0)),
        )
        expense("Swiggy", 999.0, "Food", LocalDateTime.of(2026, 9, 1, 0, 0))
    }

    @Test
    fun `each month's figures are that month's entries`() = runBlocking {
        seedThreeMonths()

        val july = repository.monthlyAnalyticsFor(YearMonth.of(2026, 7)).first()
        val august = repository.monthlyAnalyticsFor(YearMonth.of(2026, 8)).first()
        val september = repository.monthlyAnalyticsFor(YearMonth.of(2026, 9)).first()

        assertEquals(500.0, july.totalExpense, 0.0)
        assertEquals(55000.0, july.totalIncome, 0.0)
        assertEquals(listOf("Shopping"), july.categorySummaries.map { it.category })

        // Chai 120 + rent 18000 + my 450 of the dinner.
        assertEquals(18570.0, august.totalExpense, 0.0)
        assertEquals(60000.0, august.totalIncome, 0.0)
        assertEquals(listOf("Rent", "Food"), august.categorySummaries.map { it.category })
        assertEquals(570.0, august.categorySummaries.first { it.category == "Food" }.amount, 0.0)

        assertEquals(999.0, september.totalExpense, 0.0)
        assertEquals(0.0, september.totalIncome, 0.0)
    }

    @Test
    fun `a month follows edits and deletes`() = runBlocking {
        seedThreeMonths()
        val swiggy = db.transactionDao().getAllTransactionsDirect().first { it.merchant == "Swiggy" }

        // Moved back into August.
        repository.updateTransactionWithSplits(swiggy.copy(date = millis(LocalDateTime.of(2026, 8, 31, 22, 0))))
        assertEquals(19569.0, repository.monthlyAnalyticsFor(YearMonth.of(2026, 8)).first().totalExpense, 0.0)
        assertEquals(0.0, repository.monthlyAnalyticsFor(YearMonth.of(2026, 9)).first().totalExpense, 0.0)

        repository.deleteTransaction(swiggy.id)
        assertEquals(18570.0, repository.monthlyAnalyticsFor(YearMonth.of(2026, 8)).first().totalExpense, 0.0)
    }
}
