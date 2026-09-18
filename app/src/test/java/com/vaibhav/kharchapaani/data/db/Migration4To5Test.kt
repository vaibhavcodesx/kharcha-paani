package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-4-5-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `people keep everything and start with no emoji`() {
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL(
                "INSERT INTO people (id, name, avatarColor, createdAt, profileId, avatarUrl) " +
                    "VALUES (1, 'Rahul', '#FF0000', 10, NULL, NULL), (2, 'Priya', '#00FF00', 11, 'p-2', 'https://photo')"
            )
        }

        // Validates the migrated schema against the exported v5 schema.
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).use { db ->
            assertEquals(
                listOf(listOf("Rahul", "#FF0000", null, null, null), listOf("Priya", "#00FF00", "p-2", "https://photo", null)),
                db.rows("SELECT name, avatarColor, profileId, avatarUrl, avatarEmoji FROM people ORDER BY id"),
            )
        }
    }

    @Test
    fun `every migration from the first exported schema reaches v5 and opens with Room`() {
        helper.createDatabase(dbName, 2).close()
        helper.runMigrationsAndValidate(dbName, 5, true, *AppDatabase.ALL_MIGRATIONS).close()

        val database = AppDatabase.builder(context, dbName).build()
        database.openHelper.writableDatabase.execSQL("UPDATE people SET avatarEmoji = '🦊'")
        database.close()
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
