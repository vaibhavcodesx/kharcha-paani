package com.vaibhav.kharchapaani.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Test matrix for the Activity "involved" rule (#228).
 * Covers every combination of:
 *  - in a group or not
 *  - I paid vs someone else paid
 *  - I have a share (>0, ==0, guest covered by me, or absent)
 *  - I added it vs someone else added it
 *  - a settlement I'm in vs between two other people (in group and direct)
 *  - an IOU I'm in vs between two other people (in group and direct)
 *
 * Built from a real Room in-memory database to verify full entity mapping and query flows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityInvolvedMatrixTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var repository: LedgerRepository

    companion object {
        private const val ME = -1L
        private const val BOB = 2L
        private const val CAROL = 3L
        private const val GROUP_ID = 7L
        private const val PROFILE_BOB = "profile_bob_123"
    }

    private val group = GroupEntity(id = GROUP_ID, name = "Goa Trip", syncState = SyncState.SYNCED)
    private val bobPerson = PersonEntity(id = BOB, name = "Bob", avatarColor = "#2196F3", profileId = PROFILE_BOB)
    private val carolPerson = PersonEntity(id = CAROL, name = "Carol", avatarColor = "#4CAF50")

    @Before
    fun setUp() {
        runBlocking {
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = LedgerRepository(db)

            db.personDao().insertPerson(bobPerson)
            db.personDao().insertPerson(carolPerson)
            db.groupDao().insertGroup(group)
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertTx(
        id: Long,
        groupId: Long?,
        ownerProfileId: String?,
        isSplit: Boolean = false,
        type: String = "expense",
        amount: Double = 100.0,
        merchant: String = "Merchant $id",
    ) {
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = id,
                type = type,
                amount = amount,
                category = "Food",
                merchant = merchant,
                paymentMethod = "UPI",
                date = id * 1000L,
                isSplit = isSplit,
                createdAt = id * 1000L,
                groupId = groupId,
                ownerProfileId = ownerProfileId,
            )
        )
    }

    private suspend fun insertSplitWithShares(
        splitId: Long,
        transactionId: Long,
        paidBy: Long,
        totalAmount: Double = 100.0,
        shares: List<SplitShareEntity>,
    ) {
        db.splitDao().insertSplit(
            SplitEntity(
                id = splitId,
                transactionId = transactionId,
                totalAmount = totalAmount,
                paidByPersonId = paidBy,
            )
        )
        db.splitDao().insertSplitShares(shares)
    }

    private suspend fun insertSettlement(
        id: Long,
        fromPersonId: Long,
        toPersonId: Long,
        amount: Double = 50.0,
        isIou: Boolean = false,
        groupId: Long? = null,
        note: String = "Settlement $id",
    ) {
        db.settlementDao().insertSettlement(
            SettlementEntity(
                id = id,
                fromPersonId = fromPersonId,
                toPersonId = toPersonId,
                amount = amount,
                date = id * 1000L,
                isIou = isIou,
                groupId = groupId,
                note = note,
                createdAt = id * 1000L,
            )
        )
    }

    // --- Combinations Outside a Group (groupId == null) ---

    @Test
    fun `outside group - personal expense without split always involves me`() {
        runBlocking {
            insertTx(id = 1, groupId = null, ownerProfileId = null, isSplit = false)

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 1L }

            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_1"), feed.map { it.key })
        }
    }

    @Test
    fun `outside group - personal income without split always involves me`() {
        runBlocking {
            insertTx(id = 2, groupId = null, ownerProfileId = null, isSplit = false, type = "income")

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 2L }

            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_2"), feed.map { it.key })
        }
    }

    @Test
    fun `outside group - split where I paid always involves me`() {
        runBlocking {
            insertTx(id = 3, groupId = null, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(
                splitId = 3,
                transactionId = 3,
                paidBy = ME,
                shares = listOf(
                    SplitShareEntity(splitId = 3, personId = ME, shareAmount = 50.0),
                    SplitShareEntity(splitId = 3, personId = BOB, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 3L }

            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_3"), feed.map { it.key })
        }
    }

    @Test
    fun `outside group - split where other paid always involves me because outside group is mine by definition`() {
        runBlocking {
            insertTx(id = 4, groupId = null, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(
                splitId = 4,
                transactionId = 4,
                paidBy = BOB,
                shares = listOf(
                    SplitShareEntity(splitId = 4, personId = ME, shareAmount = 50.0),
                    SplitShareEntity(splitId = 4, personId = BOB, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 4L }

            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_4"), feed.map { it.key })
        }
    }

    // --- Combinations Inside a Group, I Added It (ownerProfileId == null) ---

    @Test
    fun `in group, I added, I paid, I have share - involves me`() {
        runBlocking {
            insertTx(id = 10, groupId = GROUP_ID, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(
                splitId = 10,
                transactionId = 10,
                paidBy = ME,
                shares = listOf(
                    SplitShareEntity(splitId = 10, personId = ME, shareAmount = 50.0),
                    SplitShareEntity(splitId = 10, personId = BOB, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 10L }

            assertTrue(tx.canEdit)
            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_10"), feed.map { it.key })
        }
    }

    @Test
    fun `in group, I added, I paid, I have zero share - involves me`() {
        runBlocking {
            insertTx(id = 11, groupId = GROUP_ID, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(
                splitId = 11,
                transactionId = 11,
                paidBy = ME,
                shares = listOf(
                    SplitShareEntity(splitId = 11, personId = ME, shareAmount = 0.0),
                    SplitShareEntity(splitId = 11, personId = BOB, shareAmount = 100.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 11L }

            assertTrue(tx.canEdit)
            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_11"), feed.map { it.key })
        }
    }

    @Test
    fun `in group, I added, Bob paid, I have no share - involves me because I added it`() {
        runBlocking {
            insertTx(id = 12, groupId = GROUP_ID, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(
                splitId = 12,
                transactionId = 12,
                paidBy = BOB,
                shares = listOf(
                    SplitShareEntity(splitId = 12, personId = BOB, shareAmount = 50.0),
                    SplitShareEntity(splitId = 12, personId = CAROL, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 12L }

            assertTrue(tx.canEdit)
            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_12"), feed.map { it.key })
        }
    }

    // --- Combinations Inside a Group, Someone Else Added It (ownerProfileId == PROFILE_BOB) ---

    @Test
    fun `in group, Bob added, I paid, I have no share - involves me because I paid`() {
        runBlocking {
            insertTx(id = 20, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(
                splitId = 20,
                transactionId = 20,
                paidBy = ME,
                shares = listOf(
                    SplitShareEntity(splitId = 20, personId = BOB, shareAmount = 50.0),
                    SplitShareEntity(splitId = 20, personId = CAROL, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 20L }

            assertTrue(tx.canEdit)
            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_20"), feed.map { it.key })
        }
    }

    @Test
    fun `in group, Bob added, Bob paid, I have positive own share - involves me`() {
        runBlocking {
            insertTx(id = 21, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(
                splitId = 21,
                transactionId = 21,
                paidBy = BOB,
                shares = listOf(
                    SplitShareEntity(splitId = 21, personId = BOB, shareAmount = 50.0),
                    SplitShareEntity(splitId = 21, personId = ME, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 21L }

            assertFalse(tx.canEdit)
            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_21"), feed.map { it.key })
        }
    }

    @Test
    fun `in group, Bob added, Bob paid, I have positive covered guest share - involves me`() {
        runBlocking {
            insertTx(id = 22, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(
                splitId = 22,
                transactionId = 22,
                paidBy = BOB,
                shares = listOf(
                    SplitShareEntity(splitId = 22, personId = BOB, shareAmount = 50.0),
                    SplitShareEntity(splitId = 22, personId = ME, shareAmount = 50.0, guestName = "My Guest"),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 22L }

            assertFalse(tx.canEdit)
            assertTrue(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertEquals(listOf("tx_22"), feed.map { it.key })
        }
    }

    @Test
    fun `in group, Bob added, Bob paid, I have zero share - DOES NOT involve me`() {
        runBlocking {
            insertTx(id = 23, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(
                splitId = 23,
                transactionId = 23,
                paidBy = BOB,
                shares = listOf(
                    SplitShareEntity(splitId = 23, personId = BOB, shareAmount = 100.0),
                    SplitShareEntity(splitId = 23, personId = ME, shareAmount = 0.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 23L }

            assertFalse(tx.canEdit)
            assertFalse(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertTrue("Zero-share expense where I didn't add or pay must be hidden from feed", feed.isEmpty())
        }
    }

    @Test
    fun `in group, Bob added, Bob paid, I am absent from shares - DOES NOT involve me`() {
        runBlocking {
            insertTx(id = 24, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(
                splitId = 24,
                transactionId = 24,
                paidBy = BOB,
                shares = listOf(
                    SplitShareEntity(splitId = 24, personId = BOB, shareAmount = 50.0),
                    SplitShareEntity(splitId = 24, personId = CAROL, shareAmount = 50.0),
                )
            )

            val fullTxs = repository.fullTransactions.first()
            val tx = fullTxs.single { it.transaction.id == 24L }

            assertFalse(tx.canEdit)
            assertFalse(tx.involvesMe)

            val feed = ActivityFeed.build(context, fullTxs, emptyList(), listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)
            assertTrue("Expense between others must be hidden from feed", feed.isEmpty())
        }
    }

    // --- Settlements & IOUs Matrix ---

    @Test
    fun `settlements - I paid to Bob (direct or group) involves me`() {
        runBlocking {
            insertSettlement(id = 30, fromPersonId = ME, toPersonId = BOB, groupId = null)
            insertSettlement(id = 31, fromPersonId = ME, toPersonId = BOB, groupId = GROUP_ID)

            val settlements = db.settlementDao().getAllSettlementsDirect()
            val feed = ActivityFeed.build(context, emptyList(), settlements, listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)

            assertEquals(listOf("settlement_31", "settlement_30"), feed.map { it.key })
        }
    }

    @Test
    fun `settlements - Bob paid to me (direct or group) involves me`() {
        runBlocking {
            insertSettlement(id = 32, fromPersonId = BOB, toPersonId = ME, groupId = null)
            insertSettlement(id = 33, fromPersonId = BOB, toPersonId = ME, groupId = GROUP_ID)

            val settlements = db.settlementDao().getAllSettlementsDirect()
            val feed = ActivityFeed.build(context, emptyList(), settlements, listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)

            assertEquals(listOf("settlement_33", "settlement_32"), feed.map { it.key })
        }
    }

    @Test
    fun `settlements - between Bob and Carol (direct or group) DOES NOT involve me`() {
        runBlocking {
            insertSettlement(id = 34, fromPersonId = BOB, toPersonId = CAROL, groupId = null)
            insertSettlement(id = 35, fromPersonId = BOB, toPersonId = CAROL, groupId = GROUP_ID)

            val settlements = db.settlementDao().getAllSettlementsDirect()
            val feed = ActivityFeed.build(context, emptyList(), settlements, listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)

            assertTrue("Settlements between two other people must not be in my Activity", feed.isEmpty())
        }
    }

    @Test
    fun `IOUs - I owe Bob or Bob owes me (direct or group) involves me`() {
        runBlocking {
            insertSettlement(id = 40, fromPersonId = BOB, toPersonId = ME, isIou = true, groupId = null)
            insertSettlement(id = 41, fromPersonId = ME, toPersonId = BOB, isIou = true, groupId = GROUP_ID)

            val settlements = db.settlementDao().getAllSettlementsDirect()
            val feed = ActivityFeed.build(context, emptyList(), settlements, listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)

            assertEquals(listOf("settlement_41", "settlement_40"), feed.map { it.key })
        }
    }

    @Test
    fun `IOUs - between Bob and Carol (direct or group) DOES NOT involve me`() {
        runBlocking {
            insertSettlement(id = 42, fromPersonId = BOB, toPersonId = CAROL, isIou = true, groupId = null)
            insertSettlement(id = 43, fromPersonId = CAROL, toPersonId = BOB, isIou = true, groupId = GROUP_ID)

            val settlements = db.settlementDao().getAllSettlementsDirect()
            val feed = ActivityFeed.build(context, emptyList(), settlements, listOf(bobPerson, carolPerson), listOf(group), "", ActivityFeed.ALL)

            assertTrue("IOUs between two other people must not be in my Activity", feed.isEmpty())
        }
    }

    // --- Full Real Room DB Feed Integration Matrix ---

    @Test
    fun `full combined Room DB feed includes all involved items and excludes all non-involved items`() {
        runBlocking {
            // Items that should be included:
            // tx_1: outside group personal
            insertTx(id = 1, groupId = null, ownerProfileId = null, isSplit = false)
            // tx_10: group, added by me, paid by me
            insertTx(id = 10, groupId = GROUP_ID, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(10, 10, paidBy = ME, shares = listOf(SplitShareEntity(splitId = 10, personId = ME, shareAmount = 50.0), SplitShareEntity(splitId = 10, personId = BOB, shareAmount = 50.0)))
            // tx_12: group, added by me, paid by Bob, no share for me
            insertTx(id = 12, groupId = GROUP_ID, ownerProfileId = null, isSplit = true)
            insertSplitWithShares(12, 12, paidBy = BOB, shares = listOf(SplitShareEntity(splitId = 12, personId = BOB, shareAmount = 100.0)))
            // tx_20: group, added by Bob, paid by me
            insertTx(id = 20, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(20, 20, paidBy = ME, shares = listOf(SplitShareEntity(splitId = 20, personId = BOB, shareAmount = 100.0)))
            // tx_21: group, added by Bob, paid by Bob, I have share > 0
            insertTx(id = 21, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(21, 21, paidBy = BOB, shares = listOf(SplitShareEntity(splitId = 21, personId = BOB, shareAmount = 50.0), SplitShareEntity(splitId = 21, personId = ME, shareAmount = 50.0)))
            // tx_22: group, added by Bob, paid by Bob, I cover a guest
            insertTx(id = 22, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true)
            insertSplitWithShares(22, 22, paidBy = BOB, shares = listOf(SplitShareEntity(splitId = 22, personId = BOB, shareAmount = 50.0), SplitShareEntity(splitId = 22, personId = ME, shareAmount = 50.0, guestName = "My Guest")))
            // settlement_30: I paid Bob
            insertSettlement(id = 30, fromPersonId = ME, toPersonId = BOB)
            // settlement_40: Bob lent me (IOU)
            insertSettlement(id = 40, fromPersonId = BOB, toPersonId = ME, isIou = true)

            // Items that should be excluded:
            // tx_23: group, added by Bob, paid by Bob, my share is 0
            insertTx(id = 23, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true, merchant = "ExcludedZeroShare")
            insertSplitWithShares(23, 23, paidBy = BOB, shares = listOf(SplitShareEntity(splitId = 23, personId = BOB, shareAmount = 100.0), SplitShareEntity(splitId = 23, personId = ME, shareAmount = 0.0)))
            // tx_24: group, added by Bob, paid by Bob, my share absent
            insertTx(id = 24, groupId = GROUP_ID, ownerProfileId = PROFILE_BOB, isSplit = true, merchant = "ExcludedAbsentShare")
            insertSplitWithShares(24, 24, paidBy = BOB, shares = listOf(SplitShareEntity(splitId = 24, personId = BOB, shareAmount = 50.0), SplitShareEntity(splitId = 24, personId = CAROL, shareAmount = 50.0)))
            // settlement_34: settlement between Bob and Carol
            insertSettlement(id = 34, fromPersonId = BOB, toPersonId = CAROL, note = "ExcludedSettlement")
            // settlement_42: IOU between Bob and Carol
            insertSettlement(id = 42, fromPersonId = BOB, toPersonId = CAROL, isIou = true, note = "ExcludedIou")

            val fullTxs = repository.fullTransactions.first()
            val settlements = db.settlementDao().getAllSettlementsDirect()
            val feed = ActivityFeed.build(
                context,
                fullTxs,
                settlements,
                listOf(bobPerson, carolPerson),
                listOf(group),
                query = "",
                filter = ActivityFeed.ALL,
            )

            val feedKeys = feed.map { it.key }.toSet()
            val expectedKeys = setOf("tx_1", "tx_10", "tx_12", "tx_20", "tx_21", "tx_22", "settlement_30", "settlement_40")
            val forbiddenKeys = setOf("tx_23", "tx_24", "settlement_34", "settlement_42")

            assertEquals(expectedKeys, feedKeys)
            for (forbidden in forbiddenKeys) {
                assertFalse("Feed must not contain $forbidden", feedKeys.contains(forbidden))
            }

            // Searching for terms on non-involved items must never bring them into the feed
            val searchedFeedZeroShare = ActivityFeed.build(context, fullTxs, settlements, listOf(bobPerson, carolPerson), listOf(group), "ExcludedZeroShare", ActivityFeed.ALL)
            assertTrue(searchedFeedZeroShare.isEmpty())

            val searchedFeedAbsentShare = ActivityFeed.build(context, fullTxs, settlements, listOf(bobPerson, carolPerson), listOf(group), "ExcludedAbsentShare", ActivityFeed.ALL)
            assertTrue(searchedFeedAbsentShare.isEmpty())

            val searchedFeedSettlement = ActivityFeed.build(context, fullTxs, settlements, listOf(bobPerson, carolPerson), listOf(group), "ExcludedSettlement", ActivityFeed.ALL)
            assertTrue(searchedFeedSettlement.isEmpty())

            val searchedFeedIou = ActivityFeed.build(context, fullTxs, settlements, listOf(bobPerson, carolPerson), listOf(group), "ExcludedIou", ActivityFeed.ALL)
            assertTrue(searchedFeedIou.isEmpty())
        }
    }
}
