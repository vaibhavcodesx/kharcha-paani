package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration3To4Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-3-4-${UUID.randomUUID()}.db"
    private val uuidV4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `existing groups, people, splits and settlements keep their data and stay local-only`() {
        helper.createDatabase(dbName, 3).use { db ->
            db.execSQL("INSERT INTO people (id, name, avatarColor, createdAt) VALUES (1, 'Rahul', '#FF0000', 10), (2, 'Priya', '#00FF00', 11)")
            db.execSQL(
                "INSERT INTO groups (id, name, description, emoji, colorHex, createdAt) " +
                    "VALUES (1, 'Goa', 'beach', '🏖️', '#FF7A3D', 500), (2, 'Flat', '', '🏠', '#C6F24C', 600)"
            )
            db.execSQL("INSERT INTO group_members (id, groupId, personId) VALUES (1, 1, -1), (2, 1, 1), (3, 2, 2)")
            db.execSQL(
                "INSERT INTO transactions (id, type, amount, category, merchant, paymentMethod, date, note, isSplit, groupId, createdAt, remoteId, updatedAt, syncState) " +
                    "VALUES (1, 'expense', 300.0, 'Food', 'Shack', 'UPI', 1700000000000, '', 1, 1, 700, 'a1a1a1a1-0000-4000-8000-000000000001', 700, 'LOCAL_ONLY')"
            )
            db.execSQL("INSERT INTO splits (id, transactionId, totalAmount, paidByPersonId, splitType) VALUES (1, 1, 300.0, -1, 'equal')")
            db.execSQL("INSERT INTO split_shares (id, splitId, personId, shareAmount, percentage) VALUES (1, 1, -1, 150.0, NULL), (2, 1, 1, 150.0, NULL)")
            db.execSQL(
                "INSERT INTO settlements (id, fromPersonId, toPersonId, amount, date, note, isIou, groupId, createdAt) " +
                    "VALUES (1, 1, -1, 150.0, 1700000100000, 'paid back', 0, 1, 800), (2, -1, 2, 40.0, 1700000200000, '', 1, NULL, 900)"
            )
            db.execSQL("INSERT INTO pending_remote_deletes (remoteId, deletedAt) VALUES ('b2b2b2b2-0000-4000-8000-000000000002', 42)")
        }

        // Validates the migrated schema against the exported v4 schema, defaults and indices included.
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).use { db ->
            val people = db.rows("SELECT name, profileId, avatarUrl FROM people ORDER BY id")
            assertEquals(listOf("Rahul", "Priya"), people.map { it[0] })
            assertTrue(people.all { it[1] == null && it[2] == null })

            val groups = db.rows("SELECT name, description, remoteId, updatedAt, syncState, archivedAt, createdByProfileId FROM groups ORDER BY id")
            assertEquals(listOf("Goa", "Flat"), groups.map { it[0] })
            assertEquals(listOf("beach", ""), groups.map { it[1] })
            assertEquals(listOf("500", "600"), groups.map { it[3] })
            assertTrue(groups.all { it[4] == "LOCAL_ONLY" && it[5] == null && it[6] == null })
            assertUniqueV4(groups.map { it[2] })

            assertEquals(3, db.rows("SELECT id FROM group_members").size)

            val transaction = db.rows("SELECT remoteId, syncState, ownerProfileId FROM transactions").single()
            assertEquals(listOf("a1a1a1a1-0000-4000-8000-000000000001", "LOCAL_ONLY", null), transaction)

            val split = db.rows("SELECT totalAmount, remoteId FROM splits").single()
            assertEquals("300.0", split[0])
            assertUniqueV4(listOf(split[1]))
            assertEquals(2, db.rows("SELECT id FROM split_shares").size)

            val settlements = db.rows("SELECT note, amount, remoteId, updatedAt, syncState FROM settlements ORDER BY id")
            assertEquals(listOf("paid back", ""), settlements.map { it[0] })
            assertEquals(listOf("800", "900"), settlements.map { it[3] })
            assertTrue(settlements.all { it[4] == "LOCAL_ONLY" })
            assertUniqueV4(settlements.map { it[2] })

            val tombstone = db.rows("SELECT remoteId, deletedAt, entityType FROM pending_remote_deletes").single()
            assertEquals(listOf("b2b2b2b2-0000-4000-8000-000000000002", "42", "transaction"), tombstone)
        }
    }

    @Test
    fun `the migrated database opens with Room and people can share no profile`() {
        helper.createDatabase(dbName, 3).close()
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).close()

        val database = AppDatabase.builder(context, dbName).build()
        database.openHelper.writableDatabase.apply {
            // A unique index on a nullable column still allows any number of local-only friends.
            execSQL("INSERT INTO people (name, avatarColor, createdAt) VALUES ('A', '#000', 1), ('B', '#000', 2)")
            assertNull(rows("SELECT profileId FROM people WHERE name = 'B'").single()[0])
        }
        database.close()
    }

    private fun assertUniqueV4(ids: List<String?>) {
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue("not a v4 UUID: $it", it != null && uuidV4.matches(it)) }
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> =
        query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
                }
            }
        }
}
