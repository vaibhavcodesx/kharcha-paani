package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration5To6Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-5-6-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `adding the subscriptions table keeps the ledger as it was`() {
        helper.createDatabase(dbName, 5).use { db ->
            db.execSQL(
                "INSERT INTO transactions (id, type, amount, category, merchant, paymentMethod, date, note, isSplit, groupId, createdAt, remoteId, updatedAt, syncState) " +
                    "VALUES (1, 'expense', 300.0, 'Food', 'Shack', 'UPI', 1700000000000, '', 0, NULL, 700, 'a1', 700, 'LOCAL_ONLY')"
            )
        }

        // Validates the migrated schema against the exported v6 schema.
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT merchant FROM transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Shack", cursor.getString(0))
            }
            db.query("SELECT count(*) FROM subscriptions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    @Test
    fun `every migration from the first exported schema reaches v6 and opens with Room`() {
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 6, true, *AppDatabase.ALL_MIGRATIONS).close()

        val database = AppDatabase.builder(context, dbName).build()
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO subscriptions (name, amount, category, paymentMethod, cycle, startDate, createdAt) " +
                "VALUES ('Netflix', 649.0, 'Entertainment', 'Credit Card', 'MONTHLY', 1700000000000, 1700000000000)"
        )
        database.close()
    }
}
