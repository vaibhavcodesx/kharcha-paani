package com.vaibhav.kharchapaani.reminder

import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SettlementRemindersTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val day = 86_400_000L
    private val recorded = 1_800_000_000_000L

    @Test
    fun `a nudge when it arrives, then day 3, 6, 9 and on, for as long as it waits`() {
        assertEquals(0, SettlementReminders.stageFor(recorded, recorded))
        assertEquals(0, SettlementReminders.stageFor(recorded, recorded + 2 * day + 23 * 3_600_000L))
        assertEquals(1, SettlementReminders.stageFor(recorded, recorded + 3 * day))
        assertEquals(1, SettlementReminders.stageFor(recorded, recorded + 5 * day))
        assertEquals(2, SettlementReminders.stageFor(recorded, recorded + 6 * day))
        assertEquals(10, SettlementReminders.stageFor(recorded, recorded + 30 * day))
    }

    @Test
    fun `the words escalate and always say it doesn't count yet`() {
        val settlement = SettlementEntity(
            fromPersonId = 2, toPersonId = -1L, amount = 500.0, createdAt = recorded,
            status = SettlementStatus.PENDING, recordedByPersonId = 2,
        )

        val (firstTitle, firstBody) = SettlementReminders.textFor(context, settlement, "Priya", stage = 0, now = recorded)
        assertEquals("Priya says they paid you ₹500", firstTitle)
        assertTrue(firstBody.contains("doesn't count until you do"))

        val (laterTitle, laterBody) = SettlementReminders.textFor(context, settlement, "Priya", stage = 3, now = recorded + 9 * day)
        assertEquals("Still waiting on you: ₹500 with Priya", laterTitle)
        assertTrue(laterBody.contains("9 days ago"))
    }
}
