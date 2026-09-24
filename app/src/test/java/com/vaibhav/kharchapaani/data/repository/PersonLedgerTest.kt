package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
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
 * A friend's history, as the person sheet lists it. Had no test at all; the rows said
 * "You paid ₹150 (Their share)" when their share was ₹75, and rounded ₹150.50 to ₹151.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonLedgerTest {

    private val db: AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = LedgerRepository(db, selfProfileId = "me")

    @After
    fun tearDown() = db.close()

    private fun expense(amount: Double, merchant: String) = TransactionEntity(
        type = "expense", amount = amount, category = "Food", merchant = merchant, paymentMethod = "UPI", date = 1_000L,
    )

    @Test
    fun `each row says both figures, to the paisa, and the rows add up to the balance`() = runTest {
        val rohan = repository.addPerson("Rohan", "#70C1FF")
        repository.saveTransactionWithSplits(
            expense(150.50, "Dinner"), paidByPersonId = -1L,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 75.25), SplitShareEntity(splitId = 0, personId = rohan, shareAmount = 75.25)),
            splitType = "equal",
        )
        repository.saveTransactionWithSplits(
            expense(90.0, "Auto"), paidByPersonId = rohan,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 45.0), SplitShareEntity(splitId = 0, personId = rohan, shareAmount = 45.0)),
            splitType = "equal",
        )
        repository.recordSettlement(fromPersonId = rohan, toPersonId = -1L, amount = 20.0, isIou = false)
        repository.recordSettlement(fromPersonId = -1L, toPersonId = rohan, amount = 500.0, isIou = true)

        val rows = repository.getLedgerForPerson(rohan).first()

        assertEquals("You paid ₹150.50 · their share ₹75.25", rows.single { it.title == "Dinner" }.subtitle)
        assertEquals("They paid ₹90 · your share ₹45", rows.single { it.title == "Auto" }.subtitle)
        assertEquals(setOf("Dinner", "Auto", "They paid you", "You lent them"), rows.map { it.title }.toSet())

        // 75.25 owed to me, 45 I owe, 20 they paid back, 500 I lent: +510.25.
        val balance = repository.personBalances.first().single { it.person.id == rohan }.netBalance
        assertEquals(510.25, rows.sumOf { it.netChange }, 0.001)
        assertEquals(balance, rows.sumOf { it.netChange }, 0.001)
    }
}
