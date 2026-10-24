package com.vaibhav.kharchapaani.ask

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** An ask is logged when the share sheet reports a picked app, and only for a well-formed ask. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AskSentReceiverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val userId = "00000000-0000-4000-8000-0000000000a1"

    @After
    fun tearDown() = runBlocking {
        LedgerDatabases.close()
        context.deleteDatabase(LedgerDatabases.databaseName(userId))
        Unit
    }

    private fun picked(personId: Long, amount: Double, user: String? = userId) = Intent(context, AskSentReceiver::class.java).apply {
        putExtra(AskShare.EXTRA_PERSON_ID, personId)
        putExtra(AskShare.EXTRA_AMOUNT, amount)
        user?.let { putExtra(AskShare.EXTRA_USER_ID, it) }
    }

    @Test
    fun `picking an app logs the ask in that account's ledger`() = runBlocking {
        val db = LedgerDatabases.open(context, userId)
        val rohan = db.personDao().insertPerson(PersonEntity(name = "Rohan", avatarColor = "#70C1FF"))

        assertTrue(AskSentReceiver.record(context, picked(rohan, 200.0), now = 5_000L, signedInUserId = { userId }))

        val ask = db.paymentAskDao().observeAll().first().single()
        assertEquals(rohan, ask.personId)
        assertEquals(200.0, ask.amount, 0.0)
        assertEquals(5_000L, ask.sentAt)
    }

    @Test
    fun `an intent that isn't an ask logs nothing`() = runBlocking {
        val db = LedgerDatabases.open(context, userId)

        assertFalse(AskSentReceiver.record(context, picked(0, 200.0), signedInUserId = { userId }))
        assertFalse(AskSentReceiver.record(context, picked(3, 0.0), signedInUserId = { userId }))
        assertFalse(AskSentReceiver.record(context, picked(3, 200.0, user = null), signedInUserId = { userId }))
        assertEquals(0, db.paymentAskDao().observeAll().first().size)
    }

    @Test
    fun `an ask picked after switching accounts leaves the other account's ledger open`() = runBlocking {
        // Codex review, 2026-10-23: opening the asking account's ledger closed the one in use.
        val other = "00000000-0000-4000-8000-0000000000b2"
        val inUse = LedgerDatabases.open(context, other)
        // Room opens lazily: a first query makes the connection real, so "still open" means something.
        assertEquals(0, inUse.paymentAskDao().observeAll().first().size)

        assertFalse(AskSentReceiver.record(context, picked(3, 200.0), signedInUserId = { other }))

        assertTrue("still open for the signed-in account", inUse.isOpen)
        assertEquals(0, inUse.paymentAskDao().observeAll().first().size)
        LedgerDatabases.close()
        context.deleteDatabase(LedgerDatabases.databaseName(other))
        Unit
    }
}
