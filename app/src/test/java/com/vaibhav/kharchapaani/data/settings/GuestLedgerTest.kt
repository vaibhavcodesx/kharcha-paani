package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.ask.AskSentReceiver
import com.vaibhav.kharchapaani.ask.AskShare
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GuestLedgerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val guest = GuestSession(UUID.randomUUID().toString())
    private val files = mutableSetOf<String>()

    @Before fun setUp() = runBlocking {
        LedgerDatabases.close()
        assertTrue(context.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE).edit().clear().commit())
    }

    @After fun tearDown() = runBlocking {
        LedgerDatabases.close()
        files.forEach { context.deleteDatabase(it) }
        context.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    @Test fun `mode and guest identity persist and switching cloud never replaces the guest identity`() {
        assertEquals(LedgerMode.Choice.CLOUD, LedgerMode(context).choice)
        val original = LedgerMode(context).useLocal()
        assertEquals(LedgerMode.Choice.LOCAL, LedgerMode(context).choice)
        assertEquals(original, LedgerMode(context).guestOrNull())
        LedgerMode(context).useCloud()
        assertNull(LedgerMode(context).guestOrNull())
        assertEquals(original, LedgerMode(context).useLocal())
    }

    @Test fun `local opening leaves legacy rows available for the first real account`() = runBlocking {
        files += LedgerDatabases.LEGACY_DATABASE_NAME
        files += LedgerDatabases.localDatabaseName(guest)
        files += LedgerDatabases.databaseName(guest.id)
        val legacy = AppDatabase.builder(context, LedgerDatabases.LEGACY_DATABASE_NAME).build()
        try {
            legacy.personDao().insertPerson(PersonEntity(name = "Legacy friend", avatarColor = "#FFFFFF"))
        } finally { legacy.close() }

        val local = LedgerDatabases.openLocal(context, guest)
        assertTrue(local.personDao().getAllPeopleDirect().isEmpty())
        assertTrue(context.getDatabasePath(LedgerDatabases.LEGACY_DATABASE_NAME).exists())
        local.personDao().insertPerson(PersonEntity(name = "Local friend", avatarColor = "#FFFFFF"))
        val account = LedgerDatabases.open(context, guest.id)
        assertFalse(local.isOpen)
        assertEquals(listOf("Legacy friend"), account.personDao().getAllPeopleDirect().map { it.name })
        assertEquals(listOf("Local friend"), LedgerDatabases.openLocal(context, guest).personDao().getAllPeopleDirect().map { it.name })
    }

    @Test fun `same UUID in local and account mode has separate durable Room files`() = runBlocking {
        files += LedgerDatabases.localDatabaseName(guest)
        files += LedgerDatabases.databaseName(guest.id)
        val local = LedgerDatabases.openLocal(context, guest)
        local.personDao().insertPerson(PersonEntity(name = "Only local", avatarColor = "#FFFFFF"))
        val account = LedgerDatabases.open(context, guest.id)
        assertTrue(account.personDao().getAllPeopleDirect().isEmpty())
        account.personDao().insertPerson(PersonEntity(name = "Only account", avatarColor = "#FFFFFF"))
        LedgerDatabases.close()
        assertEquals(listOf("Only local"), LedgerDatabases.openLocal(context, guest).personDao().getAllPeopleDirect().map { it.name })
        assertEquals(listOf("Only account"), LedgerDatabases.open(context, guest.id).personDao().getAllPeopleDirect().map { it.name })
    }

    @Test fun `local receipt namespace matches local ledger and never account receipt namespace`() {
        val local = ReceiptStore.forLocal(context, guest).dir
        assertEquals("ledger-local-${guest.id}", local.name)
        assertNotEquals(ReceiptStore.forAccount(context, guest.id).dir, local)
    }

    @Test fun `background local identity resolves without asking auth and cloud only resolves its account`() = runBlocking {
        val chosen = LedgerMode(context).useLocal()
        val local = BackgroundLedger.current(context) { error("Local resolution touched cloud auth") }
        assertEquals(BackgroundLedger(chosen.id, true), local)
        LedgerMode(context).useCloud()
        assertEquals(BackgroundLedger(guest.id, false), BackgroundLedger.current(context) { guest.id })
        assertNull(BackgroundLedger.current(context) { null })
    }

    @Test fun `switching local during cloud identity load refuses the stale background account`() = runBlocking {
        assertNull(BackgroundLedger.current(context) {
            LedgerMode(context).useLocal()
            guest.id
        })
    }

    @Test fun `local ask callback records locally and a stale cloud callback cannot switch its database`() = runBlocking {
        val chosen = LedgerMode(context).useLocal()
        files += LedgerDatabases.localDatabaseName(chosen)
        val local = LedgerDatabases.openLocal(context, chosen)
        val person = local.personDao().insertPerson(PersonEntity(name = "Rohan", avatarColor = "#FFFFFF"))
        fun picked(id: String, isLocal: Boolean = true) = Intent(context, AskSentReceiver::class.java).apply {
            putExtra(AskShare.EXTRA_PERSON_ID, person)
            putExtra(AskShare.EXTRA_AMOUNT, 200.0)
            putExtra(AskShare.EXTRA_USER_ID, id)
            putExtra(AskShare.EXTRA_IS_LOCAL, isLocal)
        }
        assertTrue(AskSentReceiver.record(context, picked(chosen.id), now = 5_000L))
        assertFalse(AskSentReceiver.record(context, picked(guest.id), now = 6_000L))
        assertFalse(AskSentReceiver.record(context, picked(chosen.id, isLocal = false), now = 7_000L))
        assertTrue(local.isOpen)
        val ask = local.paymentAskDao().observeAll().first().single()
        assertEquals(200.0, ask.amount, 0.0)
        assertEquals(5_000L, ask.sentAt)
    }
}
