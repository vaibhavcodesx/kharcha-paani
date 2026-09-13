package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration2To3Test {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-2-3-${UUID.randomUUID()}.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun `existing transactions keep their data, get unique v4 remote ids, and stay local-only`() {
        helper.createDatabase(dbName, 2).use { db ->
            for (id in 1..3) {
                db.execSQL(
                    "INSERT INTO transactions (id, type, amount, category, merchant, paymentMethod, date, note, isSplit, groupId, createdAt) " +
                        "VALUES ($id, 'expense', ${id * 100}.5, 'Food', 'Cafe $id', 'UPI', 1700000000000, '', 0, NULL, ${1000 + id})"
                )
            }
        }

        // Validates the migrated schema against the exported v3 schema, column defaults included.
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).close()

        val database = AppDatabase.builder(context, dbName).build()
        val rows = runBlocking { database.transactionDao().getAllTransactionsDirect() }.sortedBy { it.id }
        database.close()

        assertEquals(listOf("Cafe 1", "Cafe 2", "Cafe 3"), rows.map { it.merchant })
        assertEquals(listOf(100.5, 200.5, 300.5), rows.map { it.amount })
        assertTrue(rows.all { it.syncState == SyncState.LOCAL_ONLY })
        assertEquals(listOf(1001L, 1002L, 1003L), rows.map { it.updatedAt })
        assertEquals(3, rows.map { it.remoteId }.toSet().size)
        val uuidV4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        rows.forEach { assertTrue("not a v4 UUID: ${it.remoteId}", uuidV4.matches(it.remoteId)) }
    }
}
