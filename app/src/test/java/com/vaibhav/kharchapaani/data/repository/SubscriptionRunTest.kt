package com.vaibhav.kharchapaani.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Subscriptions against real Room: charges appear when due, once, and never for the future. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubscriptionRunTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "subs-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val repository = LedgerRepository(db)
    private val zone = ZoneId.systemDefault()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun millis(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun netflix(start: LocalDate) = SubscriptionEntity(
        name = "Netflix",
        amount = 649.0,
        category = "Entertainment",
        paymentMethod = "Credit Card",
        cycle = SubscriptionCycle.MONTHLY.name,
        startDate = millis(start),
    )

    private suspend fun entries() = db.transactionDao().getAllTransactionsDirect().sortedBy { it.date }

    @Test
    fun `a subscription started months ago fills in its charges once, and not again`() = runBlocking {
        repository.saveSubscription(netflix(LocalDate.of(2026, 6, 15)))

        val created = repository.runDueSubscriptions(today = LocalDate.of(2026, 9, 18), zone = zone)

        assertEquals(4, created)
        assertEquals(listOf("Netflix", "Netflix", "Netflix", "Netflix"), entries().map { it.merchant })
        assertEquals(listOf(649.0, 649.0, 649.0, 649.0), entries().map { it.amount })
        assertEquals("Entertainment", entries().first().category)

        // Running again the same day changes nothing.
        assertEquals(0, repository.runDueSubscriptions(today = LocalDate.of(2026, 9, 18), zone = zone))
        assertEquals(4, entries().size)

        // A month later, one more.
        assertEquals(1, repository.runDueSubscriptions(today = LocalDate.of(2026, 10, 15), zone = zone))
        assertEquals(5, entries().size)
    }

    @Test
    fun `starting from the next one leaves the months before it alone`() = runBlocking {
        repository.saveSubscription(netflix(LocalDate.of(2026, 6, 15)), chargeFromStart = false, today = LocalDate.of(2026, 9, 18))

        assertEquals(0, repository.runDueSubscriptions(today = LocalDate.of(2026, 9, 18), zone = zone))
        assertEquals(emptyList<String>(), entries().map { it.merchant })

        assertEquals(1, repository.runDueSubscriptions(today = LocalDate.of(2026, 10, 15), zone = zone))
    }

    @Test
    fun `nothing is created before the first charge is due`() = runBlocking {
        repository.saveSubscription(netflix(LocalDate.of(2026, 10, 1)))

        assertEquals(0, repository.runDueSubscriptions(today = LocalDate.of(2026, 9, 18), zone = zone))
        assertEquals(emptyList<String>(), entries().map { it.merchant })
    }

    @Test
    fun `cancelling stops what's to come and keeps what already went through`() = runBlocking {
        val id = repository.saveSubscription(netflix(LocalDate.of(2026, 6, 15)))
        repository.runDueSubscriptions(today = LocalDate.of(2026, 8, 20), zone = zone)
        assertEquals(3, entries().size)

        repository.cancelSubscription(id, at = LocalDate.of(2026, 8, 20))

        assertEquals(0, repository.runDueSubscriptions(today = LocalDate.of(2026, 12, 31), zone = zone))
        assertEquals(3, entries().size)

        repository.deleteSubscription(id)
        assertNull(db.subscriptionDao().byId(id))
        assertEquals("the charges it already made stay", 3, entries().size)
    }
}
