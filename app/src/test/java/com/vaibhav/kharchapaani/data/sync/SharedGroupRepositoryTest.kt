package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Repository rules for shared groups (decisions 1-7), against real Room. Runs on CI. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedGroupRepositoryTest {

    private val self = "00000000-0000-4000-8000-0000000000a1"
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "shared-groups-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private var syncRequests = 0
    private val repository = LedgerRepository(db, onLocalTransactionChange = { syncRequests++ }, selfProfileId = self)

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun sharedGroupBy(creator: String): Long = db.groupDao().insertGroup(
        GroupEntity(name = "Goa", syncState = SyncState.SYNCED, createdByProfileId = creator)
    )

    private suspend fun groupmate(profileId: String = "00000000-0000-4000-8000-0000000000b1") =
        db.personDao().insertPerson(PersonEntity(name = "Bob", avatarColor = "#70C1FF", profileId = profileId))

    @Test
    fun `a new shared group is queued with only this account as a member`() = runBlocking {
        val groupId = repository.createSharedGroup("Flat")

        val group = db.groupDao().getGroupByIdDirect(groupId)!!
        assertEquals(SyncState.PENDING, group.syncState)
        assertEquals(self, group.createdByProfileId)
        assertEquals(listOf(-1L), db.groupDao().getAllGroupMembersDirect().filter { it.groupId == groupId }.map { it.personId })
        assertEquals(1, syncRequests)
    }

    @Test
    fun `local groups and their expenses stay local`() = runBlocking {
        val groupId = repository.createGroup("Lonavala 2026")
        val txId = repository.saveTransactionWithSplits(personalTransaction("Chai").copy(groupId = groupId))

        assertEquals(SyncState.LOCAL_ONLY, db.groupDao().getGroupByIdDirect(groupId)?.syncState)
        assertEquals(SyncState.LOCAL_ONLY, db.transactionDao().getTransactionByIdDirect(txId)?.syncState)
    }

    @Test
    fun `moving an expense out of a shared group retracts the old copy under a new identity`() = runBlocking {
        val groupId = sharedGroupBy(self)
        val txId = repository.saveTransactionWithSplits(personalTransaction("Villa").copy(groupId = groupId))
        val before = db.transactionDao().getTransactionByIdDirect(txId)!!
        db.syncDao().markSyncedIfUnchanged(txId, before.updatedAt)

        repository.updateTransactionWithSplits(before.copy(groupId = null))

        val after = db.transactionDao().getTransactionByIdDirect(txId)!!
        assertNotEquals(before.remoteId, after.remoteId)
        assertEquals(SyncState.PENDING, after.syncState)
        assertEquals(listOf(before.remoteId), db.syncDao().pendingDeletes(10))
    }

    @Test
    fun `shared-group shares are rounded to paisa with the leftover on the payer`() = runBlocking {
        val groupId = sharedGroupBy(self)
        val bob = groupmate()

        // 3.335 and 6.665 each round up, over-covering 10.00 by a paisa that comes off the payer.
        val txId = repository.saveTransactionWithSplits(
            personalTransaction("Dinner", amount = 10.0).copy(groupId = groupId),
            paidByPersonId = bob,
            shares = listOf(
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 3.335),
                SplitShareEntity(splitId = 0, personId = bob, shareAmount = 6.665),
            ),
        )

        val split = db.groupSyncDao().splitForTransaction(txId)!!
        val shares = db.groupSyncDao().sharesForSplit(split.id).associate { it.personId to it.shareAmount }
        assertEquals(mapOf(-1L to 3.34, bob to 6.66), shares)
    }

    @Test
    fun `shares that don't add up are kept as entered for sync to reject`() = runBlocking {
        val groupId = sharedGroupBy(self)
        val bob = groupmate()

        val txId = repository.saveTransactionWithSplits(
            personalTransaction("Dinner", amount = 100.0).copy(groupId = groupId),
            shares = listOf(SplitShareEntity(splitId = 0, personId = bob, shareAmount = 40.0)),
        )

        val split = db.groupSyncDao().splitForTransaction(txId)!!
        assertEquals(listOf(40.0), db.groupSyncDao().sharesForSplit(split.id).map { it.shareAmount })
    }

    @Test
    fun `shared groups can't be deleted, only archived, and only by their creator`() = runBlocking {
        val mine = sharedGroupBy(self)
        val theirs = sharedGroupBy("00000000-0000-4000-8000-0000000000b1")

        repository.deleteGroup(mine)
        assertNotNull(db.groupDao().getGroupByIdDirect(mine))

        repository.setGroupArchived(theirs, archived = true)
        assertNull(db.groupDao().getGroupByIdDirect(theirs)?.archivedAt)

        repository.setGroupArchived(mine, archived = true)
        val archived = db.groupDao().getGroupByIdDirect(mine)!!
        assertNotNull(archived.archivedAt)
        assertEquals(SyncState.PENDING, archived.syncState)

        repository.setGroupArchived(mine, archived = false)
        assertNull(db.groupDao().getGroupByIdDirect(mine)?.archivedAt)
    }

    @Test
    fun `only the creator can rename a shared group, and membership isn't editable`() = runBlocking {
        val theirs = sharedGroupBy("00000000-0000-4000-8000-0000000000b1")
        val mine = sharedGroupBy(self)
        val stranger = groupmate("00000000-0000-4000-8000-0000000000c1")

        repository.updateGroup(db.groupDao().getGroupByIdDirect(theirs)!!.copy(name = "Renamed"), listOf(stranger))
        repository.updateGroup(db.groupDao().getGroupByIdDirect(mine)!!.copy(name = "Renamed"), listOf(stranger))

        assertEquals("Goa", db.groupDao().getGroupByIdDirect(theirs)?.name)
        assertEquals("Renamed", db.groupDao().getGroupByIdDirect(mine)?.name)
        assertEquals(emptyList<Long>(), db.groupDao().getAllGroupMembersDirect().map { it.personId })
    }

    @Test
    fun `a groupmate can't be deleted as a local friend`() = runBlocking {
        val bob = groupmate()

        repository.deletePerson(bob)

        assertNotNull(db.personDao().getPersonById(bob))
    }

    @Test
    fun `analytics ignore a groupmate's expense I have no share in`() = runBlocking {
        val groupId = sharedGroupBy(self)
        val bob = groupmate()
        val now = System.currentTimeMillis()
        val theirTx = db.transactionDao().insertTransaction(
            personalTransaction("Their taxi", amount = 500.0).copy(
                groupId = groupId, isSplit = true, date = now, ownerProfileId = "00000000-0000-4000-8000-0000000000b1",
            )
        )
        val splitId = db.splitDao().insertSplit(SplitEntity(transactionId = theirTx, totalAmount = 500.0, paidByPersonId = bob))
        db.splitDao().insertSplitShares(listOf(SplitShareEntity(splitId = splitId, personId = bob, shareAmount = 500.0)))
        repository.saveTransactionWithSplits(personalTransaction("My chai", amount = 20.0).copy(date = now))

        val analytics = repository.monthlyAnalytics.first()

        assertEquals(20.0, analytics.totalExpense, 0.0)
        assertEquals(listOf(1), analytics.categorySummaries.map { it.count })
    }

    @Test
    fun `a shared group's balance for me includes groupmates' settlements`() = runBlocking {
        val groupId = sharedGroupBy(self)
        val bob = groupmate()
        repository.saveTransactionWithSplits(
            personalTransaction("Villa", amount = 100.0).copy(groupId = groupId),
            paidByPersonId = -1L,
            shares = listOf(-1L, bob).map { SplitShareEntity(splitId = 0, personId = it, shareAmount = 50.0) },
        )
        repository.recordSettlement(fromPersonId = bob, toPersonId = -1L, amount = 30.0, groupId = groupId)

        val details = repository.groupsWithDetails.first().single { it.group.id == groupId }

        assertEquals(20.0, details.netBalanceForMe, 0.0)
    }
}
