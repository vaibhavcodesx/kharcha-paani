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
    fun `existing rows survive reopening the current schema version`() {
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
}
