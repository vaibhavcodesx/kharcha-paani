package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.UUID

/**
 * Friends without an account in a shared-group expense (design item 9): the member who adds the
 * expense hosts them and covers their shares; the friend owes the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestSharesTest {

    private val alice = "00000000-0000-4000-8000-0000000000a1" // this account
    private val bob = "00000000-0000-4000-8000-0000000000b1"
    private val goa = "10000000-0000-4000-8000-000000000001"

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "guests-${UUID.randomUUID()}.db"
    private val db = AppDatabase.builder(context, dbName).build()
    private val remote = FakeGroupRemote(self = alice).apply {
        profile(alice, "Alice")
        profile(bob, "Bob")
    }
    private val engine = GroupSyncEngine(db, remote, selfProfileId = alice)
    private val repository = LedgerRepository(db, selfProfileId = alice)
    private val dao get() = db.groupSyncDao()

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private suspend fun joinGoa(): Pair<Long, Long> {
        remote.groupCreatedBy(bob, goa, "Goa", alice)
        engine.sync()
        return dao.groupByRemoteId(goa)!!.id to dao.personByProfileId(bob)!!.id
    }

    private suspend fun balanceWith(personId: Long) = repository.personBalances.first().single { it.person.id == personId }.netBalance

    @Test
    fun `I host Ravi on a dinner Bob paid, so I owe Bob for both of us, and Ravi owes me`() = runBlocking {
        val (groupId, bobId) = joinGoa()
        val ravi = repository.addPerson("Ravi", "#FF8FB0")
        repository.saveTransactionWithSplits(
            personalTransaction("Shack", amount = 900.0, syncState = SyncState.LOCAL_ONLY).copy(groupId = groupId, date = System.currentTimeMillis()),
            paidByPersonId = bobId,
            shares = listOf(
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 300.0),
                SplitShareEntity(splitId = 0, personId = bobId, shareAmount = 300.0),
                SplitShareEntity(splitId = 0, personId = -1L, shareAmount = 300.0, guestName = "Ravi", guestPersonId = ravi),
            ),
        )

        assertEquals("my share and Ravi's, to Bob", -600.0, balanceWith(bobId), 0.0)
        assertEquals("Ravi owes me his share", 300.0, balanceWith(ravi), 0.0)
        assertEquals("only my own dinner is my spending", 300.0, repository.monthlyAnalytics.first().totalExpense, 0.0)
        val group = repository.groupsWithDetails.first().single { it.group.id == groupId }
        assertEquals(300.0, group.myShare, 0.0)
        assertEquals(-600.0, group.netBalanceForMe, 0.0)

        engine.sync()

        val sent = remote.savedExpenses.single()
        assertEquals(
            listOf(Triple(alice, null, BigDecimal("300.00")), Triple(bob, null, BigDecimal("300.00")), Triple(null, "Ravi", BigDecimal("300.00"))),
            sent.shares.map { Triple(it.profileId, it.localName, it.shareAmount) }.sortedBy { it.second ?: it.first },
        )
    }

    @Test
    fun `Bob's guest arrives on my phone as Bob's to cover, without planting a stranger`() = runBlocking {
        val (groupId, bobId) = joinGoa()
        val split = RemoteSplit(
            id = "sp-1", paidBy = alice, splitType = "equal", totalAmount = 900.0,
            shares = listOf(
                RemoteShare(id = "s1", profileId = alice, shareAmount = 300.0),
                RemoteShare(id = "s2", profileId = bob, shareAmount = 300.0),
                RemoteShare(id = "s3", localName = "Meera", shareAmount = 300.0),
            ),
        )
        val stamp = remote.tick()
        remote.expenses["bob-dinner"] = RemoteGroupTransaction(
            id = "bob-dinner", ownerId = bob, type = "expense", amount = 900.0, category = "Food", merchant = "Shack",
            paymentMethod = "UPI", date = stamp, note = "", groupId = goa, createdAt = stamp, updatedAt = stamp, deletedAt = null,
            splitJson = Json.encodeToJsonElement(RemoteSplit.serializer(), split),
        )

        engine.sync()

        val tx = db.syncDao().transactionByRemoteId("bob-dinner")!!
        val shares = dao.sharesForSplit(dao.splitForTransaction(tx.id)!!.id)
        val guest = shares.single { it.guestName != null }
        assertEquals(bobId, guest.personId)
        assertEquals("Meera", guest.guestName)
        assertNull("I don't know Bob's friends", guest.guestPersonId)
        assertEquals("no Meera in my friends", null, db.personDao().getAllPeopleDirect().firstOrNull { it.name == "Meera" })
        assertEquals("I paid: Bob owes me for himself and Meera", 600.0, balanceWith(bobId), 0.0)
        assertEquals(600.0, repository.groupsWithDetails.first().single { it.group.id == groupId }.netBalanceForMe, 0.0)
    }
}
