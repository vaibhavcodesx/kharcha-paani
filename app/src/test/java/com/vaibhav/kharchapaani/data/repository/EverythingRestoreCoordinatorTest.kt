package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.domain.*
import android.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EverythingRestoreCoordinatorTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val databaseName = "everything-recovery-${java.util.UUID.randomUUID()}"
    private var db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
    private val directory = File(context.cacheDir, databaseName).apply { check(mkdirs()) }
    private val photos = ReceiptStore(File(directory, "photos").apply { check(mkdirs()) })
    private val journal = File(directory, "restore.json")
    private val settings = EverythingSettings(customExpenseCategories = listOf("Keep me"), aiEnabled = false)
    private val backup = EverythingBackup(exportedAt = 1, appVersion = "test", settings = settings,
        people = listOf(EverythingPerson(11, "One friend", "#123456", 1, null, null, null, null, null)))

    @After fun close() {
        db.close()
        context.deleteDatabase(databaseName)
        directory.deleteRecursively()
    }

    private fun coordinator(apply: suspend (EverythingSettings) -> Unit) =
        EverythingRestoreCoordinator(db, EverythingBackupRepository(db, photos), journal, apply)

    private suspend fun reject(action: suspend () -> Unit) {
        try { action(); fail("Unsafe restore accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `new coordinator after Room reopen resumes settings without inserting duplicate rows`() = runBlocking {
        var attempts = 0
        try {
            coordinator { attempts++; throw IllegalStateException("simulate interrupted preference write") }
                .restore(backup, isLocal = true)
            fail("Settings failure was swallowed")
        } catch (_: IllegalStateException) { }
        assertEquals(1, attempts)
        assertEquals(listOf(11L), db.personDao().getAllPeopleDirect().map { it.id })
        assertTrue(journal.exists())
        assertNotNull(db.syncDao().meta(EVERYTHING_RESTORE_MARKER))
        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        var applied: EverythingSettings? = null
        val restarted = coordinator { attempts++; applied = it }
        assertTrue(restarted.resume(isLocal = true))
        assertEquals(settings, applied)
        assertEquals(2, attempts)
        assertEquals(listOf(11L), db.personDao().getAllPeopleDirect().map { it.id })
        assertFalse(journal.exists())
        assertFalse(restarted.resume(isLocal = true))
        assertEquals(2, attempts)
    }

    @Test fun `corrupt journal never applies settings or writes rows`() = runBlocking {
        journal.writeText("not an archive")
        var applied = false
        reject { coordinator { applied = true }.resume(isLocal = true) }
        assertFalse(applied)
        assertTrue(db.personDao().getAllPeopleDirect().isEmpty())
        assertNull(db.syncDao().meta(EVERYTHING_RESTORE_MARKER))
        assertEquals("not an archive", journal.readText())
    }

    @Test fun `invalid UTF8 journal is rejected without replacing corrupt bytes`() = runBlocking {
        val bytes = byteArrayOf(0xc3.toByte(), 0x28)
        journal.writeBytes(bytes)
        var applied = false
        reject { coordinator { applied = true }.resume(true) }
        assertFalse(applied)
        assertTrue(db.personDao().getAllPeopleDirect().isEmpty())
        assertArrayEquals(bytes, journal.readBytes())
    }

    @Test fun `AtomicFile backup journal is recovered before restoring`() = runBlocking {
        File(journal.path + ".bak").writeText(EverythingBackup.encode(backup))
        var applied: EverythingSettings? = null
        assertTrue(coordinator { applied = it }.resume(true))
        assertEquals(settings, applied)
        assertEquals(listOf(11L), db.personDao().getAllPeopleDirect().map { it.id })
        assertFalse(journal.exists())
        assertFalse(File(journal.path + ".bak").exists())
    }

    @Test fun `fresh restore refuses occupied destination before creating journal`() = runBlocking {
        val existing = PersonEntity(id = 99, name = "Existing", avatarColor = "#123456")
        db.personDao().insertPerson(existing)
        var applied = false
        reject { coordinator { applied = true }.restore(backup, isLocal = true) }
        assertFalse(applied)
        assertEquals(listOf(existing), db.personDao().getAllPeopleDirect())
        assertFalse(journal.exists())
    }

    @Test fun `pending journal with no marker cannot claim an occupied destination`() = runBlocking {
        journal.writeText(EverythingBackup.encode(backup))
        val existing = PersonEntity(id = 99, name = "Existing", avatarColor = "#123456")
        db.personDao().insertPerson(existing)
        var applied = false
        reject { coordinator { applied = true }.resume(isLocal = true) }
        assertFalse(applied)
        assertEquals(listOf(existing), db.personDao().getAllPeopleDirect())
        assertNull(db.syncDao().meta(EVERYTHING_RESTORE_MARKER))
        assertTrue(journal.exists())
    }

    @Test fun `different valid journal cannot apply settings to already committed rows`() = runBlocking {
        try { coordinator { throw IllegalStateException("stop after rows") }.restore(backup, true) }
        catch (_: IllegalStateException) { }
        journal.writeText(EverythingBackup.encode(backup.copy(settings = settings.copy(aiEnabled = true))))
        var applied = false
        reject { coordinator { applied = true }.resume(true) }
        assertFalse(applied)
        assertEquals(listOf(11L), db.personDao().getAllPeopleDirect().map { it.id })
    }

    @Test fun `nonlocal caller cannot start or resume a restore`() = runBlocking {
        var applied = false
        reject { coordinator { applied = true }.restore(backup, false) }
        assertFalse(journal.exists())
        journal.writeText(EverythingBackup.encode(backup))
        reject { coordinator { applied = true }.resume(false) }
        assertFalse(applied)
        assertTrue(db.personDao().getAllPeopleDirect().isEmpty())
    }

    private val photoBytes = byteArrayOf(1, 3, 5, 7)
    private fun photoBackup() = backup.copy(
        transactions = listOf(EverythingTransaction(51, "expense", 10.0, "Food", "Dinner", "Cash", 1, "", false, null, 1, "tx-a", 1, "LOCAL_ONLY", null)),
        receipts = listOf(EverythingReceipt(71, 51, "bill.jpg", 2, 2, 4, 1, null, Base64.encodeToString(photoBytes, Base64.NO_WRAP))),
    )

    @Test fun `resume adopts exact unreferenced receipt left before Room commit`() = runBlocking {
        val archive = photoBackup()
        journal.writeText(EverythingBackup.encode(archive))
        File(photos.dir, "bill.jpg").writeBytes(photoBytes)
        var applied: EverythingSettings? = null
        assertTrue(coordinator { applied = it }.resume(true))
        assertEquals(settings, applied)
        assertEquals(1, db.receiptDao().forTransaction(51).size)
        assertArrayEquals(photoBytes, File(photos.dir, "bill.jpg").readBytes())
        assertFalse(journal.exists())
    }

    @Test fun `resume never overwrites or deletes a different preexisting receipt`() = runBlocking {
        journal.writeText(EverythingBackup.encode(photoBackup()))
        val existing = File(photos.dir, "bill.jpg").apply { writeBytes(byteArrayOf(9, 9, 9, 9)) }
        var applied = false
        reject { coordinator { applied = true }.resume(true) }
        assertFalse(applied)
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), existing.readBytes())
        assertTrue(db.personDao().getAllPeopleDirect().isEmpty())
        assertTrue(db.transactionDao().getAllTransactionsDirect().isEmpty())
        assertNull(db.syncDao().meta(EVERYTHING_RESTORE_MARKER))
        assertTrue(journal.exists())
    }

    @Test fun `fresh restore still refuses even byte-identical existing receipt`() = runBlocking {
        File(photos.dir, "bill.jpg").writeBytes(photoBytes)
        reject { coordinator { }.restore(photoBackup(), true) }
        assertTrue(db.personDao().getAllPeopleDirect().isEmpty())
        assertNull(db.syncDao().meta(EVERYTHING_RESTORE_MARKER))
        assertArrayEquals(photoBytes, File(photos.dir, "bill.jpg").readBytes())
    }
}
