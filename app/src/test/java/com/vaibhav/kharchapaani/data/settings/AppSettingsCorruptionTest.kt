package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.domain.AiUsage
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.QuietHours
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.util.UUID

/**
 * Verifies that corrupted preferences files recover cleanly without crashing
 * the app (reliability audit item L14).
 *
 * Each test owns an isolated DataStore file and CoroutineScope that is explicitly
 * cancelled and joined on completion to guarantee lifecycle isolation without
 * modifying static singletons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppSettingsCorruptionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun withTestStore(
        useProductionHandler: Boolean = true,
        block: suspend (DataStore<Preferences>, File) -> Unit,
    ) = runBlocking {
        val testFile = File(context.filesDir, "datastore/test_${UUID.randomUUID()}.preferences_pb").apply {
            parentFile?.mkdirs()
        }
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = if (useProductionHandler) AppSettings.createCorruptionHandler() else null,
            scope = scope,
            produceFile = { testFile },
        )
        try {
            block(store, testFile)
        } finally {
            job.cancel()
            job.join()
            if (testFile.exists()) {
                testFile.delete()
            }
        }
    }

    @Test
    fun `baseline verification - unhandled corrupted preferences file throws CorruptionException`() = withTestStore(useProductionHandler = false) { unhandledStore, testFile ->
        testFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertTrue(testFile.exists())

        try {
            unhandledStore.data.first()
            fail("Expected CorruptionException when corruptionHandler is null")
        } catch (e: Exception) {
            assertTrue(
                "Expected CorruptionException, but caught ${e::class.qualifiedName}: ${e.message}",
                e is CorruptionException,
            )
        }
    }

    @Test
    fun `corrupted preferences file does not throw and recovers defaults cleanly`() = withTestStore { store, testFile ->
        // 1. Seed invalid non-protobuf bytes to simulate file corruption on disk
        testFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        assertTrue("Corrupted file must exist on disk before read", testFile.exists())

        // 2. Initialize AppSettings via injected store and verify no CorruptionException is thrown on reading flows
        val settings = AppSettings(store)

        // askBeforeDiscarding defaults to true
        assertEquals(true, settings.askBeforeDiscarding.first())

        // reminder defaults: enabled = true, minuteOfDay = 21 * 60 (9 PM)
        val reminder = settings.reminder.first()
        assertTrue(reminder.enabled)
        assertEquals(ReminderSettings.DEFAULT_MINUTE_OF_DAY, reminder.minuteOfDay)

        // aiNoticeAccepted defaults to false
        assertFalse(settings.aiNoticeAccepted.first())

        // notification counters default to 0
        assertEquals(0, settings.notificationPermissionAsks.first())
        assertEquals(0, settings.notificationOffersShown.first())

        // notificationPrefs defaults
        val notifPrefs = settings.notificationPrefs.first()
        assertTrue(notifPrefs.paymentsToConfirm)
        assertTrue(notifPrefs.quietHours.enabled)
        assertEquals(QuietHours.DEFAULT_START, notifPrefs.quietHours.startMinute)
        assertEquals(QuietHours.DEFAULT_END, notifPrefs.quietHours.endMinute)

        // upiPayee defaults to null
        assertNull(settings.upiPayee("user_test").first())

        // categoryPrefs defaults to empty preferences
        assertEquals(CategoryPrefs(), settings.categoryPrefs("user_test").first())

        // aiUsage defaults to AiUsage.none
        assertEquals(AiUsage.none, settings.aiUsage.first())
    }

    @Test
    fun `preferences can be written and read back normally after corruption recovery`() = withTestStore { store, testFile ->
        // 1. Seed corruption
        testFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte()))

        val settings = AppSettings(store)

        // Recover from corruption by reading a flow
        assertEquals(true, settings.askBeforeDiscarding.first())

        // 2. Write new values across multiple data types
        settings.setAskBeforeDiscarding(false)
        settings.setAiNoticeAccepted(true)
        settings.setReminderEnabled(false)
        settings.setReminderMinute(19 * 60 + 30)
        settings.recordNotificationPermissionAsk()
        settings.recordNotificationOffer()
        settings.setPaymentsToConfirm(false)
        settings.setQuietHours(QuietHours(enabled = false, startMinute = 23 * 60, endMinute = 7 * 60))
        settings.setUpiPayee("alice", UpiPayee(upiId = "alice@upi", name = "Alice"))
        settings.updateCategoryPrefs("alice") { it.copy(defaultCategory = "Food") }
        val testDay = LocalDate.of(2026, 11, 4)
        settings.recordAiAsk(testDay)

        // 3. Verify all written values are read back correctly
        assertEquals(false, settings.askBeforeDiscarding.first())
        assertEquals(true, settings.aiNoticeAccepted.first())

        val reminder = settings.reminder.first()
        assertFalse(reminder.enabled)
        assertEquals(19 * 60 + 30, reminder.minuteOfDay)

        assertEquals(1, settings.notificationPermissionAsks.first())
        assertEquals(1, settings.notificationOffersShown.first())

        val notifPrefs = settings.notificationPrefs.first()
        assertFalse(notifPrefs.paymentsToConfirm)
        assertFalse(notifPrefs.quietHours.enabled)
        assertEquals(23 * 60, notifPrefs.quietHours.startMinute)
        assertEquals(7 * 60, notifPrefs.quietHours.endMinute)

        val payee = settings.upiPayee("alice").first()
        assertNotNull(payee)
        assertEquals("alice@upi", payee?.upiId)
        assertEquals("Alice", payee?.name)

        assertEquals("Food", settings.categoryPrefs("alice").first().defaultCategory)

        val usage = settings.aiUsage.first()
        assertEquals(testDay, usage.day)
        assertEquals(1, usage.used)
    }

    @Test
    fun `clearAccount removes account specific preferences after corruption recovery`() = withTestStore { store, testFile ->
        testFile.writeBytes(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))

        val settings = AppSettings(store)
        settings.setUpiPayee("charlie", UpiPayee("charlie@upi", "Charlie"))
        settings.updateCategoryPrefs("charlie") { it.copy(defaultCategory = "Transport") }

        assertEquals("charlie@upi", settings.upiPayee("charlie").first()?.upiId)
        assertEquals("Transport", settings.categoryPrefs("charlie").first().defaultCategory)

        settings.clearAccount("charlie")

        assertNull(settings.upiPayee("charlie").first())
        assertNull(settings.categoryPrefs("charlie").first().defaultCategory)
    }
}
