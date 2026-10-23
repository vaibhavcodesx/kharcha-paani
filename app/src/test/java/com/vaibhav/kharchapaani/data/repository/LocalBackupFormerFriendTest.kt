package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.BackupPerson
import com.vaibhav.kharchapaani.domain.BackupShare
import com.vaibhav.kharchapaani.domain.BackupSplit
import com.vaibhav.kharchapaani.domain.BackupTransaction
import com.vaibhav.kharchapaani.domain.LocalBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A backup names people, and "no name" means "you". Someone deleted from this phone has no name to
 * write, so before 2026-10-24 a split *they* paid went into the file with no payer and came back
 * paid by you: everyone else on it suddenly owed you their share (correctness audit, 2026-10-24).
 *
 * Every test here starts from a ledger built through [LedgerRepository], exports it, restores it
 * onto an empty phone and compares the balances the app shows, not the file.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupFormerFriendTest {

    private val me = -1L

    private fun newDb(): AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val source = newDb()
    private val target = newDb()

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun repo(db: AppDatabase, localFriendSync: Boolean = true) =
        LedgerRepository(db, selfProfileId = "00000000-0000-4000-8000-0000000000a1", localFriendSync = localFriendSync)

    private fun expense(merchant: String, amount: Double, date: Long) = TransactionEntity(
        type = "expense", amount = amount, category = "Travel", merchant = merchant, paymentMethod = "UPI",
        date = date, createdAt = date,
    )

    private fun shares(vararg pairs: Pair<Long, Double>) =
        pairs.map { (id, amount) -> SplitShareEntity(splitId = 0, personId = id, shareAmount = amount) }

    private suspend fun balanceWith(db: AppDatabase, name: String): Double =
        repo(db).personBalances.first().single { it.person.name == name }.netBalance

    private suspend fun roundTrip() =
        LocalBackupRepository(target).restore(LocalBackupRepository(source).collect(appVersion = "test"))

    @Test
    fun `a split paid by a friend since deleted does not come back as paid by me`() = runTest {
        val ledger = repo(source)
        val rohan = ledger.addPerson("Rohan", "#70C1FF")
        val meera = ledger.addPerson("Meera", "#C6F24C")
        // Rohan paid for the cab; the three of us shared it.
        ledger.saveTransactionWithSplits(
            expense("Cab", 300.0, 2_000L), paidByPersonId = rohan,
            shares = shares(me to 100.0, meera to 100.0, rohan to 100.0), splitType = "equal",
        )
        ledger.deletePerson(rohan)
        // Meera owed Rohan, never me.
        assertEquals(0.0, balanceWith(source, "Meera"), 0.001)

        roundTrip()

        assertEquals("Meera must not owe me Rohan's cab", 0.0, balanceWith(target, "Meera"), 0.001)
        assertTrue(target.splitDao().getAllSplitsDirect().none { it.paidByPersonId == me })
        // What I spent on it is still mine: my share, as my own expense.
        val cab = target.transactionDao().getAllTransactionsDirect().single { it.merchant == "Cab" }
        assertEquals(100.0, cab.amount, 0.001)
        assertFalse(cab.isSplit)
    }

    @Test
    fun `a split paid by a friend since deleted, with no share of mine, is not restored at all`() = runTest {
        val ledger = repo(source)
        val rohan = ledger.addPerson("Rohan", "#70C1FF")
        val meera = ledger.addPerson("Meera", "#C6F24C")
        ledger.saveTransactionWithSplits(
            expense("Their dinner", 400.0, 3_000L), paidByPersonId = rohan,
            shares = shares(meera to 200.0, rohan to 200.0), splitType = "equal",
        )
        ledger.deletePerson(rohan)

        roundTrip()

        assertTrue(target.transactionDao().getAllTransactionsDirect().none { it.merchant == "Their dinner" })
        assertEquals(0.0, balanceWith(target, "Meera"), 0.001)
    }

    @Test
    fun `what deleting a friend leaves as my own expense is backed up, and backed up again after a restore`() = runTest {
        // A split from before 1.3.0, kept on the phone: the case where nothing of it is in the cloud.
        val ledger = repo(source, localFriendSync = false)
        val rohan = ledger.addPerson("Rohan", "#70C1FF")
        ledger.saveTransactionWithSplits(
            expense("Auto", 200.0, 6_000L), paidByPersonId = rohan,
            shares = shares(me to 100.0, rohan to 100.0), splitType = "equal",
        )
        // Nobody else was on it, so it collapses into my own ₹100, kept on this phone only: it
        // never uploads, and it was left out of the backup as if it had.
        ledger.deletePerson(rohan)

        roundTrip()
        val again = LocalBackupRepository(target).collect(appVersion = "test")

        val auto = target.transactionDao().getAllTransactionsDirect().single { it.merchant == "Auto" }
        assertEquals(100.0, auto.amount, 0.001)
        assertEquals(100.0, again.transactions.single { it.merchant == "Auto" }.amount, 0.001)
    }

    @Test
    fun `a share or a settlement naming someone no longer here is left out, not given to me`() = runTest {
        val ledger = repo(source)
        val meera = ledger.addPerson("Meera", "#C6F24C")
        val gone = 999L // a person row since removed, e.g. a groupmate who left
        ledger.saveTransactionWithSplits(
            expense("Snacks", 150.0, 4_000L), paidByPersonId = me,
            shares = shares(me to 50.0, meera to 50.0, gone to 50.0), splitType = "equal",
        )
        source.groupSyncDao().insertSettlement(
            SettlementEntity(fromPersonId = me, toPersonId = gone, amount = 80.0, date = 5_000L, isIou = true, createdAt = 5_000L)
        )

        roundTrip()

        val snacks = target.transactionDao().getAllTransactionsDirect().single { it.merchant == "Snacks" }
        val split = target.splitDao().getAllSplitsDirect().single { it.transactionId == snacks.id }
        val restoredShares = target.splitDao().getSharesForSplitDirect(split.id)
        assertEquals("one share of mine, not two", 50.0, restoredShares.filter { it.personId == me }.sumOf { it.shareAmount }, 0.001)
        assertEquals(50.0, balanceWith(target, "Meera"), 0.001)
        assertTrue(target.settlementDao().getAllSettlementsDirect().isEmpty())
    }

    @Test
    fun `a payer the restoring phone has never heard of is not taken to be me`() = runTest {
        // A registered groupmate who paid outside a shared group is named in the file but isn't one
        // of its local friends, so a fresh phone can't place them until the group syncs.
        val backup = LocalBackup(
            exportedAt = 1L,
            appVersion = "test",
            people = listOf(BackupPerson(name = "Meera", avatarColor = "#C6F24C", createdAt = 1L)),
            transactions = listOf(
                BackupTransaction(
                    type = "expense", amount = 300.0, category = "Travel", merchant = "Cab", paymentMethod = "UPI",
                    date = 2_000L, createdAt = 2_000L,
                    split = BackupSplit(
                        totalAmount = 300.0, splitType = "equal", paidByName = "Bob",
                        shares = listOf(BackupShare(name = null, amount = 100.0), BackupShare(name = "Meera", amount = 100.0), BackupShare(name = "Bob", amount = 100.0)),
                    ),
                )
            ),
        )

        LocalBackupRepository(target).restore(backup)

        assertEquals(0.0, balanceWith(target, "Meera"), 0.001)
        val cab = target.transactionDao().getAllTransactionsDirect().single()
        assertEquals(100.0, cab.amount, 0.001)
        assertFalse(cab.isSplit)
    }
}
