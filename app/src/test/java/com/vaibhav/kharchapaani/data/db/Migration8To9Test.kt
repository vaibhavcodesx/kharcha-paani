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

/** v9 (ask-to-pay) adds the local log of asks. Additive: what's already on the phone is untouched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration8To9Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-8-9-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `people stay as they were, and asks can be logged and go with their friend`() {
        helper.createDatabase(dbName, 8).use { db ->
            db.execSQL("INSERT INTO people (id, name, avatarColor, createdAt, profileId, avatarUrl, avatarEmoji, upiId, hue) VALUES (2, 'Rohan', '#70C1FF', 700, NULL, NULL, NULL, NULL, 210)")
        }

        helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9).use { db ->
            db.query("SELECT name, hue FROM people WHERE id = 2").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Rohan", c.getString(0))
                assertEquals(210, c.getInt(1))
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.execSQL("INSERT INTO payment_asks (personId, amount, sentAt) VALUES (2, 340.0, 900)")
            db.query("SELECT COUNT(*) FROM payment_asks").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            db.execSQL("DELETE FROM people WHERE id = 2")
            db.query("SELECT COUNT(*) FROM payment_asks").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
        }
    }
}
