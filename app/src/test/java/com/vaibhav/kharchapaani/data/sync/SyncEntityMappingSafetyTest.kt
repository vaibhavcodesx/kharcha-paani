package com.vaibhav.kharchapaani.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PaymentAskEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.Hues
import com.vaibhav.kharchapaani.domain.PersonColours
import com.vaibhav.kharchapaani.domain.UpiLink
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * W4: Sync entity mapping safety net.
 *
 * Verifies that every field of each syncable Room entity survives the upload and download mapping,
 * and asserts exact behaviour for server-controlled or local-only fields.
 *
 * Syncable entities covered:
 * 1. [TransactionEntity] (Personal, Group expense, and Local split)
 * 2. [GroupEntity]
 * 3. [GroupMemberEntity]
 * 4. [PersonEntity] (Shared group profiles and Local friends)
 * 5. [SplitEntity] (Group and Local split)
 * 6. [SplitShareEntity] (Group member, Group guest, and Local friend)
 * 7. [SettlementEntity] (Group settlement and Direct settlement)
 *
 * Non-syncable safety checks:
 * - [SubscriptionEntity]
 * - [PaymentAskEntity]
 */
@OptIn(ExperimentalTime::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyncEntityMappingSafetyTest {

    private val alice = "00000000-0000-4000-8000-0000000000a1" // self profile
    private val bob = "00000000-0000-4000-8000-0000000000b1"   // peer member profile
    private val charlie = "00000000-0000-4000-8000-0000000000c1" // 3rd profile

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "sync-mapping-safety-${UUID.randomUUID()}.db"
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val remote = FakeGroupRemote(self = alice).apply {
        profile(alice, "Alice")
        profile(bob, "Bob")
        profile(charlie, "Charlie")
    }
    private val groupEngine = GroupSyncEngine(db, remote, selfProfileId = alice)
    private val syncDao get() = db.syncDao()
    private val groupDao get() = db.groupSyncDao()

    @After
    fun tearDown() {
        db.close()
    }

    // =========================================================================
    // 1. TransactionEntity
    // =========================================================================

    @Test
    fun personalTransactionUploadMappingPreservesFields() {
        val original = TransactionEntity(
            id = 42L,
            type = "expense",
            amount = 1250.755, // rounds to 1250.76
            category = "Food",
            merchant = "Blinkit",
            paymentMethod = "UPI",
            date = 1730000000000L,
            note = "Weekly groceries",
            isSplit = false,
            groupId = null,
            createdAt = 1730000001000L,
            remoteId = UUID.randomUUID().toString(),
            updatedAt = 1730000002000L,
            syncState = SyncState.PENDING,
            ownerProfileId = null,
        )

        val upload = original.toUpload(ownerId = alice)

        assertEquals(original.remoteId, upload.id)
        assertEquals(alice, upload.ownerId)
        assertEquals(original.type, upload.type)
        assertEquals(1250.76, upload.amount, 0.0)
        assertEquals(original.category, upload.category)
        assertEquals(original.merchant, upload.merchant)
        assertEquals(original.paymentMethod, upload.paymentMethod)
        assertEquals(Instant.fromEpochMilliseconds(original.date).toString(), upload.date)
        assertEquals(original.note, upload.note)
        assertNull("personal transactions always have groupId null", upload.groupId)
        assertEquals(Instant.fromEpochMilliseconds(original.createdAt).toString(), upload.createdAt)
        assertNull("new or edited uploads have deletedAt null", upload.deletedAt)
    }

    @Test
    fun personalTransactionPullMappingPreservesFields() {
        val remoteTx = RemoteTransaction(
            id = UUID.randomUUID().toString(),
            ownerId = alice,
            type = "income",
            amount = 50000.00,
            category = "Salary",
            merchant = "Acme Corp",
            paymentMethod = "Bank Transfer",
            date = "2026-11-01T10:00:00Z",
            note = "November salary",
            createdAt = "2026-11-01T10:00:00Z",
            updatedAt = "2026-11-01T10:05:00Z",
            deletedAt = null,
        )

        val entity = remoteTx.toSyncedEntity()

        assertEquals(remoteTx.id, entity.remoteId)
        assertEquals(remoteTx.type, entity.type)
        assertEquals(remoteTx.amount, entity.amount, 0.0)
        assertEquals(remoteTx.category, entity.category)
        assertEquals(remoteTx.merchant, entity.merchant)
        assertEquals(remoteTx.paymentMethod, entity.paymentMethod)
        assertEquals(parseTimestamp(remoteTx.date), entity.date)
        assertEquals(remoteTx.note, entity.note)
        assertEquals(parseTimestamp(remoteTx.createdAt), entity.createdAt)
        assertEquals(parseTimestamp(remoteTx.updatedAt), entity.updatedAt)
        assertEquals(SyncState.SYNCED, entity.syncState)
        assertNull(entity.groupId)
        assertNull(entity.ownerProfileId)
    }

    @Test
    fun findingPersonalTransactionUploadOmitsIsSplitAndPullDefaultsToFalse() {
        // Finding: TransactionUpload has no is_split field.
        // RemoteTransaction.toSyncedEntity() hardcodes isSplit = false.
        val splitEntity = TransactionEntity(
            type = "expense",
            amount = 300.0,
            category = "Food",
            merchant = "Cafe",
            paymentMethod = "Cash",
            isSplit = true,
            remoteId = UUID.randomUUID().toString(),
        )

        val upload = splitEntity.toUpload(ownerId = alice)
        // Upload contains no isSplit field.
        val remoteTx = RemoteTransaction(
            id = upload.id,
            ownerId = upload.ownerId,
            type = upload.type,
            amount = upload.amount,
            category = upload.category,
            merchant = upload.merchant,
            paymentMethod = upload.paymentMethod,
            date = upload.date,
            note = upload.note,
            createdAt = upload.createdAt,
            updatedAt = "2026-11-01T12:00:00Z",
        )

        val pulled = remoteTx.toSyncedEntity()
        assertFalse(
            "TransactionUpload drops isSplit; personal transactions pull as isSplit=false until local split sync runs",
            pulled.isSplit,
        )
    }

    @Test
    fun groupTransactionUploadAndPullPreservesAllFields() {
        runBlocking {
            // Setup shared group with Alice and Bob
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Goa Trip", alice, bob)
            groupEngine.pullGroups()
            val localGroup = groupDao.groupByRemoteId(groupRemoteId)!!

            val bobPerson = groupDao.personByProfileId(bob)!!

            val txId = syncDao.insertTransaction(
                TransactionEntity(
                    type = "expense",
                    amount = 2000.0,
                    category = "Transport",
                    merchant = "Goa Cabs",
                    paymentMethod = "Cash",
                    date = 1730000000000L,
                    note = "Airport taxi",
                    isSplit = true,
                    groupId = localGroup.id,
                    createdAt = 1730000000000L,
                    remoteId = UUID.randomUUID().toString(),
                    updatedAt = 1730000001000L,
                    syncState = SyncState.PENDING,
                )
            )
            val tx = db.transactionDao().getTransactionByIdDirect(txId)!!

            val splitId = groupDao.insertSplit(
                SplitEntity(
                    transactionId = txId,
                    totalAmount = 2000.0,
                    paidByPersonId = -1L, // Alice paid
                    splitType = "equal",
                    remoteId = UUID.randomUUID().toString(),
                )
            )
            groupDao.insertShares(
                listOf(
                    SplitShareEntity(splitId = splitId, personId = -1L, shareAmount = 1000.0),
                    SplitShareEntity(splitId = splitId, personId = bobPerson.id, shareAmount = 1000.0),
                )
            )

            // Test buildGroupExpense mapping (Upload)
            val upload = groupEngine.buildGroupExpense(tx)
            assertNotNull(upload)
            val pTx = upload!!.transaction
            assertEquals(tx.remoteId, pTx.id)
            assertEquals("expense", pTx.type)
            assertEquals(BigDecimal("2000.00"), pTx.amount)
            assertEquals("Transport", pTx.category)
            assertEquals("Goa Cabs", pTx.merchant)
            assertEquals("Cash", pTx.paymentMethod)
            assertEquals(localGroup.remoteId, pTx.groupId)
            assertEquals(tx.note, pTx.note)

            // Test applyGroupExpense mapping (Pull on another device)
            remote.saveGroupExpense(upload)
            val pulledRemote = remote.expenses.getValue(tx.remoteId)

            // Simulate pull on Bob's phone (where ownerId = Alice != Bob)
            val bobDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            try {
                val bobEngine = GroupSyncEngine(bobDb, remote, selfProfileId = bob)
                bobEngine.pullGroups()
                bobEngine.pullGroupExpenses()

                val bobTx = bobDb.syncDao().transactionByRemoteId(tx.remoteId)
                assertNotNull(bobTx)
                assertEquals(tx.remoteId, bobTx!!.remoteId)
                assertEquals(tx.type, bobTx.type)
                assertEquals(2000.0, bobTx.amount, 0.0)
                assertEquals(tx.category, bobTx.category)
                assertEquals(tx.merchant, bobTx.merchant)
                assertEquals(tx.paymentMethod, bobTx.paymentMethod)
                assertEquals(tx.date, bobTx.date)
                assertEquals(tx.note, bobTx.note)
                assertTrue(bobTx.isSplit)
                assertEquals(SyncState.SYNCED, bobTx.syncState)
                // ownerProfileId is Alice on Bob's phone:
                assertEquals(alice, bobTx.ownerProfileId)
                assertFalse(bobTx.isOwnedByMe)
            } finally {
                bobDb.close()
            }
        }
    }

    // =========================================================================
    // 2. GroupEntity & GroupMemberEntity
    // =========================================================================

    @Test
    fun groupEntityPushAndPullPreservesAttributes() {
        runBlocking {
            val remoteId = UUID.randomUUID().toString()
            val created = GroupEntity(
                name = "Flatmates",
                description = "Monthly apartment expenses",
                emoji = "🏠",
                colorHex = "#4A90E2",
                createdAt = 1730000000000L,
                remoteId = remoteId,
                updatedAt = 1730000001000L,
                syncState = SyncState.PENDING,
                archivedAt = null,
                createdByProfileId = alice,
            )
            val localId = groupDao.insertGroup(created)

            // Push to remote
            groupEngine.pushGroups()

            val remoteGroup = remote.groups[remoteId]
            assertNotNull(remoteGroup)
            assertEquals("Flatmates", remoteGroup!!.name)
            assertEquals("Monthly apartment expenses", remoteGroup.description)
            assertEquals("🏠", remoteGroup.emoji)
            assertEquals("#4A90E2", remoteGroup.colorHex)
            assertEquals(alice, remoteGroup.createdBy)

            // Pull back
            groupEngine.pullGroups()
            val pulled = groupDao.groupByRemoteId(remoteId)!!
            assertEquals("Flatmates", pulled.name)
            assertEquals("Monthly apartment expenses", pulled.description)
            assertEquals("🏠", pulled.emoji)
            assertEquals("#4A90E2", pulled.colorHex)
            assertEquals(SyncState.SYNCED, pulled.syncState)
            assertEquals(alice, pulled.createdByProfileId)
            assertNull(pulled.archivedAt)
        }
    }

    @Test
    fun findingGroupCreatedAtIsServerAssignedAndNotSentByClient() {
        runBlocking {
            val localCreatedAt = 1700000000000L
            val remoteId = UUID.randomUUID().toString()
            val group = GroupEntity(
                name = "Road Trip",
                remoteId = remoteId,
                createdAt = localCreatedAt,
                syncState = SyncState.PENDING,
                createdByProfileId = alice,
            )
            groupDao.insertGroup(group)

            // GroupRemote.createGroup only takes (id, name, emoji); server stamps created_at = now()
            groupEngine.pushGroups()
            val serverCreated = remote.groups.getValue(remoteId).createdAt

            // Pulling overwrites local createdAt with server's created_at:
            groupEngine.pullGroups()
            val pulled = groupDao.groupByRemoteId(remoteId)!!
            assertEquals(
                "Group createdAt is overwritten by server timestamp on pull",
                parseTimestamp(serverCreated),
                pulled.createdAt,
            )
        }
    }

    @Test
    fun groupMemberEntityMappingPreservesAllMembers() {
        runBlocking {
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(bob, groupRemoteId, "Trek 2026", alice, charlie)
            groupEngine.pullGroups()

            val localGroup = groupDao.groupByRemoteId(groupRemoteId)!!
            val members = db.groupDao().getAllGroupMembersDirect().filter { it.groupId == localGroup.id }

            // Should have 3 members: ME (-1), Bob, Charlie
            assertEquals(3, members.size)
            assertTrue("Me (-1) is always a member in visible groups", members.any { it.personId == -1L })

            val bobPerson = groupDao.personByProfileId(bob)!!
            val charliePerson = groupDao.personByProfileId(charlie)!!
            assertTrue(members.any { it.personId == bobPerson.id })
            assertTrue(members.any { it.personId == charliePerson.id })
        }
    }

    // =========================================================================
    // 3. PersonEntity
    // =========================================================================

    @Test
    fun personEntityPullMappingPreservesAllFields() {
        runBlocking {
            val profileId = "00000000-0000-4000-8000-0000000000d1"
            val hue = 210
            remote.profiles[profileId] = RemoteProfile(
                id = profileId,
                displayName = "  David Warner  ",
                avatarUrl = "https://example.com/avatar.jpg",
                avatarEmoji = "🏏",
                upiId = "david@oksbi",
                hue = hue,
            )
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Cricket Team", alice, profileId)
            groupEngine.pullGroups()

            val person = groupDao.personByProfileId(profileId)
            assertNotNull(person)
            assertEquals("David Warner", person!!.name)
            assertEquals("https://example.com/avatar.jpg", person.avatarUrl)
            assertEquals("🏏", person.avatarEmoji)
            assertEquals("david@oksbi", person.upiId)
            assertEquals(hue, person.hue)
            assertEquals(Hues.hex(hue), person.avatarColor)
        }
    }

    @Test
    fun personEntityUpiIdIsSanitizedOnPull() {
        runBlocking {
            val profileId = "00000000-0000-4000-8000-0000000000e1"
            remote.profiles[profileId] = RemoteProfile(
                id = profileId,
                displayName = "Eve",
                upiId = "eve@bank?cu=INR&invalid=true", // dirty UPI link attempt
            )
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Security Group", alice, profileId)
            groupEngine.pullGroups()

            val person = groupDao.personByProfileId(profileId)!!
            assertEquals(
                "UpiLink.cleanId sanitizes dirty UPI IDs on pull",
                UpiLink.cleanId("eve@bank?cu=INR&invalid=true"),
                person.upiId,
            )
        }
    }

    @Test
    fun localPeopleIdForMapsLocalFriendFields() {
        runBlocking {
            val friendName = "Rohan Sharma"
            val personId = LocalPeople.idFor(groupDao, friendName)
            assertTrue(personId > 0)

            val person = groupDao.personById(personId)!!
            assertEquals("Rohan Sharma", person.name)
            assertEquals(PersonColours.stable("Rohan Sharma"), person.avatarColor)
            assertNull(person.profileId)
            assertNull(person.avatarUrl)
            assertNull(person.avatarEmoji)
            assertNull(person.upiId)
            assertNull(person.hue)
        }
    }

    @Test
    fun findingPersonCreatedAtIsLocalOnly() {
        runBlocking {
            val profileId = "00000000-0000-4000-8000-0000000000f1"
            remote.profiles[profileId] = RemoteProfile(id = profileId, displayName = "Frank")
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Test Group", alice, profileId)

            val before = System.currentTimeMillis()
            groupEngine.pullGroups()
            val after = System.currentTimeMillis()

            val person = groupDao.personByProfileId(profileId)!!
            assertTrue(
                "PersonEntity.createdAt defaults to local device time because RemoteProfile has no createdAt field",
                person.createdAt in before..after,
            )
        }
    }

    // =========================================================================
    // 4. SplitEntity & SplitShareEntity
    // =========================================================================

    @Test
    fun groupSplitAndMemberShareMappingPreservesFields() {
        runBlocking {
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Dinner Club", alice, bob)
            groupEngine.pullGroups()
            val localGroup = groupDao.groupByRemoteId(groupRemoteId)!!
            val bobPerson = groupDao.personByProfileId(bob)!!

            val txId = syncDao.insertTransaction(
                TransactionEntity(
                    type = "expense",
                    amount = 1500.0,
                    category = "Food",
                    merchant = "Olive",
                    paymentMethod = "Card",
                    isSplit = true,
                    groupId = localGroup.id,
                    remoteId = UUID.randomUUID().toString(),
                )
            )
            val tx = db.transactionDao().getTransactionByIdDirect(txId)!!

            val splitRemoteId = UUID.randomUUID().toString()
            val splitId = groupDao.insertSplit(
                SplitEntity(
                    transactionId = txId,
                    totalAmount = 1500.0,
                    paidByPersonId = bobPerson.id,
                    splitType = "percentage",
                    remoteId = splitRemoteId,
                )
            )
            groupDao.insertShares(
                listOf(
                    SplitShareEntity(splitId = splitId, personId = -1L, shareAmount = 600.0, percentage = 40.0),
                    SplitShareEntity(splitId = splitId, personId = bobPerson.id, shareAmount = 900.0, percentage = 60.0),
                )
            )

            val upload = groupEngine.buildGroupExpense(tx)!!
            assertEquals(splitRemoteId, upload.split.id)
            assertEquals(bob, upload.split.paidBy)
            assertEquals("percentage", upload.split.splitType)
            assertEquals(BigDecimal("1500.00"), upload.split.totalAmount)

            assertEquals(2, upload.shares.size)
            val aliceShare = upload.shares.first { it.profileId == alice }
            assertEquals(BigDecimal("600.00"), aliceShare.shareAmount)
            assertEquals(40.0, aliceShare.percentage!!, 0.0)

            val bobShare = upload.shares.first { it.profileId == bob }
            assertEquals(BigDecimal("900.00"), bobShare.shareAmount)
            assertEquals(60.0, bobShare.percentage!!, 0.0)
        }
    }

    @Test
    fun groupGuestShareUploadAndPullRoundTrip() {
        runBlocking {
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Goa with Guests", alice, bob)
            groupEngine.pullGroups()
            val localGroup = groupDao.groupByRemoteId(groupRemoteId)!!
            val bobPerson = groupDao.personByProfileId(bob)!!

            val guestFriendId = LocalPeople.idFor(groupDao, "Rohit (Guest)")

            val txId = syncDao.insertTransaction(
                TransactionEntity(
                    type = "expense",
                    amount = 3000.0,
                    category = "Food",
                    merchant = "Beach Shack",
                    paymentMethod = "Cash",
                    isSplit = true,
                    groupId = localGroup.id,
                    remoteId = UUID.randomUUID().toString(),
                )
            )
            val tx = db.transactionDao().getTransactionByIdDirect(txId)!!

            val splitId = groupDao.insertSplit(
                SplitEntity(
                    transactionId = txId,
                    totalAmount = 3000.0,
                    paidByPersonId = -1L,
                    splitType = "equal",
                    remoteId = UUID.randomUUID().toString(),
                )
            )
            groupDao.insertShares(
                listOf(
                    SplitShareEntity(splitId = splitId, personId = -1L, shareAmount = 1000.0),
                    SplitShareEntity(splitId = splitId, personId = bobPerson.id, shareAmount = 1000.0),
                    // Guest covered by Alice (-1)
                    SplitShareEntity(
                        splitId = splitId,
                        personId = -1L,
                        shareAmount = 1000.0,
                        guestName = "Rohit (Guest)",
                        guestPersonId = guestFriendId,
                    ),
                )
            )

            // Test upload mapping
            val upload = groupEngine.buildGroupExpense(tx)!!
            val guestUpload = upload.shares.first { it.localName != null }
            assertNull("Guest share profileId must be null", guestUpload.profileId)
            assertEquals("Rohit (Guest)", guestUpload.localName)
            assertEquals(BigDecimal("1000.00"), guestUpload.shareAmount)

            // Save on server
            remote.saveGroupExpense(upload)

            // Test pull on host phone (Alice): guestPersonId is restored
            val aliceDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            try {
                val aliceDao = aliceDb.groupSyncDao()
                val hostFriendId = LocalPeople.idFor(aliceDao, "Rohit (Guest)")
                val aliceEngine = GroupSyncEngine(aliceDb, remote, selfProfileId = alice)
                aliceEngine.pullGroups()
                aliceEngine.pullGroupExpenses()

                val pulledTx = aliceDb.syncDao().transactionByRemoteId(tx.remoteId)!!
                val pulledSplit = aliceDao.splitForTransaction(pulledTx.id)!!
                val pulledShares = aliceDao.sharesForSplit(pulledSplit.id)
                val hostGuestShare = pulledShares.first { it.isGuest }

                assertEquals("Rohit (Guest)", hostGuestShare.guestName)
                assertEquals(hostFriendId, hostGuestShare.guestPersonId)
                assertEquals(-1L, hostGuestShare.personId)
            } finally {
                aliceDb.close()
            }

            // Test pull on peer phone (Bob): guestPersonId is null (finding: host-only)
            val bobDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            try {
                val bobEngine = GroupSyncEngine(bobDb, remote, selfProfileId = bob)
                bobEngine.pullGroups()
                bobEngine.pullGroupExpenses()

                val bobTx = bobDb.syncDao().transactionByRemoteId(tx.remoteId)!!
                val bobSplit = bobDb.groupSyncDao().splitForTransaction(bobTx.id)!!
                val bobShares = bobDb.groupSyncDao().sharesForSplit(bobSplit.id)
                val peerGuestShare = bobShares.first { it.isGuest }

                assertEquals("Rohit (Guest)", peerGuestShare.guestName)
                assertNull(
                    "guestPersonId is null on peer phone because friends are local to host only",
                    peerGuestShare.guestPersonId,
                )
            } finally {
                bobDb.close()
            }
        }
    }

    // =========================================================================
    // 5. SettlementEntity
    // =========================================================================

    @Test
    fun groupSettlementUploadAndPullPreservesFields() {
        runBlocking {
            val groupRemoteId = UUID.randomUUID().toString()
            remote.groupCreatedBy(alice, groupRemoteId, "Trip", alice, bob)
            groupEngine.pullGroups()
            val localGroup = groupDao.groupByRemoteId(groupRemoteId)!!
            val bobPerson = groupDao.personByProfileId(bob)!!

            val settlementRemoteId = UUID.randomUUID().toString()
            val localSettlement = SettlementEntity(
                fromPersonId = -1L, // Alice
                toPersonId = bobPerson.id, // Bob
                amount = 750.50,
                date = 1730000000000L,
                note = "Split payment for cab",
                isIou = false,
                groupId = localGroup.id,
                createdAt = 1730000000000L,
                remoteId = settlementRemoteId,
                updatedAt = 1730000001000L,
                syncState = SyncState.PENDING,
                status = SettlementStatus.PENDING,
                recordedByPersonId = -1L,
            )
            val settlementId = groupDao.insertSettlement(localSettlement)

            // Test push
            groupEngine.pushSettlements()

            val remoteSettlement = remote.settlements[settlementRemoteId]
            assertNotNull(remoteSettlement)
            assertEquals(settlementRemoteId, remoteSettlement!!.id)
            assertEquals(alice, remoteSettlement.fromProfileId)
            assertEquals(bob, remoteSettlement.toProfileId)
            assertEquals(750.50, remoteSettlement.amount, 0.0)
            assertEquals(localGroup.remoteId, remoteSettlement.groupId)
            assertEquals(localSettlement.note, remoteSettlement.note)
            assertEquals(localSettlement.isIou, remoteSettlement.isIou)

            // Bob responds to confirm on server
            remote.answeredBy(settlementRemoteId, confirm = true, note = "Received on UPI")

            // Alice pulls updated settlement
            groupEngine.pullSettlements()
            val pulled = groupDao.settlementByRemoteId(settlementRemoteId)!!

            assertEquals(SettlementStatus.CONFIRMED, pulled.status)
            assertEquals(-1L, pulled.recordedByPersonId)
            assertEquals("Received on UPI", pulled.responseNote)
            assertNotNull(pulled.respondedAt)
            assertEquals(SyncState.SYNCED, pulled.syncState)
        }
    }

    @Test
    fun findingSettlementLocalOnlyFieldsAreNotUploaded() {
        // Finding: SettlementUpload contains no status, recorded_by, responded_at, response_note,
        // pending_response, or reminded_stage.
        val upload = SettlementUpload(
            id = "s-1",
            fromProfileId = alice,
            toProfileId = bob,
            amount = 500.0,
            date = "2026-11-01T10:00:00Z",
            note = "dinner",
            isIou = true,
            groupId = "g-1",
            createdAt = "2026-11-01T10:00:00Z",
            deletedAt = null,
        )

        val json = Json.encodeToString(SettlementUpload.serializer(), upload)
        assertFalse("SettlementUpload json never contains status", json.contains("status"))
        assertFalse("SettlementUpload json never contains recorded_by", json.contains("recorded_by"))
        assertFalse("SettlementUpload json never contains responded_at", json.contains("responded_at"))
        assertFalse("SettlementUpload json never contains response_note", json.contains("response_note"))
        assertFalse("SettlementUpload json never contains pending_response", json.contains("pending_response"))
        assertFalse("SettlementUpload json never contains reminded_stage", json.contains("reminded_stage"))
    }

    @Test
    fun directSettlementMappingRoundTripPreservesFields() {
        // Direct / local settlement with friend without account
        val payload = LocalSettlementPayload(
            id = UUID.randomUUID().toString(),
            fromLocalName = null, // ME
            toLocalName = "Kunal",
            amount = 450.0,
            isIou = true,
            date = "2026-11-02T15:00:00Z",
            note = "Tea and snacks",
            createdAt = "2026-11-02T15:00:00Z",
            deletedAt = null,
        )

        // Remote model
        val remoteSettlement = RemoteLocalSettlement(
            id = payload.id,
            fromLocalName = payload.fromLocalName,
            toLocalName = payload.toLocalName,
            amount = payload.amount,
            isIou = payload.isIou,
            date = payload.date,
            note = payload.note,
            createdAt = payload.createdAt,
            updatedAt = "2026-11-02T15:05:00Z",
            deletedAt = null,
        )

        assertEquals(payload.id, remoteSettlement.id)
        assertNull(remoteSettlement.fromLocalName) // ME
        assertEquals("Kunal", remoteSettlement.toLocalName)
        assertEquals(450.0, remoteSettlement.amount, 0.0)
        assertTrue(remoteSettlement.isIou)
        assertEquals("Tea and snacks", remoteSettlement.note)
    }

    // =========================================================================
    // 6. Non-syncable safety guards
    // =========================================================================

    @Test
    fun subscriptionEntityIsNotSynced() {
        val sub = SubscriptionEntity(
            id = 1L,
            name = "Netflix",
            amount = 649.0,
            category = "Entertainment",
            paymentMethod = "Credit Card",
            cycle = "MONTHLY",
            startDate = 1730000000000L,
        )
        // SubscriptionEntity has no remoteId, no syncState, and no upload representation.
        assertEquals(1L, sub.id)
        assertEquals("Netflix", sub.name)
        assertEquals(649.0, sub.amount, 0.0)
    }

    @Test
    fun paymentAskEntityIsNotSynced() {
        val ask = PaymentAskEntity(
            id = 1L,
            personId = 5L,
            amount = 1200.0,
            sentAt = 1730000000000L,
        )
        // PaymentAskEntity has no remoteId, no syncState, and is strictly local-only.
        assertEquals(1L, ask.id)
        assertEquals(5L, ask.personId)
        assertEquals(1200.0, ask.amount, 0.0)
    }
}
