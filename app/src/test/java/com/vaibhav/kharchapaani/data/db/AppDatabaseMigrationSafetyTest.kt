package com.vaibhav.kharchapaani.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.vaibhav.kharchapaani.data.db.entity.PaymentAskEntity
import com.vaibhav.kharchapaani.data.db.entity.ReceiptEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppDatabaseMigrationSafetyTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-safety-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `existing v2 rows survive opening with the current app version`() {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO people (id, name, avatarColor, createdAt) VALUES (7, 'Existing friend', '#FFFFFF', 0)")
        }

        val database = AppDatabase.builder(context, dbName).build()
        val people = runBlocking { database.personDao().getAllPeopleDirect() }
        database.close()

        assertEquals(listOf("Existing friend"), people.map { it.name })
    }

    @Test
    fun `a database with no migration path fails to open instead of being wiped`() {
        // Simulates an on-disk version the app has no Migration for.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null).use { db ->
            db.execSQL("CREATE TABLE people (id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
            db.execSQL("INSERT INTO people (id, name) VALUES (1, 'Irreplaceable ledger row')")
            db.version = 1
        }

        val database = AppDatabase.builder(context, dbName).build()
        assertThrows(IllegalStateException::class.java) { database.openHelper.writableDatabase }
        database.close()

        SQLiteDatabase.openDatabase(context.getDatabasePath(dbName).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT name FROM people", null).use { cursor ->
                assertEquals(1, cursor.count)
                cursor.moveToFirst()
                assertEquals("Irreplaceable ledger row", cursor.getString(0))
            }
        }
    }

    @Test
    fun `migration chain from v2 to v10 keeps every entity row and sets expected defaults`() {
        val uuidV4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

        // 1. Create database at v2 and seed rows across all 7 entities supported in v2
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO people (id, name, avatarColor, createdAt) VALUES (1, 'Rohan', '#4A90E2', 1000), (2, 'Priya', '#50E3C2', 1001)")
            db.execSQL(
                "INSERT INTO groups (id, name, description, emoji, colorHex, createdAt) " +
                    "VALUES (1, 'Goa Trip', 'Beach vacation', '🏖️', '#FF7A3D', 1100)"
            )
            db.execSQL("INSERT INTO group_members (id, groupId, personId) VALUES (1, 1, -1), (2, 1, 1)")
            db.execSQL(
                "INSERT INTO transactions (id, type, amount, category, merchant, paymentMethod, date, note, isSplit, groupId, createdAt) " +
                    "VALUES (1, 'expense', 450.0, 'Food', 'Cafe Shine', 'Cash', 2000, 'Breakfast', 0, 1, 2000), " +
                    "(2, 'expense', 1200.0, 'Transport', 'Cab Rental', 'UPI', 3000, 'Airport drop', 1, 1, 3000)"
            )
            db.execSQL("INSERT INTO splits (id, transactionId, totalAmount, paidByPersonId, splitType) VALUES (1, 2, 1200.0, -1, 'equal')")
            db.execSQL(
                "INSERT INTO split_shares (id, splitId, personId, shareAmount, percentage) " +
                    "VALUES (1, 1, -1, 600.0, NULL), (2, 1, 1, 600.0, NULL)"
            )
            db.execSQL(
                "INSERT INTO settlements (id, fromPersonId, toPersonId, amount, date, note, isIou, groupId, createdAt) " +
                    "VALUES (1, 1, -1, 250.0, 4000, 'Borrowed for fuel', 1, 1, 4000), " +
                    "(2, -1, 1, 100.0, 5000, 'Settled part', 0, 1, 5000)"
            )
        }

        // 2. Run all migrations from v2 through v10 and validate schema against 10.json
        helper.runMigrationsAndValidate(dbName, 10, true, *AppDatabase.ALL_MIGRATIONS).close()

        // 3. Verify seeded legacy rows and migrated defaults, then smoke-test new tables
        val database = AppDatabase.builder(context, dbName).build()
        try {
            runBlocking {
                // Verify People
                val people = database.personDao().getAllPeopleDirect().sortedBy { it.id }
                assertEquals(2, people.size)
                val rohan = people[0]
                assertEquals(1L, rohan.id)
                assertEquals("Rohan", rohan.name)
                assertEquals("#4A90E2", rohan.avatarColor)
                assertEquals(1000L, rohan.createdAt)
                assertNull(rohan.profileId)
                assertNull(rohan.avatarUrl)
                assertNull(rohan.avatarEmoji)
                assertNull(rohan.upiId)
                assertNull(rohan.hue)

                val priya = people[1]
                assertEquals(2L, priya.id)
                assertEquals("Priya", priya.name)
                assertEquals("#50E3C2", priya.avatarColor)
                assertEquals(1001L, priya.createdAt)
                assertNull(priya.profileId)
                assertNull(priya.avatarUrl)
                assertNull(priya.avatarEmoji)
                assertNull(priya.upiId)
                assertNull(priya.hue)

                // Verify Groups
                val groups = database.groupDao().getAllGroupsDirect()
                assertEquals(1, groups.size)
                val group = groups[0]
                assertEquals(1L, group.id)
                assertEquals("Goa Trip", group.name)
                assertEquals("Beach vacation", group.description)
                assertEquals("🏖️", group.emoji)
                assertEquals("#FF7A3D", group.colorHex)
                assertEquals(1100L, group.createdAt)
                assertEquals(1100L, group.updatedAt)
                assertEquals(SyncState.LOCAL_ONLY, group.syncState)
                assertNull(group.archivedAt)
                assertNull(group.createdByProfileId)
                assertTrue("Group remoteId must be valid UUID v4", uuidV4.matches(group.remoteId))

                // Verify Group Members
                val members = database.groupDao().getMembersForGroupDirect(1)
                assertEquals(2, members.size)
                assertEquals(setOf(-1L, 1L), members.map { it.personId }.toSet())
                assertEquals(setOf(1L to -1L, 2L to 1L), members.map { it.id to it.personId }.toSet())
                assertTrue(members.all { it.groupId == 1L })

                // Verify Transactions
                val transactions = database.transactionDao().getAllTransactionsDirect().sortedBy { it.id }
                assertEquals(2, transactions.size)

                val directTx = transactions[0]
                assertEquals(1L, directTx.id)
                assertEquals("expense", directTx.type)
                assertEquals(450.0, directTx.amount, 0.0)
                assertEquals("Food", directTx.category)
                assertEquals("Cafe Shine", directTx.merchant)
                assertEquals("Cash", directTx.paymentMethod)
                assertEquals(2000L, directTx.date)
                assertEquals("Breakfast", directTx.note)
                assertEquals(false, directTx.isSplit)
                assertEquals(1L, directTx.groupId)
                assertEquals(2000L, directTx.createdAt)
                assertEquals(2000L, directTx.updatedAt)
                assertEquals(SyncState.LOCAL_ONLY, directTx.syncState)
                assertNull(directTx.ownerProfileId)
                assertTrue("Direct tx remoteId must be valid UUID v4", uuidV4.matches(directTx.remoteId))

                val splitTx = transactions[1]
                assertEquals(2L, splitTx.id)
                assertEquals("expense", splitTx.type)
                assertEquals(1200.0, splitTx.amount, 0.0)
                assertEquals("Transport", splitTx.category)
                assertEquals("UPI", splitTx.paymentMethod)
                assertEquals(3000L, splitTx.date)
                assertEquals("Airport drop", splitTx.note)
                assertNull(splitTx.ownerProfileId)
                assertEquals("Cab Rental", splitTx.merchant)
                assertEquals(true, splitTx.isSplit)
                assertEquals(1L, splitTx.groupId)
                assertEquals(3000L, splitTx.createdAt)
                assertEquals(3000L, splitTx.updatedAt)
                assertEquals(SyncState.LOCAL_ONLY, splitTx.syncState)
                assertTrue("Split tx remoteId must be valid UUID v4", uuidV4.matches(splitTx.remoteId))

                // Verify Splits & Shares
                val splits = database.splitDao().getAllSplitsDirect()
                assertEquals(1, splits.size)
                val split = splits[0]
                assertEquals(1L, split.id)
                assertEquals(2L, split.transactionId)
                assertEquals(1200.0, split.totalAmount, 0.0)
                assertEquals(-1L, split.paidByPersonId)
                assertEquals("equal", split.splitType)
                assertTrue("Split remoteId must be valid UUID v4", uuidV4.matches(split.remoteId))

                val shares = database.splitDao().getSharesForSplitDirect(split.id).sortedBy { it.id }
                assertEquals(2, shares.size)
                assertEquals(1L, shares[0].id)
                assertEquals(split.id, shares[0].splitId)
                assertEquals(-1L, shares[0].personId)
                assertEquals(600.0, shares[0].shareAmount, 0.0)
                assertNull(shares[0].percentage)
                assertNull(shares[0].guestName)
                assertNull(shares[0].guestPersonId)

                assertEquals(2L, shares[1].id)
                assertEquals(split.id, shares[1].splitId)
                assertEquals(1L, shares[1].personId)
                assertEquals(600.0, shares[1].shareAmount, 0.0)
                assertNull(shares[1].guestName)
                assertNull(shares[1].percentage)
                assertNull(shares[1].guestPersonId)

                // Verify Settlements
                val settlements = database.settlementDao().getAllSettlementsDirect().sortedBy { it.id }
                assertEquals(2, settlements.size)

                val iou = settlements[0]
                assertEquals(1L, iou.id)
                assertEquals(1L, iou.fromPersonId)
                assertEquals(-1L, iou.toPersonId)
                assertEquals(250.0, iou.amount, 0.0)
                assertEquals(4000L, iou.date)
                assertEquals("Borrowed for fuel", iou.note)
                assertEquals(true, iou.isIou)
                assertEquals(1L, iou.groupId)
                assertEquals(4000L, iou.createdAt)
                assertEquals(4000L, iou.updatedAt)
                assertEquals(SyncState.LOCAL_ONLY, iou.syncState)
                assertEquals(SettlementStatus.CONFIRMED, iou.status)
                assertNull(iou.recordedByPersonId)
                assertNull(iou.respondedAt)
                assertNull(iou.responseNote)
                assertNull(iou.pendingResponse)
                assertEquals(-1, iou.remindedStage)
                assertTrue("IOU remoteId must be valid UUID v4", uuidV4.matches(iou.remoteId))

                val settle = settlements[1]
                assertEquals(2L, settle.id)
                assertEquals(-1L, settle.fromPersonId)
                assertEquals(1L, settle.toPersonId)
                assertEquals(100.0, settle.amount, 0.0)
                assertEquals(5000L, settle.date)
                assertEquals("Settled part", settle.note)
                assertEquals(false, settle.isIou)
                assertEquals(1L, settle.groupId)
                assertEquals(5000L, settle.createdAt)
                assertEquals(5000L, settle.updatedAt)
                assertEquals(SyncState.LOCAL_ONLY, settle.syncState)
                assertEquals(SettlementStatus.CONFIRMED, settle.status)
                assertNull(settle.recordedByPersonId)
                assertNull(settle.respondedAt)
                assertNull(settle.responseNote)
                assertNull(settle.pendingResponse)
                assertEquals(-1, settle.remindedStage)
                assertTrue("Settle remoteId must be valid UUID v4", uuidV4.matches(settle.remoteId))
                val remoteIds = listOf(group.remoteId, directTx.remoteId, splitTx.remoteId,
                    split.remoteId, iou.remoteId, settle.remoteId)
                assertEquals("Each migrated row receives its own remote UUID", remoteIds.size, remoteIds.toSet().size)

                // Verify New Tables at v10 can be inserted into cleanly
                val subId = database.subscriptionDao().insert(
                    SubscriptionEntity(
                        name = "Streaming",
                        amount = 199.0,
                        category = "Entertainment",
                        paymentMethod = "UPI",
                        cycle = "MONTHLY",
                        startDate = 6000L,
                        createdAt = 6000L,
                    )
                )
                assertEquals(1L, subId)

                val askId = database.paymentAskDao().insert(
                    PaymentAskEntity(
                        personId = 1L,
                        amount = 250.0,
                        sentAt = 7000L,
                    )
                )
                assertEquals(1L, askId)

                val receiptId = database.receiptDao().insert(
                    ReceiptEntity(
                        transactionId = 1L,
                        fileName = "cafe_receipt.jpg",
                        width = 1080,
                        height = 1920,
                        bytes = 204800L,
                        createdAt = 8000L,
                    )
                )
                assertEquals(1L, receiptId)
                assertEquals(1, database.receiptDao().forTransaction(1L).size)
            }
        } finally {
            database.close()
        }
    }

}
