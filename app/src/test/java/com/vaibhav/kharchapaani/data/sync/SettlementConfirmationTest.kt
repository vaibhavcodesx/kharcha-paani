package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.repository.NotYoursToWithdrawException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * Settlement confirmation (design item 10) end to end on one phone: real Room, the real sync engine,
 * and a fake server with the staged migration's rules. Bob's side is played by writing his rows and
 * answers on the server, the way his phone would.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettlementConfirmationTest {

    private val alice = "00000000-0000-4000-8000-0000000000a1" // this account
    private val bob = "00000000-0000-4000-8000-0000000000b1"
    private val goa = "10000000-0000-4000-8000-000000000001"

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "settlement-confirmation-${UUID.randomUUID()}.db"
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

    private suspend fun balanceWithBob(bobId: Long) = repository.personBalances.first().single { it.person.id == bobId }.netBalance

    @Test
    fun `what I record is pending until Bob confirms, and only then moves the balance`() = runBlocking {
        val (groupId, bobId) = joinGoa()
        val id = repository.recordSettlement(fromPersonId = -1L, toPersonId = bobId, amount = 500.0, groupId = groupId)

        assertEquals(SettlementStatus.PENDING, dao.settlementById(id)!!.status)
        assertEquals("a claim isn't a payment yet", 0.0, balanceWithBob(bobId), 0.0)

        engine.sync()
        val remoteId = dao.settlementById(id)!!.remoteId
        assertEquals("pending", remote.settlements.getValue(remoteId).status)
        assertEquals(alice, remote.settlements.getValue(remoteId).recordedBy)

        remote.answeredBy(remoteId, confirm = true)
        engine.sync()

        assertEquals(SettlementStatus.CONFIRMED, dao.settlementById(id)!!.status)
        assertEquals(500.0, balanceWithBob(bobId), 0.0)
    }

    @Test
    fun `one Bob records waits for my answer, and I can't make it disappear`() = runBlocking {
        val (_, bobId) = joinGoa()
        remote.settlementBy("from-bob", goa, from = bob, to = alice, amount = 300.0, recordedBy = bob)
        engine.sync()

        val pulled = dao.settlementByRemoteId("from-bob")!!
        assertEquals(SettlementStatus.PENDING, pulled.status)
        assertEquals(bobId, pulled.recordedByPersonId)
        assertEquals(listOf(pulled.id), dao.settlementsAwaitingMyAnswer().map { it.id })
        assertEquals(0.0, balanceWithBob(bobId), 0.0)

        try {
            repository.deleteSettlement(pulled.id)
            fail("the other party answers instead of deleting")
        } catch (e: NotYoursToWithdrawException) {
            assertNotNull(dao.settlementById(pulled.id))
        }
    }

    @Test
    fun `confirming counts at once on this phone and reaches the server on the next sync`() = runBlocking {
        val (_, bobId) = joinGoa()
        remote.settlementBy("from-bob", goa, from = bob, to = alice, amount = 300.0, recordedBy = bob)
        engine.sync()
        val id = dao.settlementByRemoteId("from-bob")!!.id

        repository.respondToSettlement(id, confirm = true)

        assertEquals(SettlementStatus.CONFIRMED, dao.settlementById(id)!!.status)
        assertEquals("Bob paid me 300, so I owe him that now", -300.0, balanceWithBob(bobId), 0.0)
        assertTrue(dao.settlementsAwaitingMyAnswer().isEmpty())

        engine.sync()
        assertEquals(listOf("from-bob" to true), remote.answers)
        assertEquals("confirmed", remote.settlements.getValue("from-bob").status)
        assertNull(dao.settlementById(id)!!.pendingResponse)
        assertEquals(SettlementStatus.CONFIRMED, dao.settlementById(id)!!.status)
    }

    @Test
    fun `saying it isn't right leaves the balance alone and sends the note`() = runBlocking {
        val (_, bobId) = joinGoa()
        remote.settlementBy("from-bob", goa, from = bob, to = alice, amount = 300.0, recordedBy = bob)
        engine.sync()
        val id = dao.settlementByRemoteId("from-bob")!!.id

        repository.respondToSettlement(id, confirm = false, note = "  That was the Goa cab, already settled  ")
        engine.sync()

        val local = dao.settlementById(id)!!
        assertEquals(SettlementStatus.REJECTED, local.status)
        assertEquals("That was the Goa cab, already settled", local.responseNote)
        assertEquals(0.0, balanceWithBob(bobId), 0.0)
        assertEquals("rejected", remote.settlements.getValue("from-bob").status)
        assertEquals("That was the Goa cab, already settled", remote.settlements.getValue("from-bob").responseNote)
    }

    @Test
    fun `an answer to one Bob withdrew meanwhile is dropped, and the settlement goes`() = runBlocking {
        joinGoa()
        remote.settlementBy("from-bob", goa, from = bob, to = alice, amount = 300.0, recordedBy = bob)
        engine.sync()
        val id = dao.settlementByRemoteId("from-bob")!!.id

        repository.respondToSettlement(id, confirm = true)
        remote.retractSettlement("from-bob")
        engine.sync()

        assertTrue(dao.settlementsWithUnsentAnswers(10).isEmpty())
        assertNull(dao.settlementByRemoteId("from-bob"))
    }

    @Test
    fun `I can withdraw my own pending one, and clear it once Bob says it isn't right`() = runBlocking {
        val (groupId, bobId) = joinGoa()
        val first = repository.recordSettlement(fromPersonId = -1L, toPersonId = bobId, amount = 100.0, groupId = groupId)
        engine.sync()
        val firstRemote = dao.settlementById(first)!!.remoteId

        repository.deleteSettlement(first)
        engine.sync()
        assertNotNull(remote.settlements.getValue(firstRemote).deletedAt)

        val second = repository.recordSettlement(fromPersonId = -1L, toPersonId = bobId, amount = 200.0, groupId = groupId)
        engine.sync()
        val secondRemote = dao.settlementById(second)!!.remoteId
        remote.answeredBy(secondRemote, confirm = false, note = "Never got it")
        engine.sync()
        assertEquals(SettlementStatus.REJECTED, dao.settlementById(second)!!.status)
        assertEquals("Never got it", dao.settlementById(second)!!.responseNote)

        repository.deleteSettlement(second)
        engine.sync()
        assertNotNull(remote.settlements.getValue(secondRemote).deletedAt)
        assertEquals(0.0, balanceWithBob(bobId), 0.0)
    }

    @Test
    fun `settlements from before confirmation count as they always did`() = runBlocking {
        val (_, bobId) = joinGoa()
        remote.settlementBy("old", goa, from = bob, to = alice, amount = 50.0)
        engine.sync()

        assertEquals(SettlementStatus.CONFIRMED, dao.settlementByRemoteId("old")!!.status)
        assertEquals(-50.0, balanceWithBob(bobId), 0.0)
        assertTrue(dao.settlementsAwaitingMyAnswer().isEmpty())
    }
}
