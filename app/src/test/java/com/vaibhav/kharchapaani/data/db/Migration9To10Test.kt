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

/** v10 (bill photos) adds the receipts table. Additive: what's already on the phone is untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration9To10Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-9-10-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `expenses stay as they were, and a bill photo goes with its expense`() {
        helper.createDatabase(dbName, 9).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, type, amount, category, merchant, paymentMethod, date, note, isSplit, groupId, createdAt, remoteId, updatedAt, syncState) " +
                    "VALUES (7, 'expense', 2340.0, 'Groceries', 'DMart', 'UPI', 1000, '', 0, NULL, 1000, 'r-7', 1000, 'PENDING')"
            )
        }

        helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10).use { db ->
            db.query("SELECT merchant, amount FROM transactions WHERE id = 7").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("DMart", c.getString(0))
                assertEquals(2340.0, c.getDouble(1), 0.001)
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO receipts (transactionId, fileName, width, height, bytes, createdAt, remotePath) VALUES (7, 'a.jpg', 1500, 2000, 300000, 1100, NULL)")
            db.query("SELECT COUNT(*) FROM receipts").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.execSQL("DELETE FROM transactions WHERE id = 7")
            db.query("SELECT COUNT(*) FROM receipts").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
