package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UX-01: deleting this phone's ledger removes the guest ledger's file and bill photos, forgets its
 * identity and returns to the account flow, and a later guest ledger starts empty with a new id.
 * An account's own files are left alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestDeletionTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `deleting removes the guest files, forgets the identity and goes back to sign-in`(): Unit = runBlocking {
        val mode = LedgerMode(app)
        val guest = mode.useLocal()
        val db = app.getDatabasePath(LedgerDatabases.localDatabaseName(guest))
        db.parentFile!!.mkdirs()
        db.writeText("ledger")
        val photos = ReceiptStore.forLocal(app, guest).dir
        photos.mkdirs()
        photos.resolve("a.jpg").writeText("photo")
        // Another account's files must survive.
        val other = app.getDatabasePath(LedgerDatabases.databaseName("11111111-2222-3333-4444-555555555555"))
        other.writeText("someone else")

        GuestDeletion.wipeLocal(app, guest)
        mode.discardGuest()

        assertFalse("the ledger file is gone", db.exists())
        assertFalse("the bill photos are gone", photos.exists())
        assertTrue("an account's ledger is untouched", other.exists())
        assertEquals(LedgerMode.Choice.CLOUD, mode.choice)
        assertNull(mode.guestOrNull())
        other.delete()
    }

    @Test
    fun `the next guest ledger is a new, empty one`(): Unit = runBlocking {
        val mode = LedgerMode(app)
        val first = mode.useLocal()
        GuestDeletion.wipeLocal(app, first)
        mode.discardGuest()

        val second = mode.useLocal()

        assertNotEquals(first.id, second.id)
        assertFalse(app.getDatabasePath(LedgerDatabases.localDatabaseName(second)).exists())
        GuestDeletion.wipeLocal(app, second)
        mode.discardGuest()
    }
}
