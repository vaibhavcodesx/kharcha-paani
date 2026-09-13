package com.vaibhav.kharchapaani.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerDatabasesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Fresh ids per test: database files from a previous test can outlive its cleanup on Windows
    // (open handles block deletion), and reusing a name then fails to open.
    private val alice = UUID.randomUUID().toString()
    private val bob = UUID.randomUUID().toString()

    @After
    fun tearDown() = runBlocking {
        LedgerDatabases.close()
        context.databaseList().forEach { context.deleteDatabase(it) }
    }

    @Test
    fun `each account gets its own file named by user id`() {
        assertEquals("ledger-$alice.db", LedgerDatabases.databaseName(alice))
        assertEquals("ledger-$alice.db", LedgerDatabases.databaseName(alice.uppercase()))
    }

    @Test
    fun `anything other than a user id is rejected before it reaches a file path`() {
        assertThrows(IllegalArgumentException::class.java) { LedgerDatabases.databaseName("../splitflow") }
        assertThrows(IllegalArgumentException::class.java) { LedgerDatabases.databaseName("") }
    }

    @Test
    fun `first account to sign in takes over the pre-accounts ledger`() = runBlocking {
        seedLegacyDatabase("Existing friend")

        val database = LedgerDatabases.open(context, alice)

        assertEquals(listOf("Existing friend"), database.personDao().getAllPeopleDirect().map { it.name })
        assertFalse(context.getDatabasePath(LedgerDatabases.LEGACY_DATABASE_NAME).exists())
    }

    @Test
    fun `rows still sitting in the legacy write-ahead log survive the move`() = runBlocking {
        // Recreate what a killed process leaves behind: committed rows only in splitflow.db-wal.
        val legacyPath = context.getDatabasePath(LedgerDatabases.LEGACY_DATABASE_NAME)
        val snapshotDir = File(context.cacheDir, "wal-snapshot").apply { mkdirs() }
        val legacy = AppDatabase.builder(context, LedgerDatabases.LEGACY_DATABASE_NAME).build()
        legacy.personDao().insertPerson(PersonEntity(name = "Only in the WAL", avatarColor = "#FFFFFF"))
        val walFile = File(legacyPath.path + "-wal")
        assertTrue("expected the insert to still be in the WAL", walFile.length() > 0)
        val files = listOf(legacyPath, walFile, File(legacyPath.path + "-shm"))
        files.forEach { it.copyTo(File(snapshotDir, it.name), overwrite = true) }
        legacy.close()
        files.forEach { File(snapshotDir, it.name).copyTo(it, overwrite = true) }

        val database = LedgerDatabases.open(context, alice)

        assertEquals(listOf("Only in the WAL"), database.personDao().getAllPeopleDirect().map { it.name })
    }

    @Test
    fun `a second account cannot see the first account's ledger`() = runBlocking {
        seedLegacyDatabase("Alice's friend")
        LedgerDatabases.open(context, alice)

        val bobsDatabase = LedgerDatabases.open(context, bob)

        assertEquals(emptyList<PersonEntity>(), bobsDatabase.personDao().getAllPeopleDirect())
    }

    @Test
    fun `switching accounts closes the previous account's database`() = runBlocking {
        val alicesDatabase = LedgerDatabases.open(context, alice)
        assertSame(alicesDatabase, LedgerDatabases.open(context, alice))

        LedgerDatabases.open(context, bob)

        assertFalse(alicesDatabase.isOpen)
    }

    @Test
    fun `an account that already has a file never has it replaced by the legacy ledger`() = runBlocking {
        LedgerDatabases.open(context, alice).personDao().insertPerson(PersonEntity(name = "Alice's own", avatarColor = "#000000"))
        LedgerDatabases.close()
        seedLegacyDatabase("Legacy row")

        assertFalse(LedgerDatabases.claimLegacyDatabase(context, alice))

        val names = LedgerDatabases.open(context, alice).personDao().getAllPeopleDirect().map { it.name }
        assertEquals(listOf("Alice's own"), names)
        assertTrue(context.getDatabasePath(LedgerDatabases.LEGACY_DATABASE_NAME).exists())
    }

    @Test
    fun `a new account starts with an empty ledger and no sample data`() = runBlocking {
        val database = LedgerDatabases.open(context, bob)

        assertEquals(0, database.personDao().getAllPeopleDirect().size)
        assertEquals(0, database.groupDao().getAllGroupsDirect().size)
    }

    private suspend fun seedLegacyDatabase(personName: String) {
        val legacy = AppDatabase.builder(context, LedgerDatabases.LEGACY_DATABASE_NAME).build()
        legacy.personDao().insertPerson(PersonEntity(name = personName, avatarColor = "#FFFFFF"))
        legacy.close()
    }
}
