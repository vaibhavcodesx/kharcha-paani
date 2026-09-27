package com.vaibhav.kharchapaani.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Local-friend sync (docs/local-friend-sync-v2.md), against real Room and a fake server that
 * enforces what the staged migration enforces. Shaped like LocalBackupRoundTripTest, for the same
 * reason: start from a ledger with money in it, move it, and assert the *balance* on the far side.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalLedgerSyncTest {

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

    private fun repo(db: AppDatabase, enabled: Boolean) =
        LedgerRepository(db, selfProfileId = owner, localFriendSync = enabled)

    private suspend fun syncAll(db: AppDatabase) {
        TransactionSyncEngine(RoomTransactionSyncStore(db), transactions, owner).sync()
        LocalLedgerSyncEngine(db, server).sync()
    }

    private fun dinner(amount: Double, merchant: String = "Dinner") = TransactionEntity(
        type = "expense", amount = amount, category = "Food", merchant = merchant, paymentMethod = "UPI",
        date = 1_758_400_000_000L, createdAt = 1_758_400_300_000L,
    )

    private fun shares(vararg pairs: Pair<Long, Double>) = pairs.map { (id, amount) -> SplitShareEntity(splitId = 0, personId = id, shareAmount = amount) }

    private suspend fun balanceWith(db: AppDatabase, name: String): Double {
        val person = db.personDao().getAllPeopleDirect().single { it.name == name }
        return repo(db, enabled = false).personBalances.first().single { it.person.id == person.id }.netBalance
    }

    @Test
    fun `new splits and IOUs with a friend with no account come back on another phone, balance and dates intact`() = runTest {
        val a = repo(phoneA, enabled = true)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        a.saveTransactionWithSplits(dinner(600.0), paidByPersonId = -1L, shares = shares(-1L to 300.0, rohan to 300.0), splitType = "equal")
        a.saveTransactionWithSplits(dinner(90.0, "Auto"), paidByPersonId = rohan, shares = shares(-1L to 45.0, rohan to 45.0), splitType = "equal")
        a.recordSettlement(fromPersonId = -1L, toPersonId = rohan, amount = 777.0, isIou = true)
        a.recordSettlement(fromPersonId = rohan, toPersonId = -1L, amount = 100.0)
        val expected = balanceWith(phoneA, "Rohan")
        assertEquals(300.0 - 45.0 + 777.0 - 100.0, expected, 0.001)

        syncAll(phoneA)
        // A reinstall: an empty phone, the same account.
        syncAll(phoneB)

        assertEquals(expected, balanceWith(phoneB, "Rohan"), 0.001)
        val restored = phoneB.transactionDao().getAllTransactionsDirect().single { it.merchant == "Dinner" }
        assertTrue(restored.isSplit)
        assertEquals(1_758_400_000_000L, restored.date)
        assertEquals(1_758_400_300_000L, restored.createdAt)
        // Everything on phone A made it up: nothing left queued or failed.
        assertTrue(phoneA.transactionDao().getAllTransactionsDirect().all { it.syncState == SyncState.SYNCED })
    }

    @Test
    fun `history already on the phone never uploads, not when the feature turns on and not when it's edited`() = runTest {
        // Recorded before the feature existed.
        val before = repo(phoneA, enabled = false)
        val rohan = before.addPerson("Rohan", "#70C1FF")
        val oldId = before.saveTransactionWithSplits(dinner(400.0, "Old dinner"), paidByPersonId = -1L, shares = shares(-1L to 200.0, rohan to 200.0), splitType = "equal")
        before.recordSettlement(fromPersonId = -1L, toPersonId = rohan, amount = 50.0, isIou = true)

        val after = repo(phoneA, enabled = true)
        syncAll(phoneA)
        val old = phoneA.transactionDao().getTransactionByIdDirect(oldId)!!
        after.updateTransactionWithSplits(old.copy(merchant = "Old dinner, renamed"), paidByPersonId = -1L, shares = shares(-1L to 200.0, rohan to 200.0), splitType = "equal")
        syncAll(phoneA)

        assertEquals(SyncState.LOCAL_ONLY, phoneA.transactionDao().getTransactionByIdDirect(oldId)!!.syncState)
        assertTrue(phoneA.settlementDao().getAllSettlementsDirect().all { it.syncState == SyncState.LOCAL_ONLY })
        assertTrue("nothing from before reached the server", server.splits.isEmpty() && server.settlements.isEmpty() && transactions.server.isEmpty())
    }

    @Test
    fun `with the feature off nothing about splits changes`() = runTest {
        val a = repo(phoneA, enabled = false)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        val id = a.saveTransactionWithSplits(dinner(600.0), paidByPersonId = -1L, shares = shares(-1L to 300.0, rohan to 300.0), splitType = "equal")

        assertEquals(SyncState.LOCAL_ONLY, phoneA.transactionDao().getTransactionByIdDirect(id)!!.syncState)
    }

    @Test
    fun `a split with someone who has an account stays on the phone`() = runTest {
        // The save flow offers a shared group for them; "save on this phone" has to mean it.
        val a = repo(phoneA, enabled = true)
        val bob = phoneA.personDao().insertPerson(PersonEntity(name = "Bob", avatarColor = "#fff", profileId = "00000000-0000-4000-8000-0000000000b1"))
        val id = a.saveTransactionWithSplits(dinner(600.0), paidByPersonId = -1L, shares = shares(-1L to 300.0, bob to 300.0), splitType = "equal")

        assertEquals(SyncState.LOCAL_ONLY, phoneA.transactionDao().getTransactionByIdDirect(id)!!.syncState)
    }

    @Test
    fun `an even three-way split is rounded to the paisa so the server takes it`() = runTest {
        val a = repo(phoneA, enabled = true)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        val meera = a.addPerson("Meera", "#C6F24C")
        a.saveTransactionWithSplits(dinner(100.0), paidByPersonId = -1L, shares = shares(-1L to 33.33, rohan to 33.33, meera to 33.33), splitType = "equal")

        syncAll(phoneA)

        assertEquals(1, server.splits.size)
        assertTrue(phoneA.transactionDao().getAllTransactionsDirect().all { it.syncState == SyncState.SYNCED })
    }

    @Test
    fun `unsplitting a synced split takes the split down, so a restore doesn't bring it back`() = runTest {
        val a = repo(phoneA, enabled = true)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        val id = a.saveTransactionWithSplits(dinner(600.0), paidByPersonId = -1L, shares = shares(-1L to 300.0, rohan to 300.0), splitType = "equal")
        syncAll(phoneA)

        val tx = phoneA.transactionDao().getTransactionByIdDirect(id)!!
        a.updateTransactionWithSplits(tx.copy(merchant = "Just mine"), paidByPersonId = -1L, shares = emptyList())
        // A second plain edit, which clears the expense's own tombstone: the split's must survive it.
        a.updateTransactionWithSplits(phoneA.transactionDao().getTransactionByIdDirect(id)!!.copy(note = "edited"), paidByPersonId = -1L, shares = emptyList())
        syncAll(phoneA)
        syncAll(phoneB)

        val restored = phoneB.transactionDao().getAllTransactionsDirect().single()
        assertFalse(restored.isSplit)
        assertEquals(600.0, restored.amount, 0.001)
        assertNull(phoneB.groupSyncDao().splitForTransaction(restored.id))
    }

    @Test
    fun `deleting a synced split's expense takes its split down on the server too`() = runTest {
        val a = repo(phoneA, enabled = true)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        val id = a.saveTransactionWithSplits(dinner(600.0), paidByPersonId = -1L, shares = shares(-1L to 300.0, rohan to 300.0), splitType = "equal")
        syncAll(phoneA)
        assertNull(server.splits.values.single().deletedAt)

        a.deleteTransaction(id)
        syncAll(phoneA)

        // The 1.3 audit found the split row left live after its expense was deleted.
        assertTrue(server.splits.values.single().deletedAt != null)
        syncAll(phoneB)
        assertTrue(phoneB.transactionDao().getAllTransactionsDirect().isEmpty())
    }

    @Test
    fun `a retracted IOU is retracted on the other phone too`() = runTest {
        val a = repo(phoneA, enabled = true)
        val rohan = a.addPerson("Rohan", "#70C1FF")
        a.recordSettlement(fromPersonId = -1L, toPersonId = rohan, amount = 777.0, isIou = true)
        syncAll(phoneA)
        syncAll(phoneB)
        assertEquals(1, phoneB.settlementDao().getAllSettlementsDirect().size)

        a.deleteSettlement(phoneA.settlementDao().getAllSettlementsDirect().single().id)
        syncAll(phoneA)
        syncAll(phoneB)

        assertTrue(phoneB.settlementDao().getAllSettlementsDirect().isEmpty())
    }
}

