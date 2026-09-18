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
 * v7 adds a groupmate's UPI handle to `people`, so settling up with someone can show where to pay.
 * Additive and nullable: everyone already on the phone keeps their row exactly as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration6To7Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-6-7-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `people keep their names and colours, and gain an empty upi id`() {
        helper.createDatabase(dbName, 6).use { db ->
            db.execSQL(
                "INSERT INTO people (id, name, avatarColor, createdAt, profileId, avatarUrl, avatarEmoji) " +
                    "VALUES (1, 'Lal', '#70C1FF', 700, 'profile-lal', NULL, '🦊')"
            )
        }

        helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7).use { db ->
            db.query("SELECT name, avatarColor, avatarEmoji, upiId FROM people WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Lal", cursor.getString(0))
                assertEquals("#70C1FF", cursor.getString(1))
                assertEquals("🦊", cursor.getString(2))
                assertTrue("a person from before v7 has no handle yet", cursor.isNull(3))
            }
        }
    }

    @Test
    fun `every migration from the first exported schema reaches v7 and opens with Room`() {
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 7, true, *AppDatabase.ALL_MIGRATIONS).close()

        val room = AppDatabase.builder(context, dbName).addMigrations(*AppDatabase.ALL_MIGRATIONS).build()
        room.openHelper.writableDatabase.use { db ->
            db.query("SELECT upiId FROM people").use { cursor -> assertEquals(0, cursor.count) }
        }
        room.close()
    }
}
