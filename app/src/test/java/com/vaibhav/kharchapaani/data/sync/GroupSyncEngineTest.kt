package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.UUID

/** The group sync rules against real Room (runs on CI) and a fake server with the schema's rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupSyncEngineTest {

    private val alice = "00000000-0000-4000-8000-0000000000a1" // this account
    private val bob = "00000000-0000-4000-8000-0000000000b1"
    private val carol = "00000000-0000-4000-8000-0000000000c1"
    private val goa = "10000000-0000-4000-8000-000000000001"

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "group-sync-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val remote = FakeGroupRemote(self = alice).apply {
        profile(alice, "Alice")
        profile(bob, "Bob")
        profile(carol, "Carol")
    }
    private val engine = GroupSyncEngine(db, remote, selfProfileId = alice)
    private val repository = LedgerRepository(db, selfProfileId = alice)
    private val groupDao get() = db.groupSyncDao()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun person(profileId: String) = groupDao.personByProfileId(profileId)!!
    private suspend fun localGroup(remoteId: String = goa) = groupDao.groupByRemoteId(remoteId)!!
    private suspend fun transaction(remoteId: String) = db.syncDao().transactionByRemoteId(remoteId)

    private suspend fun joinGoaWithBobAndCarol(): GroupEntity {
        remote.groupCreatedBy(bob, goa, "Goa", alice, carol)
        engine.sync()
        return localGroup()
    }

    @Test
    fun `a shared group created here is created on the server and its members come back as people`() = runBlocking {
        val groupId = repository.createSharedGroup("Flat", description = "rent", colorHex = "#C6F24C")
        val group = groupDao.groupById(groupId)!!

        engine.sync()
        assertEquals(SyncState.SYNCED, groupDao.groupById(groupId)?.syncState)
        val server = remote.groups.getValue(group.remoteId)
        assertEquals(listOf("Flat", "rent", "#C6F24C", alice), listOf(server.name, server.description, server.colorHex, server.createdBy))

        remote.join(group.remoteId, bob)
        engine.sync()

        val members = db.groupDao().getAllGroupMembersDirect().filter { it.groupId == groupId }.map { it.personId }.toSet()
        val bobPerson = person(bob)
        assertEquals(setOf(-1L, bobPerson.id), members)
        assertEquals("Bob", bobPerson.name)
        assertNull("no person row is made for this account", groupDao.personByProfileId(alice))
    }

    @Test
    fun `a group expense is saved with exact paisa shares and profile ids`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        val bobId = person(bob).id
        val carolId = person(carol).id

        val txId = repository.saveTransactionWithSplits(
            groupExpense(group.id, 100.0),
            paidByPersonId = -1L,
            shares = listOf(-1L, bobId, carolId).map { SplitShareEntity(splitId = 0, personId = it, shareAmount = 100.0 / 3) },
        )
        assertEquals(SyncState.PENDING, db.transactionDao().getTransactionByIdDirect(txId)?.syncState)

        engine.sync()

        val saved = remote.savedExpenses.single()
        assertEquals(BigDecimal("100.00"), saved.transaction.amount)
        assertEquals(alice, saved.split.paidBy)
        assertEquals(
            mapOf(alice to BigDecimal("33.34"), bob to BigDecimal("33.33"), carol to BigDecimal("33.33")),
            saved.shares.associate { it.profileId to it.shareAmount },
        )
        assertEquals(goa, saved.transaction.groupId)
        assertEquals(SyncState.SYNCED, db.transactionDao().getTransactionByIdDirect(txId)?.syncState)
    }

    @Test
    fun `an expense shared with a local-only friend fails without being sent, and others still sync`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        val localFriend = db.personDao().insertPerson(PersonEntity(name = "Rahul", avatarColor = "#70C1FF"))

        val bad = repository.saveTransactionWithSplits(
            groupExpense(group.id, 50.0, "Bad"),
            shares = listOf(SplitShareEntity(splitId = 0, personId = localFriend, shareAmount = 50.0)),
        )
        val good = repository.saveTransactionWithSplits(
            groupExpense(group.id, 50.0, "Good"),
            shares = listOf(SplitShareEntity(splitId = 0, personId = person(bob).id, shareAmount = 50.0)),
        )

        engine.sync()

        assertEquals(SyncState.FAILED, db.transactionDao().getTransactionByIdDirect(bad)?.syncState)
        assertEquals(SyncState.SYNCED, db.transactionDao().getTransactionByIdDirect(good)?.syncState)
        assertEquals(listOf("Good"), remote.savedExpenses.map { it.transaction.merchant })
    }

    @Test
    fun `the server refusing an expense marks only that expense failed`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        val txId = repository.saveTransactionWithSplits(
            groupExpense(group.id, 80.0),
            shares = listOf(SplitShareEntity(splitId = 0, personId = person(bob).id, shareAmount = 80.0)),
        )
        // Bob leaves before the push reaches the server, so his share is refused.
        remote.leave(goa, bob)

        engine.pushGroupExpenses()

        assertEquals(SyncState.FAILED, db.transactionDao().getTransactionByIdDirect(txId)?.syncState)
    }

    @Test
    fun `a groupmate's expense arrives read-only with their split mapped to local people`() = runBlocking {
        joinGoaWithBobAndCarol()
        remote.expenseBy(
            owner = bob, id = "tx-bob", groupId = goa, amount = 90.0, paidBy = bob,
            shares = mapOf(alice to 30.0, bob to 30.0, carol to 30.0),
            removedShares = mapOf("00000000-0000-4000-8000-0000000000d1" to 10.0),
        )

        engine.sync()

        val row = transaction("tx-bob")!!
        assertEquals(bob, row.ownerProfileId)
        assertEquals(SyncState.SYNCED, row.syncState)
        assertEquals(localGroup().id, row.groupId)
        val split = groupDao.splitForTransaction(row.id)!!
        assertEquals(person(bob).id, split.paidByPersonId)
        val shares = groupDao.sharesForSplit(split.id).associate { it.personId to it.shareAmount }
        assertEquals(mapOf(-1L to 30.0, person(bob).id to 30.0, person(carol).id to 30.0), shares)

        // Bob added it and Bob paid: it can't be edited or deleted here.
        repository.deleteTransaction(row.id)
        repository.updateTransactionWithSplits(row.copy(merchant = "Changed"))
        assertEquals("Shack", db.transactionDao().getTransactionByIdDirect(row.id)?.merchant)
    }

    @Test
    fun `an expense a groupmate added but I paid can be edited here and the edit reaches the server`() = runBlocking {
        joinGoaWithBobAndCarol()
        remote.expenseBy(owner = bob, id = "tx-paid-by-me", groupId = goa, amount = 90.0, paidBy = alice, shares = mapOf(alice to 45.0, bob to 45.0))
        engine.sync()
        val row = transaction("tx-paid-by-me")!!
        val full = db.transactionDao().getAllTransactionsDirect().first { it.id == row.id }
        val split = groupDao.splitForTransaction(row.id)!!
        assertEquals(-1L, split.paidByPersonId)

        repository.updateTransactionWithSplits(
            full.copy(merchant = "Shack (fixed)", amount = 100.0),
            paidByPersonId = -1L,
            shares = listOf(
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 50.0),
                SplitShareEntity(splitId = 0, personId = person(bob).id, shareAmount = 50.0),
            ),
            splitType = "exact",
        )

        val edited = db.transactionDao().getTransactionByIdDirect(row.id)!!
        assertEquals("Shack (fixed)", edited.merchant)
        assertEquals(SyncState.PENDING, edited.syncState)
        assertEquals("it still belongs to Bob, who added it", bob, edited.ownerProfileId)

        engine.pushGroupExpenses()

        assertEquals(SyncState.SYNCED, db.transactionDao().getTransactionByIdDirect(row.id)?.syncState)
        val server = remote.expenses.getValue("tx-paid-by-me")
        assertEquals(listOf("Shack (fixed)", 100.0, bob, alice), listOf(server.merchant, server.amount, server.ownerId, server.split!!.paidBy))
    }

    @Test
    fun `the payer of a groupmate's expense can delete it but can't hand the payment to someone else`() = runBlocking {
        joinGoaWithBobAndCarol()
        remote.expenseBy(owner = bob, id = "tx-mine-to-pay", groupId = goa, amount = 60.0, paidBy = alice, shares = mapOf(alice to 30.0, bob to 30.0))
        engine.sync()
        val row = transaction("tx-mine-to-pay")!!

        // Only Bob, who added it, can say someone else paid.
        repository.updateTransactionWithSplits(
            row.copy(merchant = "Carol paid?"),
            paidByPersonId = person(carol).id,
            shares = listOf(SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 30.0), SplitShareEntity(splitId = 0, personId = person(bob).id, shareAmount = 30.0)),
        )
        assertEquals("Shack", db.transactionDao().getTransactionByIdDirect(row.id)?.merchant)

        repository.deleteTransaction(row.id)

        assertNull(db.transactionDao().getTransactionByIdDirect(row.id))
        assertTrue(db.syncDao().hasPendingDelete("tx-mine-to-pay"))
    }

    @Test
    fun `pulling an unchanged expense again leaves its split and shares untouched, a real change rewrites them`() = runBlocking {
        joinGoaWithBobAndCarol()
        remote.expenseBy(owner = bob, id = "tx-same", groupId = goa, amount = 60.0, paidBy = bob, shares = mapOf(alice to 30.0, bob to 30.0))
        engine.sync()
        val row = transaction("tx-same")!!
        val split = groupDao.splitForTransaction(row.id)!!
        val shareIds = groupDao.sharesForSplit(split.id).map { it.id }.toSet()

        // The same content arrives again with a newer clock, as the cursor overlap and parent bumps do.
        remote.expenses["tx-same"] = remote.expenses.getValue("tx-same").copy(updatedAt = remote.tick())
        db.syncDao().deleteMeta(GroupSyncEngine.GROUP_EXPENSES_PULL_CURSOR)
        engine.pullGroupExpenses()

        assertEquals(split.id, groupDao.splitForTransaction(row.id)!!.id)
        assertEquals(shareIds, groupDao.sharesForSplit(split.id).map { it.id }.toSet())

        remote.expenseBy(owner = bob, id = "tx-same", groupId = goa, amount = 60.0, paidBy = bob, shares = mapOf(alice to 20.0, bob to 40.0))
        engine.pullGroupExpenses()

        val rewritten = groupDao.splitForTransaction(row.id)!!
        assertEquals(mapOf(-1L to 20.0, person(bob).id to 40.0), groupDao.sharesForSplit(rewritten.id).associate { it.personId to it.shareAmount })
    }

    @Test
    fun `a pulled delete removes a synced expense but never a queued local edit`() = runBlocking {
        joinGoaWithBobAndCarol()
        remote.expenseBy(owner = bob, id = "tx-1", groupId = goa, amount = 40.0, paidBy = bob, shares = mapOf(alice to 20.0, bob to 20.0))
        remote.expenseBy(owner = alice, id = "tx-2", groupId = goa, amount = 40.0, paidBy = alice, shares = mapOf(alice to 20.0, bob to 20.0))
        engine.sync()
        val mine = transaction("tx-2")!!
        repository.updateTransactionWithSplits(
            mine.copy(merchant = "Edited offline"),
            shares = listOf(SplitShareEntity(splitId = 0, personId = person(bob).id, shareAmount = 40.0)),
        )

        remote.deleteExpense("tx-1")
        remote.deleteExpense("tx-2")
        engine.pullGroupExpenses()

        assertNull(transaction("tx-1"))
        assertEquals("Edited offline", transaction("tx-2")?.merchant)
        assertEquals(SyncState.PENDING, transaction("tx-2")?.syncState)
    }

    @Test
    fun `an edit that removes someone from a split keeps the same server split`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        val bobId = person(bob).id
        val carolId = person(carol).id
        val txId = repository.saveTransactionWithSplits(
            groupExpense(group.id, 60.0),
            shares = listOf(bobId, carolId).map { SplitShareEntity(splitId = 0, personId = it, shareAmount = 30.0) },
        )
        engine.sync()
        val splitId = remote.savedExpenses.single().split.id

        val row = db.transactionDao().getTransactionByIdDirect(txId)!!
        repository.updateTransactionWithSplits(row, shares = listOf(SplitShareEntity(splitId = 0, personId = bobId, shareAmount = 60.0)))
        engine.sync()

        assertEquals(splitId, remote.savedExpenses.last().split.id)
        val server = remote.expenses.getValue(row.remoteId)
        assertEquals(listOf(bob), server.activeShares.map { it.profileId })
        assertEquals(mapOf(bobId to 60.0), groupDao.sharesForSplit(groupDao.splitForTransaction(txId)!!.id).associate { it.personId to it.shareAmount })
    }

    @Test
    fun `joining a group pulls its history even when it is older than the pull cursor`() = runBlocking {
        remote.groupCreatedBy(bob, goa, "Goa", carol)
        remote.expenseBy(owner = bob, id = "old", groupId = goa, amount = 10.0, paidBy = bob, shares = mapOf(carol to 10.0))
        remote.groupCreatedBy(carol, "20000000-0000-4000-8000-000000000002", "Flat", alice)
        remote.expenseBy(owner = carol, id = "newer", groupId = "20000000-0000-4000-8000-000000000002", amount = 5.0, paidBy = carol, shares = mapOf(alice to 5.0))
        engine.sync()
        assertNotNull(db.syncDao().meta(GroupSyncEngine.GROUP_EXPENSES_PULL_CURSOR))
        assertNull(transaction("old"))

        remote.join(goa, alice)
        engine.sync()

        assertNotNull("history from before joining must arrive", transaction("old"))
        assertNotNull(transaction("newer"))
    }

    @Test
    fun `a group this account left elsewhere is removed with its expenses and settlements, local groups untouched`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        remote.expenseBy(owner = bob, id = "tx", groupId = goa, amount = 10.0, paidBy = bob, shares = mapOf(alice to 10.0))
        remote.settlementBy("s1", goa, from = alice, to = bob, amount = 10.0)
        val localGroupId = repository.createGroup("Local trip")
        engine.sync()
        assertNotNull(transaction("tx"))

        remote.leave(goa, alice)
        engine.sync()

        assertNull(groupDao.groupById(group.id))
        assertNull(transaction("tx"))
        assertNull(groupDao.settlementByRemoteId("s1"))
        assertNotNull(groupDao.groupById(localGroupId))
    }

    @Test
    fun `settlements push, pull and retract`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        val bobId = person(bob).id
        val mineId = repository.recordSettlement(fromPersonId = -1L, toPersonId = bobId, amount = 25.5, groupId = group.id)
        remote.settlementBy("from-carol", goa, from = carol, to = bob, amount = 12.0)

        engine.sync()

        val mine = groupDao.settlementById(mineId)!!
        assertEquals(SyncState.SYNCED, mine.syncState)
        assertEquals(25.5, remote.settlements.getValue(mine.remoteId).amount, 0.0)
        val pulled = groupDao.settlementByRemoteId("from-carol")!!
        assertEquals(listOf(person(carol).id, bobId), listOf(pulled.fromPersonId, pulled.toPersonId))

        // Not a party: read-only here.
        repository.deleteSettlement(pulled.id)
        assertNotNull(groupDao.settlementById(pulled.id))

        repository.deleteSettlement(mineId)
        assertEquals(listOf(mine.remoteId), db.syncDao().pendingDeletes(10, PendingRemoteDeleteEntity.TYPE_SETTLEMENT))
        assertEquals("settlement tombstones aren't transaction deletes", emptyList<String>(), db.syncDao().pendingDeletes(10))
        engine.sync()
        assertNotNull(remote.settlements.getValue(mine.remoteId).deletedAt)
        assertNull(groupDao.settlementByRemoteId(mine.remoteId))

        remote.retractSettlement("from-carol")
        engine.sync()
        assertNull(groupDao.settlementByRemoteId("from-carol"))
    }

    @Test
    fun `a retraction the server refuses after the group is archived is dropped and the settlement restored`() = runBlocking {
        val group = joinGoaWithBobAndCarol()
        val settlementId = repository.recordSettlement(fromPersonId = -1L, toPersonId = person(bob).id, amount = 40.0, groupId = group.id)
        engine.sync()
        val remoteId = groupDao.settlementById(settlementId)!!.remoteId

        // Retracted offline; meanwhile the creator archives the group on their phone.
        repository.deleteSettlement(settlementId)
        remote.archive(goa)
        engine.sync()

        assertEquals("a refused tombstone isn't retried forever", emptyList<String>(), db.syncDao().pendingDeletes(10, PendingRemoteDeleteEntity.TYPE_SETTLEMENT))
        assertNull(remote.settlements.getValue(remoteId).deletedAt)
        assertNotNull("the settlement still exists for everyone, so it comes back here", groupDao.settlementByRemoteId(remoteId))
        assertNotNull(localGroup().archivedAt)
    }

    @Test
    fun `offline sync leaves everything queued`() = runBlocking {
        val groupId = repository.createSharedGroup("Flat")
        remote.online = false

        runCatching { engine.sync() }

        assertEquals(SyncState.PENDING, groupDao.groupById(groupId)?.syncState)
    }

    private fun groupExpense(groupId: Long, amount: Double, merchant: String = "Villa") =
        personalTransaction(merchant, syncState = SyncState.LOCAL_ONLY, amount = amount).copy(groupId = groupId)
}
