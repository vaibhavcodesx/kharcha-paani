package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
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
 * A friend's balance and their history are one computation (TODOS.md, "One source for the per-friend
 * balance maths"): ask-to-pay's itemised message promises its lines add up to the balance.
 *
 * One ledger with every kind of row in it: splits either of us paid, a three-way split to the paisa,
 * a guest I host on a groupmate's expense and on a friend's, a groupmate's own guest, payments and
 * IOUs both ways, and a pending and a rejected payment that mustn't count. The figures are pinned,
 * so a change to either side that moves a number fails here, and every friend's rows must add up to
 * their balance.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonBalanceOneSourceTest {

    private val db: AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val repository = LedgerRepository(db, selfProfileId = "me")
    private val me = -1L

    @After
    fun tearDown() = db.close()

    private var rohan = 0L
    private var bob = 0L
    private var ravi = 0L
    private var meera = 0L
    private val splitIds = mutableMapOf<String, Long>()
    private val settlementIds = mutableMapOf<String, Long>()

    private suspend fun split(name: String, amount: Double, paidBy: Long, date: Long, groupId: Long?, vararg shares: SplitShareEntity) {
        val txId = db.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense", amount = amount, category = "Food", merchant = name, paymentMethod = "UPI",
                date = date, isSplit = true, groupId = groupId, createdAt = date,
            )
        )
        val splitId = db.splitDao().insertSplit(SplitEntity(transactionId = txId, totalAmount = amount, paidByPersonId = paidBy, splitType = "exact"))
        db.groupSyncDao().insertShares(shares.map { it.copy(splitId = splitId) })
        splitIds[name] = splitId
    }

    private fun share(personId: Long, amount: Double, guestName: String? = null, guestPersonId: Long? = null) =
        SplitShareEntity(splitId = 0, personId = personId, shareAmount = amount, guestName = guestName, guestPersonId = guestPersonId)

    private suspend fun payment(key: String, from: Long, to: Long, amount: Double, date: Long, isIou: Boolean = false, groupId: Long? = null, status: SettlementStatus = SettlementStatus.CONFIRMED) {
        settlementIds[key] = db.groupSyncDao().insertSettlement(
            SettlementEntity(fromPersonId = from, toPersonId = to, amount = amount, date = date, isIou = isIou, groupId = groupId, createdAt = date, status = status)
        )
    }

    private suspend fun seed() {
        rohan = db.personDao().insertPerson(PersonEntity(name = "Rohan", avatarColor = "#70C1FF"))
        bob = db.personDao().insertPerson(PersonEntity(name = "Bob", avatarColor = "#C6F24C", profileId = "00000000-0000-4000-8000-0000000000b1"))
        ravi = db.personDao().insertPerson(PersonEntity(name = "Ravi", avatarColor = "#FF8FB0"))
        meera = db.personDao().insertPerson(PersonEntity(name = "Meera", avatarColor = "#F2D06B"))
        val goa = db.groupDao().insertGroup(GroupEntity(name = "Goa", syncState = SyncState.SYNCED))

        split("Dinner", 150.50, paidBy = me, date = 1_000L, groupId = null, share(me, 75.25), share(rohan, 75.25))
        split("Auto", 90.0, paidBy = rohan, date = 2_000L, groupId = null, share(me, 45.0), share(rohan, 45.0))
        // Bob paid; I host Ravi, so I owe Bob for both of us and Ravi owes me.
        split("Shack", 900.0, paidBy = bob, date = 3_000L, groupId = goa, share(me, 300.0), share(bob, 300.0), share(me, 300.0, "Ravi", ravi))
        split("Cafe", 100.0, paidBy = me, date = 4_000L, groupId = null, share(me, 33.34), share(rohan, 33.33), share(bob, 33.33))
        // I paid; Bob hosts a Meera I don't know (no guestPersonId), so Bob owes me for both, and
        // my friend Meera, who merely shares the name, owes nothing.
        split("Taxi", 250.0, paidBy = me, date = 5_000L, groupId = goa, share(me, 100.0), share(bob, 100.0), share(bob, 50.0, "Meera", null))
        // Rohan paid; my guest Ravi came along.
        split("Lunch", 60.0, paidBy = rohan, date = 6_000L, groupId = null, share(me, 20.0), share(rohan, 20.0), share(me, 20.0, "Ravi", ravi))

        // Same date as Dinner: the history must keep the split before the payment.
        payment("rohanPaid", from = rohan, to = me, amount = 20.0, date = 1_000L)
        payment("iLentRohan", from = me, to = rohan, amount = 500.0, date = 7_000L, isIou = true)
        payment("bobPending", from = bob, to = me, amount = 100.0, date = 8_000L, groupId = goa, status = SettlementStatus.PENDING)
        payment("iPaidBobRejected", from = me, to = bob, amount = 70.0, date = 9_000L, groupId = goa, status = SettlementStatus.REJECTED)
        payment("bobPaid", from = bob, to = me, amount = 50.0, date = 10_000L, groupId = goa)
        payment("raviLent", from = ravi, to = me, amount = 10.5, date = 11_000L, isIou = true)
    }

    private suspend fun balance(personId: Long) = repository.personBalances.first().single { it.person.id == personId }

    @Test
    fun `every friend's balance and both of its totals are the pinned figures`() = runTest {
        seed()

        // Rohan: 75.25 + 33.33 + 500 owed to me; 45 + 40 (my share and Ravi's at lunch) + 20 to him.
        with(balance(rohan)) {
            assertEquals(608.58, totalTheyOweMe, 0.0)
            assertEquals(105.0, totalIOweThem, 0.0)
            assertEquals(503.58, netBalance, 0.0)
        }
        // Bob: 33.33 + 150 (him and his guest) owed to me; 600 (me and Ravi) + 50 paid. The pending
        // 100 and the rejected 70 count for nothing.
        with(balance(bob)) {
            assertEquals(183.33, totalTheyOweMe, 0.0)
            assertEquals(650.0, totalIOweThem, 0.0)
            assertEquals(-466.67, netBalance, 0.0)
        }
        // Ravi: 300 + 20 as my guest; lent me 10.50.
        with(balance(ravi)) {
            assertEquals(320.0, totalTheyOweMe, 0.0)
            assertEquals(10.5, totalIOweThem, 0.0)
            assertEquals(309.5, netBalance, 0.0)
        }
        with(balance(meera)) {
            assertEquals(0.0, totalTheyOweMe, 0.0)
            assertEquals(0.0, totalIOweThem, 0.0)
            assertEquals(0.0, netBalance, 0.0)
        }
    }

    @Test
    fun `every friend's history adds up to their balance`() = runTest {
        seed()

        for (person in listOf(rohan, bob, ravi, meera)) {
            val rows = repository.getLedgerForPerson(person).first()
            assertEquals("person $person", balance(person).netBalance, rows.sumOf { it.netChange }, 0.001)
        }
    }

    @Test
    fun `each history lists the same rows in the same order`() = runTest {
        seed()

        fun s(name: String) = "split_${splitIds.getValue(name)}"
        fun g(name: String) = "guest_${splitIds.getValue(name)}"
        fun p(key: String) = "settlement_${settlementIds.getValue(key)}"
        suspend fun history(personId: Long) = repository.getLedgerForPerson(personId).first().map { it.id to it.netChange }

        assertEquals(
            listOf(p("iLentRohan") to 500.0, s("Lunch") to -40.0, s("Cafe") to 33.33, s("Auto") to -45.0, s("Dinner") to 75.25, p("rohanPaid") to -20.0),
            history(rohan),
        )
        assertEquals(
            listOf(p("bobPaid") to -50.0, p("iPaidBobRejected") to 0.0, p("bobPending") to 0.0, s("Taxi") to 150.0, s("Cafe") to 33.33, s("Shack") to -600.0),
            history(bob),
        )
        assertEquals(listOf(p("raviLent") to -10.5, g("Lunch") to 20.0, g("Shack") to 300.0), history(ravi))
        assertEquals(emptyList<Pair<String, Double>>(), history(meera))
    }
}
