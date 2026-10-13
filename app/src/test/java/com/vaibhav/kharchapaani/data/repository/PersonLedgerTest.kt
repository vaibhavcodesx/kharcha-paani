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

    // Ask-to-pay builds its lines from these rows (eng review D7), so they must also add up to the
    // balance when guests are involved and when the history starts with you owing them.
    @Test
    fun `guest shares are rows, and the rows still add up to the balance`() = runTest {
        val rohan = repository.addPerson("Rohan", "#70C1FF")
        val bob = repository.addPerson("Bob", "#FFB3BA")
        // I paid; Rohan brought a guest, so he covers both shares.
        repository.saveTransactionWithSplits(
            expense(900.0, "Shack"), paidByPersonId = -1L,
            shares = listOf(
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 300.0),
                SplitShareEntity(splitId = 0, personId = rohan, shareAmount = 300.0),
                SplitShareEntity(splitId = 0, personId = rohan, shareAmount = 300.0, guestName = "Kabir"),
            ),
            splitType = "equal",
        )
        // Bob paid; Rohan came along as my guest, so he owes me his share.
        repository.saveTransactionWithSplits(
            expense(600.0, "Boat"), paidByPersonId = bob,
            shares = listOf(
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 200.0),
                SplitShareEntity(splitId = 0, personId = bob, shareAmount = 200.0),
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 200.0, guestName = "Rohan", guestPersonId = rohan),
            ),
            splitType = "equal",
        )

        val rows = repository.getLedgerForPerson(rohan).first()
        val balance = repository.personBalances.first().single { it.person.id == rohan }.netBalance

        assertEquals(800.0, balance, 0.001)
        assertEquals(balance, rows.sumOf { it.netChange }, 0.001)
        assertEquals(setOf("split", "guest"), rows.map { it.id.substringBefore('_') }.toSet())
    }

    @Test
    fun `starting with me owing them, the rows still add up to the balance`() = runTest {
        val rohan = repository.addPerson("Rohan", "#70C1FF")
        repository.saveTransactionWithSplits(
            expense(100.0, "Lunch").copy(date = 1_000L), paidByPersonId = rohan,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 50.0), SplitShareEntity(splitId = 0, personId = rohan, shareAmount = 50.0)),
            splitType = "equal",
        )
        repository.saveTransactionWithSplits(
            expense(600.0, "Dinner").copy(date = 2_000L), paidByPersonId = -1L,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 300.0), SplitShareEntity(splitId = 0, personId = rohan, shareAmount = 300.0)),
            splitType = "equal",
        )

        val rows = repository.getLedgerForPerson(rohan).first()
        val balance = repository.personBalances.first().single { it.person.id == rohan }.netBalance
        val hisaab = com.vaibhav.kharchapaani.domain.HisaabItemiser.itemise(rows)!!

        assertEquals(250.0, balance, 0.001)
        assertEquals(balance, rows.sumOf { it.netChange }, 0.001)
        assertEquals(-50.0, hisaab.carriedOver, 0.001)
        assertEquals(balance, hisaab.total, 0.001)
    }
}
