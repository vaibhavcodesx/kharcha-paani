package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PaymentAskEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The "Asked 3 days ago" line on a friend's row (ask-to-pay): it shows the latest ask while the
 * friend still owes you, and goes once they've been square since.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AskToPayRepositoryTest {

    private val db: AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = LedgerRepository(db, selfProfileId = "me")

    @After
    fun tearDown() = db.close()

    private suspend fun iPaidFor(person: Long, theirShare: Double, at: Long) = repository.saveTransactionWithSplits(
        TransactionEntity(type = "expense", amount = theirShare * 2, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = at),
        paidByPersonId = -1L,
        shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = theirShare), SplitShareEntity(splitId = 0, personId = person, shareAmount = theirShare)),
        splitType = "equal",
    )

    @Test
    fun `the latest ask shows while they still owe you`() = runTest {
        val rohan = repository.addPerson("Rohan", "#70C1FF")
        val bob = repository.addPerson("Bob", "#FFB3BA")
        iPaidFor(rohan, 300.0, at = 1_000L)
        db.paymentAskDao().insert(PaymentAskEntity(personId = rohan, amount = 300.0, sentAt = 5_000L))
        db.paymentAskDao().insert(PaymentAskEntity(personId = rohan, amount = 300.0, sentAt = 9_000L))

        assertEquals(mapOf(rohan to 9_000L), repository.currentAsks.first())
        assertEquals("Bob was never asked", null, repository.currentAsks.first()[bob])
    }

    @Test
    fun `once they've been square since the ask, it no longer shows`() = runTest {
        val rohan = repository.addPerson("Rohan", "#70C1FF")
        iPaidFor(rohan, 300.0, at = 1_000L)
        db.paymentAskDao().insert(PaymentAskEntity(personId = rohan, amount = 300.0, sentAt = 2_000L))
        repository.recordSettlement(fromPersonId = rohan, toPersonId = -1L, amount = 300.0, isIou = false, date = 3_000L)

        assertEquals(emptyMap<Long, Long>(), repository.currentAsks.first())

        // A new debt after that, with the old ask: still nothing to show until they're asked again.
        iPaidFor(rohan, 120.0, at = 4_000L)
        assertEquals(emptyMap<Long, Long>(), repository.currentAsks.first())
    }

    @Test
    fun `deleting a friend takes their asks with them`() = runTest {
        val rohan = repository.addPerson("Rohan", "#70C1FF")
        db.paymentAskDao().insert(PaymentAskEntity(personId = rohan, amount = 300.0, sentAt = 2_000L))

        db.personDao().deletePersonById(rohan)

        assertEquals(emptyList<PaymentAskEntity>(), db.paymentAskDao().observeAll().first())
    }
}
