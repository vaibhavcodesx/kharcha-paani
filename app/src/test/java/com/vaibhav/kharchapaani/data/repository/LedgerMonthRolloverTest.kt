package com.vaibhav.kharchapaani.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.sync.personalTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Home's "this month" moves on at midnight on the 1st while the screen is open, with nothing written
 * to the database. It used to wait for the next write (correctness audit, 2026-10-24).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerMonthRolloverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "rollover-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val today = MutableStateFlow(LocalDate.of(2026, 10, 31))
    private val repository = LedgerRepository(db, today = today)

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `this month's spending starts again at midnight on the 1st without a database write`() = runBlocking {
        val lastDay = LocalDateTime.of(2026, 10, 31, 20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        repository.saveTransactionWithSplits(
            personalTransaction("DMart", syncState = SyncState.LOCAL_ONLY, amount = 500.0).copy(date = lastDay),
        )
        val spent = Channel<Double>(Channel.UNLIMITED)
        val watching = launch(Dispatchers.Default) { repository.monthlyAnalytics.collect { spent.send(it.totalExpense) } }

        withTimeout(10_000) {
            assertEquals(500.0, spent.receive(), 0.001)
            today.value = LocalDate.of(2026, 11, 1)
            assertEquals("November has nothing in it yet", 0.0, spent.receive(), 0.001)
        }
        watching.cancel()
    }
}
