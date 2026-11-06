package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.EverythingSettings
import com.vaibhav.kharchapaani.domain.UpiPayee
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EverythingSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = AppSettings(context)
    private val localKey = "local-" + java.util.UUID.randomUUID()
    private val otherKey = java.util.UUID.randomUUID().toString()
    private var before: EverythingSettings? = null
    private var consent: Boolean? = null

    @After fun clean(): Unit = runBlocking {
        before?.let { settings.restoreEverythingSettings(localKey, it) }
        consent?.let { settings.setAiNoticeAccepted(it) }
        settings.clearAccount(localKey)
        settings.clearAccount(otherKey)
    }

    @Test fun `full settings restore preserves defaults and account isolation without copying consent`(): Unit = runBlocking {
        before = settings.collectEverythingSettings(localKey)
        consent = settings.aiNoticeAccepted.first()
        settings.setAiNoticeAccepted(false)
        val permissionAsks = settings.notificationPermissionAsks.first()
        val offers = settings.notificationOffersShown.first()
        settings.updateCategoryPrefs(otherKey) { CategoryPrefs(customExpense = listOf("Other ledger category")) }
        settings.setUpiPayee(otherKey, UpiPayee("other@upi", "Other ledger"))
        val saved = EverythingSettings(customExpenseCategories = listOf("Food at work"),
            customIncomeCategories = listOf("Tutoring"), hiddenCategories = listOf("Shopping"),
            customPaymentMethods = listOf("Saved card"), hiddenPaymentMethods = listOf("Cash"),
            defaultCategory = "Food at work", defaultPaymentMethod = "Saved card",
            askBeforeDiscarding = false, reminderEnabled = false, reminderMinute = 600,
            paymentsToConfirm = false, quietHoursEnabled = false, quietHoursStart = 120, quietHoursEnd = 540,
            upiId = "sample@upi", upiName = "Sample")
        settings.restoreEverythingSettings(localKey, saved)
        assertEquals(saved, settings.collectEverythingSettings(localKey))
        assertEquals("Food at work", settings.categoryPrefs(localKey).first().defaultCategory)
        assertEquals("Saved card", settings.categoryPrefs(localKey).first().defaultPaymentMethod)
        assertEquals(listOf("Other ledger category"), settings.categoryPrefs(otherKey).first().customExpense)
        assertEquals(UpiPayee("other@upi", "Other ledger"), settings.upiPayee(otherKey).first())
        assertFalse(settings.aiNoticeAccepted.first())
        assertEquals(permissionAsks, settings.notificationPermissionAsks.first())
        assertEquals(offers, settings.notificationOffersShown.first())
        settings.restoreEverythingSettings(localKey, saved)
        assertEquals("Recovery can apply preferences twice safely", saved, settings.collectEverythingSettings(localKey))
        try { settings.restoreEverythingSettings(localKey, saved.copy(reminderMinute = -1)); fail("Invalid time accepted") }
        catch (expected: IllegalArgumentException) { assertEquals(saved, settings.collectEverythingSettings(localKey)) }
    }
}