/** What the staged migration enforces: owner-only rows, shares that add up, sane names. */
@OptIn(ExperimentalTime::class)
class FakeLocalLedgerRemote(
    private val transactions: FakeTransactionRemote,
    private val owner: String,
) : LocalLedgerRemote {
    val splits = linkedMapOf<String, RemoteLocalSplit>()
    val settlements = linkedMapOf<String, RemoteLocalSettlement>()
    private var clockMillis = 1_900_000_000_000L
    private fun tick(): String = Instant.fromEpochMilliseconds(clockMillis++).toString()

    override suspend fun saveLocalSplit(transaction: LocalTransactionPayload, split: LocalSplitPayload) {
        val sum = split.shares.sumOf { BigDecimal.valueOf(it.shareAmount) }
        if (sum.compareTo(BigDecimal.valueOf(transaction.amount)) != 0) throw RowsRejectedException(400, "local_splits_shares_valid")
        val names = split.shares.map { it.localName?.trim()?.lowercase() ?: "" }
        if (names.toSet().size != names.size) throw RowsRejectedException(400, "local_splits_shares_valid")
        if (split.shares.any { it.localName != null && it.localName.trim().length !in 1..64 }) throw RowsRejectedException(400, "local_splits_shares_valid")

        transactions.writeFromOtherDevice(
            RemoteTransaction(
                id = transaction.id, ownerId = owner, type = transaction.type, amount = transaction.amount,
                category = transaction.category, merchant = transaction.merchant, paymentMethod = transaction.paymentMethod,
                date = transaction.date, note = transaction.note, createdAt = transaction.createdAt, updatedAt = "",
            )
        )
        val existing = splits.values.firstOrNull { it.transactionId == transaction.id }
        splits[transaction.id] = RemoteLocalSplit(
            id = existing?.id ?: split.id, transactionId = transaction.id, splitType = split.splitType,
            totalAmount = split.totalAmount, paidByLocalName = split.paidByLocalName, shares = split.shares,
            updatedAt = tick(),
        )
    }

    override suspend fun retractLocalSplits(transactionIds: List<String>) {
        transactionIds.forEach { id -> splits[id]?.let { splits[id] = it.copy(deletedAt = tick(), updatedAt = tick()) } }
    }

    override suspend fun splitsChangedSince(since: String?, limit: Int) = splits.values
        .filter { since == null || Instant.parse(it.updatedAt) >= Instant.parse(since) }
        .sortedBy { Instant.parse(it.updatedAt) }
        .take(limit)

    override suspend fun upsertSettlements(rows: List<LocalSettlementPayload>) {
        rows.forEach { row ->
            if (row.fromLocalName == null && row.toLocalName == null) throw RowsRejectedException(400, "local_settlements_two_parties")
            settlements[row.id] = RemoteLocalSettlement(
                id = row.id, fromLocalName = row.fromLocalName, toLocalName = row.toLocalName, amount = row.amount,
                isIou = row.isIou, date = row.date, note = row.note, createdAt = row.createdAt,
                updatedAt = tick(), deletedAt = row.deletedAt,
            )
        }
    }

    override suspend fun retractSettlements(ids: List<String>) {
        ids.forEach { id -> settlements[id]?.let { settlements[id] = it.copy(deletedAt = tick(), updatedAt = tick()) } }
    }

    override suspend fun settlementsChangedSince(since: String?, limit: Int) = settlements.values
        .filter { since == null || Instant.parse(it.updatedAt) >= Instant.parse(since) }
        .sortedBy { Instant.parse(it.updatedAt) }
        .take(limit)
}
