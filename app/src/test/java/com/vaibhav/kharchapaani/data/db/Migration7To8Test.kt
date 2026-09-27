package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * v8 (1.4) adds settlement confirmation, guests in shared-group expenses and a groupmate's own
 * colour. Additive: every settlement already on the phone is confirmed, as the server makes every
 * existing one; shares and people keep what they had.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration7To8Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-7-8-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `settlements already here are confirmed, and shares and people keep their values`() {
        helper.createDatabase(dbName, 7).use { db ->
            db.execSQL("INSERT INTO people (id, name, avatarColor, createdAt, profileId, avatarUrl, avatarEmoji, upiId) VALUES (2, 'Rohan', '#70C1FF', 700, 'p-rohan', NULL, NULL, NULL)")
            db.execSQL(
                "INSERT INTO settlements (id, fromPersonId, toPersonId, amount, date, note, isIou, groupId, createdAt, remoteId, updatedAt, syncState) " +
                    "VALUES (1, -1, 2, 450.0, 800, 'Goa', 0, NULL, 800, 's-1', 800, 'SYNCED')"
            )
            db.execSQL("INSERT INTO transactions (id, type, amount, category, merchant, paymentMethod, date, note, isSplit, groupId, createdAt, remoteId, updatedAt, syncState, ownerProfileId) VALUES (1, 'expense', 900.0, 'Food', 'Shack', 'UPI', 900, '', 1, NULL, 900, 't-1', 900, 'SYNCED', NULL)")
            db.execSQL("INSERT INTO splits (id, transactionId, totalAmount, paidByPersonId, splitType, remoteId) VALUES (1, 1, 900.0, -1, 'equal', 'sp-1')")
            db.execSQL("INSERT INTO split_shares (id, splitId, personId, shareAmount, percentage) VALUES (1, 1, 2, 450.0, NULL)")
        }

        helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8).use { db ->
            db.query("SELECT amount, status, recordedByPersonId, pendingResponse, remindedStage FROM settlements WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(450.0, c.getDouble(0), 0.0)
                assertEquals("CONFIRMED", c.getString(1))
                assertTrue(c.isNull(2))
                assertTrue(c.isNull(3))
                assertEquals(-1, c.getInt(4))
            }
            db.query("SELECT personId, shareAmount, guestName, guestPersonId FROM split_shares WHERE id = 1").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2L, c.getLong(0))
                assertEquals(450.0, c.getDouble(1), 0.0)
                assertTrue("not a guest", c.isNull(2) && c.isNull(3))
            }
            db.query("SELECT name, avatarColor, hue FROM people WHERE id = 2").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("#70C1FF", c.getString(1))
                assertTrue("no chosen colour yet", c.isNull(2))
            }
        }
    }

    @Test
    fun `every migration from the first exported schema reaches v8 and opens with Room`() {
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 8, true, *AppDatabase.ALL_MIGRATIONS).close()

        val room = AppDatabase.builder(context, dbName).build()
        room.openHelper.writableDatabase.use { db ->
            db.query("SELECT status FROM settlements").use { cursor -> assertEquals(0, cursor.count) }
        }
        room.close()
    }
}
