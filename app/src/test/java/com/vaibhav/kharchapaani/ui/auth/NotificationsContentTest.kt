package com.vaibhav.kharchapaani.ui.auth

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.domain.NotificationAccess
import com.vaibhav.kharchapaani.domain.NotificationMoment
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import com.vaibhav.kharchapaani.domain.QuietHours
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class NotificationsContentTest {

    @get:Rule
    val compose = createComposeRule()

    private var turnedOn = 0
    private var payments: Boolean? = null
    private var quiet: QuietHours? = null

    private fun show(access: NotificationAccess = NotificationAccess.Allowed, prefs: NotificationPrefs = NotificationPrefs()) {
        compose.setContent {
            SplitFlowTheme {
                NotificationsContent(
                    access = access,
                    prefs = prefs,
                    reminder = ReminderSettings(),
                    onTurnOn = { turnedOn++ },
                    onPaymentsToConfirmChange = { payments = it },
                    onQuietHoursChange = { quiet = it },
                    onReminderEnabledChange = {},
                    onReminderTimeChange = {},
                )
            }
        }
    }

    @Test
    fun `with notifications off, the screen says so and Turn on asks`() {
        show(access = NotificationAccess.CanAsk)

        compose.onNodeWithTag("notifications_off").assertExists()
        compose.onNodeWithText("Notifications are off").assertExists()
        compose.onNodeWithTag("notifications_turn_on").performClick()

        assertEquals(1, turnedOn)
    }

    @Test
    fun `turning payments to confirm off says what that costs`() {
        show(prefs = NotificationPrefs(paymentsToConfirm = false))

        compose.onNodeWithTag("payments_to_confirm_off_warning").assertTextContains("don't count until you answer", substring = true)
        compose.onNodeWithTag("setting_payments_to_confirm").performClick()

        assertEquals(true, payments)
    }

    @Test
    fun `quiet hours default to half past ten till eight, and can be turned off`() {
        show()

        compose.onNodeWithTag("setting_quiet_hours").assertTextContains("10:30", substring = true)
        compose.onNodeWithTag("setting_quiet_hours").performClick()

        assertEquals(false, quiet?.enabled)
    }

    @Test
    fun `the offer names what it's for, and Not now is an answer`() {
        var notNow = false
        compose.setContent {
            SplitFlowTheme {
                NotificationOfferContent(NotificationMoment.JoinedGroup, reminderTime = "9:00 PM", onTurnOn = {}, onNotNow = { notNow = true })
            }
        }

        compose.onNodeWithText("Know when a payment needs you").assertExists()
        compose.onNodeWithTag("notification_offer_not_now").performClick()

        assertTrue(notNow)
    }
}
