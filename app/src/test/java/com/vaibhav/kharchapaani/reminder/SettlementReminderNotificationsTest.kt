package com.vaibhav.kharchapaani.reminder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import com.vaibhav.kharchapaani.util.NotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Payment to confirm" notifications against a real ledger (notifications audit G3, G6): a blocked
 * channel used up a reminder stage without showing anything, and a payment answered on another
 * phone (or withdrawn) left its notification up, opening a payment that no longer waits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettlementReminderNotificationsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val userId = "11111111-2222-3333-4444-555555555555"
    private val now = 1_800_000_000_000L
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
        manager.cancelAll()
    }

    /** Priya recorded paying me ₹500 in a shared group; it waits for my answer. */
    private suspend fun priyaSaysShePaid(): Long {
        val priya = db.personDao().insertPerson(PersonEntity(name = "Priya", avatarColor = "#FF7A3D"))
        return db.groupSyncDao().insertSettlement(
            SettlementEntity(
                fromPersonId = priya, toPersonId = -1L, amount = 500.0, createdAt = now,
                status = SettlementStatus.PENDING, recordedByPersonId = priya,
            )
        )
    }


    @Test
    fun `payment details have a redacted lock screen version but keep the full private reminder`() = runBlocking {
        priyaSaysShePaid()
        assertEquals(1, SettlementReminders.remind(context, db, userId, now))

        val notification = manager.activeNotifications.single().notification
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        val privateText = notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString() +
            notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()
        assertTrue(privateText.contains("Priya"))
        assertTrue(privateText.contains("500"))
        val public = notification.publicVersion
        assertNotNull("lock screen needs a separate generic notification", public)
        assertEquals(Notification.VISIBILITY_PUBLIC, public.visibility)
        val publicText = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        ).mapNotNull { public.extras.getCharSequence(it) }.joinToString(" ")
        assertFalse(publicText.contains("Priya"))
        assertFalse(publicText.contains("500"))
        assertEquals("Kharcha Paani", public.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("A payment is waiting for your answer", public.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(notification.contentIntent, public.contentIntent)
    }

    @Test
    fun `with the payments channel blocked, nothing is shown and the reminder isn't used up`() = runBlocking {
        val id = priyaSaysShePaid()
        NotificationHelper.createChannels(context)
        manager.createNotificationChannel(
            NotificationChannel(NotificationHelper.CHANNEL_SETTLEMENTS, "Payments to confirm", NotificationManager.IMPORTANCE_NONE)
        )

        val shown = SettlementReminders.remind(context, db, userId, now)

        assertEquals(0, shown)
        val stage = db.groupSyncDao().settlementById(id)!!.remindedStage
        assertTrue("stage $stage was spent on a notification nobody saw", stage < 0)
    }

    @Test
    fun `a payment answered on another phone takes its notification down at the next sync`() = runBlocking {
        val id = priyaSaysShePaid()
        assertEquals(1, SettlementReminders.remind(context, db, userId, now))
        assertEquals(1, manager.activeNotifications.size)

        // The next pull brings my answer from my other phone.
        db.openHelper.writableDatabase.execSQL("UPDATE settlements SET status = 'CONFIRMED' WHERE id = $id")
        SettlementReminders.remind(context, db, userId, now + 60_000)

        assertEquals(0, manager.activeNotifications.size)
    }

    @Test
    fun `during quiet hours the nudge waits, and comes once they're over`() = runBlocking {
        val id = priyaSaysShePaid()
        val utc = java.time.ZoneOffset.UTC
        val elevenPm = java.time.LocalDateTime.of(2027, 1, 15, 23, 0).toInstant(utc).toEpochMilli()

        assertEquals(0, SettlementReminders.remind(context, db, userId, elevenPm, NotificationPrefs(), utc))
        assertTrue(db.groupSyncDao().settlementById(id)!!.remindedStage < 0)

        val nineAm = elevenPm + 10 * 3_600_000L
        assertEquals(1, SettlementReminders.remind(context, db, userId, nineAm, NotificationPrefs(), utc))
    }

    @Test
    fun `with payments to confirm turned off, nothing is shown or used up`() = runBlocking {
        val id = priyaSaysShePaid()

        val shown = SettlementReminders.remind(context, db, userId, now, NotificationPrefs(paymentsToConfirm = false))

        assertEquals(0, shown)
        assertTrue(db.groupSyncDao().settlementById(id)!!.remindedStage < 0)
    }
}
