package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.sync.FakeLocalLedgerRemote
import com.vaibhav.kharchapaani.data.sync.FakeTransactionRemote
import com.vaibhav.kharchapaani.data.sync.LocalLedgerSyncEngine
import com.vaibhav.kharchapaani.data.sync.RoomTransactionSyncStore
import com.vaibhav.kharchapaani.data.sync.TransactionSyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A JSON backup restored onto a phone that has already pulled the same records from the cloud.
 *
 * Since local-friend sync (docs/local-friend-sync-v2.md), the backup still carries every split, so
 * new splits and IOUs are both in the file and on the server. Restoring the file after signing in
 * on a new phone is exactly what the docs tell people to do, and people are matched by name, so a
 * record that isn't recognised as already here doubles a balance. Real Room on both phones, the real
 * sync engines against the fakes LocalLedgerSyncTest uses, and the assertion is the balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupOverCloudRestoreTest {

    private val owner = "00000000-0000-4000-8000-0000000000a1"

    private fun newDb(): AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val phoneA = newDb()
    private val phoneB = newDb()
    private val transactions = FakeTransactionRemote(owner)
    private val server = FakeLocalLedgerRemote(transactions, owner)

    @After
    fun tearDown() {
        phoneA.close()
        phoneB.close()
    }

    private fun repo(db: AppDatabase, enabled: Boolean = true) =
        LedgerRepository(db, selfProfileId = owner, localFriendSync = enabled)

    private suspend fun syncAll(db: AppDatabase) {
        TransactionSyncEngine(RoomTransactionSyncStore(db), transactions, owner).sync()
        LocalLedgerSyncEngine(db, server).sync()
    }

    private fun expense(amount: Double, merchant: String, date: Long) = TransactionEntity(
        type = "expense", amount = amount, category = "Food", merchant = merchant, paymentMethod = "UPI",
        date = date, createdAt = date + 60_000L,
    )

    private fun shares(vararg pairs: Pair<Long, Double>) =
        pairs.map { (id, amount) -> SplitShareEntity(splitId = 0, personId = id, shareAmount = amount) }

    private suspend fun balanceWith(db: AppDatabase, name: String): Double {
        val person = db.personDao().getAllPeopleDirect().single { it.name == name }
        return repo(db, enabled = false).personBalances.first().single { it.person.id == person.id }.netBalance
    }

    @Test
    fun `restoring a backup over records already pulled from the cloud changes no balance`() = runTest {
        val a = repo(phoneA)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        a.saveTransactionWithSplits(expense(600.0, "Dinner", 1_758_400_000_000L), paidByPersonId = -1L, shares = shares(-1L to 300.0, rohan to 300.0))
        a.saveTransactionWithSplits(expense(90.0, "Auto", 1_758_500_000_000L), paidByPersonId = rohan, shares = shares(-1L to 45.0, rohan to 45.0))
        a.recordSettlement(fromPersonId = -1L, toPersonId = rohan, amount = 777.0, isIou = true, date = 1_758_600_000_000L)
        a.recordSettlement(fromPersonId = rohan, toPersonId = -1L, amount = 100.0, date = 1_758_700_000_000L)
        syncAll(phoneA)
        assertTrue(phoneA.transactionDao().getAllTransactionsDirect().all { it.syncState == SyncState.SYNCED })
        val backup = LocalBackupRepository(phoneA).collect(appVersion = "1.4.0")
        // Over-included on purpose (LocalBackup.isUnsyncable): synced splits are in the file too.
        assertEquals(2, backup.transactions.size)
        assertEquals(2, backup.settlements.size)

        // A new phone: sign in, sync, then restore the file as the docs say.
        syncAll(phoneB)
        val fromCloud = balanceWith(phoneB, "Rohan")
        assertEquals(balanceWith(phoneA, "Rohan"), fromCloud, 0.001)
        val report = LocalBackupRepository(phoneB).restore(backup)

        assertEquals(fromCloud, balanceWith(phoneB, "Rohan"), 0.001)
        assertEquals(0, report.added)
        assertEquals(1, report.peopleSkipped)
        assertEquals(2, report.transactionsSkipped)
        assertEquals(2, report.settlementsSkipped)
        assertEquals("Everything in that backup was already here. Nothing changed.", report.message())
        assertEquals(1, phoneB.personDao().getAllPeopleDirect().count { it.name.equals("Rohan", ignoreCase = true) })
    }

    @Test
    fun `the backup fills in a split from before 1-3 that a later payment settled`() = runTest {
        // The forward-only gap (docs/local-friend-sync-v2.md): the payment syncs, the split it
        // settles never did, so the cloud alone restores a balance that reads backwards.
        val before = repo(phoneA, enabled = false)
        val rohan = before.addPerson("Rohan", "#70C1FF")
        before.saveTransactionWithSplits(expense(400.0, "Old dinner", 1_758_400_000_000L), paidByPersonId = -1L, shares = shares(-1L to 200.0, rohan to 200.0))
        repo(phoneA).recordSettlement(fromPersonId = rohan, toPersonId = -1L, amount = 200.0, date = 1_758_700_000_000L)
        syncAll(phoneA)
        assertEquals(0.0, balanceWith(phoneA, "Rohan"), 0.001)
        val backup = LocalBackupRepository(phoneA).collect(appVersion = "1.4.0")

        syncAll(phoneB)
        assertEquals("the cloud alone has the payment without the split", -200.0, balanceWith(phoneB, "Rohan"), 0.001)
        val report = LocalBackupRepository(phoneB).restore(backup)

        assertEquals(0.0, balanceWith(phoneB, "Rohan"), 0.001)
        assertEquals(1, report.transactionsAdded)
        assertEquals(1, report.settlementsSkipped)
        assertEquals(1, report.peopleSkipped)
    }

    @Test
    fun `a payment typed to a fraction of a paisa is recognised after the cloud rounds it`() = runTest {
        // The settle sheet takes whatever is typed; the push rounds to the paisa (the server's
        // numeric(12, 2)), so the cloud copy and the backup's copy differ in the third decimal.
        val a = repo(phoneA)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        a.recordSettlement(fromPersonId = rohan, toPersonId = -1L, amount = 100.555, date = 1_758_700_000_000L)
        syncAll(phoneA)
        val backup = LocalBackupRepository(phoneA).collect(appVersion = "1.4.0")

        syncAll(phoneB)
        val fromCloud = balanceWith(phoneB, "Rohan")
        val report = LocalBackupRepository(phoneB).restore(backup)

        assertEquals(1, report.settlementsSkipped)
        assertEquals(1, phoneB.settlementDao().getAllSettlementsDirect().size)
        assertEquals(fromCloud, balanceWith(phoneB, "Rohan"), 0.001)
    }
}
