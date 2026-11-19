package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.domain.BackupReadResult
import com.vaibhav.kharchapaani.domain.CalendarDay
import com.vaibhav.kharchapaani.domain.LocalBackup
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Export real subscription charges, restore, then run the real Room charging pass. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupSubscriptionRestoreTest {
    private fun newDb() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val source = newDb()
    private val target = newDb()
    private val today = LocalDate.of(2026, 9, 18)

    @After fun close() {
        source.close()
        target.close()
    }

    private suspend fun restoreOldSubscription(start: LocalDate, chargeBackup: Boolean) {
        source.subscriptionDao().insert(SubscriptionEntity(
            name = "Monthly plan", amount = 100.0, category = "Bills", paymentMethod = "Cash",
            cycle = "MONTHLY", startDate = CalendarDay.toMillis(start),
        ))
        if (chargeBackup) {
            LedgerRepository(source).runDueSubscriptions(today.minusMonths(3))
            for (row in source.transactionDao().getAllTransactionsDirect()) {
                source.transactionDao().updateTransaction(row.copy(syncState = SyncState.LOCAL_ONLY))
            }
        }
        val exported = LocalBackupRepository(source).collect("test")
        val decoded = LocalBackup.decode(ApplicationProvider.getApplicationContext(), LocalBackup.encode(exported)) as BackupReadResult.Ok
        LocalBackupRepository(target).restore(decoded.backup, today = today)
        // Importing the same archive twice must still recognise the original subscription anchor.
        LocalBackupRepository(target).restore(decoded.backup, today = today)
        assertEquals(1, target.subscriptionDao().getAllDirect().size)
        assertEquals(CalendarDay.toMillis(start), target.subscriptionDao().getAllDirect().single().startDate)
        assertEquals(0, LedgerRepository(target).runDueSubscriptions(today))
        assertEquals(source.transactionDao().getAllTransactionsDirect().map { it.date to it.amount },
            target.transactionDao().getAllTransactionsDirect().map { it.date to it.amount })
    }

    @Test fun `three month old backup does not create past charges but keeps the next charge`() = runBlocking {
        restoreOldSubscription(today.minusMonths(6), chargeBackup = true)
        assertEquals(400.0, target.transactionDao().getAllTransactionsDirect().sumOf { it.amount }, 0.0)
        assertEquals(1, LedgerRepository(target).runDueSubscriptions(today.plusMonths(1)))
        assertEquals(500.0, target.transactionDao().getAllTransactionsDirect().sumOf { it.amount }, 0.0)
        assertEquals(0, LedgerRepository(target).runDueSubscriptions(today.plusMonths(1)))
    }

    @Test fun `far past start without a checkpoint produces no historical rows`() = runBlocking {
        restoreOldSubscription(LocalDate.of(1, 1, 31), chargeBackup = false)
        assertEquals(1, LedgerRepository(target).runDueSubscriptions(LocalDate.of(2026, 9, 30)))
        assertEquals(listOf(100.0), target.transactionDao().getAllTransactionsDirect().map { it.amount })
    }
}
