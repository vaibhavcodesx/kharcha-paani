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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** The correctness backlog fixes that live in LedgerRepository, against real Room (CI). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerBacklogFixesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "backlog-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val repository = LedgerRepository(db)

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun friend(name: String) = repository.addPerson(name, "#70C1FF")
    private suspend fun tx(id: Long) = db.transactionDao().getTransactionByIdDirect(id)
    private suspend fun splitOf(id: Long) = db.splitDao().getAllSplitsDirect().firstOrNull { it.transactionId == id }

    private suspend fun split(merchant: String, amount: Double, paidBy: Long, vararg shares: Pair<Long, Double>) =
        repository.saveTransactionWithSplits(
            // Dated now, so monthly analytics include it.
            personalTransaction(merchant, syncState = SyncState.LOCAL_ONLY, amount = amount).copy(date = System.currentTimeMillis()),
            paidByPersonId = paidBy,
            shares = shares.map { (person, share) -> SplitShareEntity(splitId = 0, personId = person, shareAmount = share) },
        )

    @Test
    fun `a repayment recorded in a local group settles it`() = runBlocking {
        // Codex review, 2026-10-23: the local-group balance never counted its settlements.
        val rahul = friend("Rahul")
        val groupId = repository.createGroup("Flat", memberPersonIds = listOf(rahul))
        repository.saveTransactionWithSplits(
            personalTransaction("Groceries", syncState = SyncState.LOCAL_ONLY, amount = 200.0).copy(groupId = groupId),
            paidByPersonId = -1L,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 100.0), SplitShareEntity(splitId = 0, personId = rahul, shareAmount = 100.0)),
        )
        suspend fun netIn() = repository.groupsWithDetails.first().single { it.group.id == groupId }.netBalanceForMe
        assertEquals(100.0, netIn(), 0.0)

        repository.recordSettlement(fromPersonId = rahul, toPersonId = -1L, amount = 60.0, groupId = groupId)
        assertEquals(40.0, netIn(), 0.0)
        repository.recordSettlement(fromPersonId = rahul, toPersonId = -1L, amount = 40.0, groupId = groupId)
        assertEquals(0.0, netIn(), 0.0)
    }

    @Test
    fun `a second friend with the same name is refused, whatever the case or spacing`() = runBlocking {
        // Their name is who they are to the sync: a second "Rahul" had his IOUs moved onto the first.
        friend("Rahul")

        val refused = runCatching { friend("  rahul ") }.exceptionOrNull()

        assertTrue(refused is DuplicateFriendException)
        assertEquals("Rahul", (refused as DuplicateFriendException).existingName)
        assertEquals(1, db.personDao().getAllPeopleDirect().count { it.name.equals("rahul", ignoreCase = true) })
        assertNotNull(friend("Rahul K"))
    }

    @Test
    fun `deleting a local group keeps its expenses and settlements but drops the dangling group id`() = runBlocking {
        val rahul = friend("Rahul")
        val groupId = repository.createGroup("Lonavala", memberPersonIds = listOf(rahul))
        val txId = repository.saveTransactionWithSplits(personalTransaction("Villa", syncState = SyncState.LOCAL_ONLY).copy(groupId = groupId))
        val settlementId = repository.recordSettlement(fromPersonId = rahul, toPersonId = -1L, amount = 10.0, groupId = groupId)
        val syncStateBefore = tx(txId)!!.syncState

        repository.deleteGroup(groupId)

        assertNull(db.groupDao().getGroupByIdDirect(groupId))
        assertNull(tx(txId)?.groupId)
        assertEquals("deleting a group doesn't start uploading its expenses", syncStateBefore, tx(txId)?.syncState)
        assertNull(db.settlementDao().getAllSettlementsDirect().single { it.id == settlementId }.groupId)
    }

    @Test
    fun `removing the only other person from a split I paid makes it my own expense`() = runBlocking {
        val rahul = friend("Rahul")
        val txId = split("Dinner", 100.0, paidBy = -1L, -1L to 50.0, rahul to 50.0)

        repository.deletePerson(rahul)

        assertFalse(tx(txId)!!.isSplit)
        assertEquals(100.0, tx(txId)!!.amount, 0.0)
        assertNull(splitOf(txId))
    }

    @Test
    fun `when the removed friend had paid, it becomes my expense of the share I had`() = runBlocking {
        val rahul = friend("Rahul")
        val txId = split("Cab", 100.0, paidBy = rahul, -1L to 40.0, rahul to 60.0)
        val spentBefore = repository.monthlyAnalytics.first().totalExpense

        repository.deletePerson(rahul)

        assertFalse(tx(txId)!!.isSplit)
        assertEquals(40.0, tx(txId)!!.amount, 0.0)
        assertNull(splitOf(txId))
        assertEquals(40.0, spentBefore, 0.0)
        assertEquals("my monthly spend doesn't change", spentBefore, repository.monthlyAnalytics.first().totalExpense, 0.0)
    }

    @Test
    fun `an expense the removed friend paid with no share of mine goes away`() = runBlocking {
        val rahul = friend("Rahul")
        val txId = split("Their gift", 80.0, paidBy = rahul, rahul to 80.0)

        repository.deletePerson(rahul)

        assertNull(tx(txId))
    }

    @Test
    fun `a split that still involves someone else stays a split`() = runBlocking {
        val rahul = friend("Rahul")
        val priya = friend("Priya")
        val iPaid = split("Movie", 90.0, paidBy = -1L, -1L to 30.0, rahul to 30.0, priya to 30.0)
        val priyaPaid = split("Snacks", 60.0, paidBy = priya, -1L to 30.0, rahul to 30.0)

        repository.deletePerson(rahul)

        assertTrue(tx(iPaid)!!.isSplit)
        assertEquals(setOf(-1L, priya), db.splitDao().getSharesForSplitDirect(splitOf(iPaid)!!.id).map { it.personId }.toSet())
        assertTrue("I still owe Priya my share", tx(priyaPaid)!!.isSplit)
        assertNotNull(splitOf(priyaPaid))
        assertEquals(30.0, repository.personBalances.first().single { it.person.id == priya }.totalIOweThem, 0.0)
    }

    @Test
    fun `balances round to the nearest paisa`() = runBlocking {
        val rahul = friend("Rahul")
        split("Trip", 100.0, paidBy = -1L, -1L to 66.67, rahul to 33.33)
        repository.recordSettlement(fromPersonId = rahul, toPersonId = -1L, amount = 0.29)

        val balance = repository.personBalances.first().single { it.person.id == rahul }

        // 33.33 - 0.29 = 33.04; truncating floating-point sums used to give 33.03 or 33.32-style results.
        assertEquals(33.04, balance.netBalance, 0.0)
        assertEquals(33.33, balance.totalTheyOweMe, 0.0)
    }
}
