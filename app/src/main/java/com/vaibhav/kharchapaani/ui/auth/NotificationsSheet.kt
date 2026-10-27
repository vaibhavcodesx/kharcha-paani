package com.vaibhav.kharchapaani.ui.auth

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.NotificationAccess
import com.vaibhav.kharchapaani.domain.NotificationMoment
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import com.vaibhav.kharchapaani.domain.QuietHours
import com.vaibhav.kharchapaani.reminder.ReminderSchedule
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import com.vaibhav.kharchapaani.ui.components.GlassActionBar
import com.vaibhav.kharchapaani.ui.components.KpModalSheet
import com.vaibhav.kharchapaani.ui.components.RowPosition
import com.vaibhav.kharchapaani.ui.components.SheetScaffold
import com.vaibhav.kharchapaani.ui.components.SheetTitle
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * Shown before Android's own notification dialog, right after something a notification would follow
 * up on (notifications plan N1). Android's dialog only says "allow notifications?"; this says what
 * they'd be for. stringResource(R.string.notification_screen_not_now) is a fine answer: everything still works, and the Notifications screen can
 * ask again later.
 */
@Composable
fun NotificationOfferSheet(
    moment: NotificationMoment,
    reminderTime: String,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
) {
    KpModalSheet(onDismiss = onNotNow) {
        NotificationOfferContent(moment, reminderTime, onTurnOn, onNotNow)
    }
}

@Composable
fun NotificationOfferContent(
    moment: NotificationMoment,
    reminderTime: String,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (title, body) = offerCopy(moment, reminderTime)
    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(label = stringResource(R.string.notification_screen_offer_on), enabled = true, onClick = onTurnOn, buttonTestTag = "notification_offer_turn_on")
        },
    ) {
        SheetTitle(title = title, supporting = body)
        TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth().testTag("notification_offer_not_now")) {
            Text(stringResource(R.string.notification_screen_not_now), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
        }
    }
}

/** What each moment's offer says. Only what the app really sends today; never a promise of more. */
@Composable
internal fun offerCopy(moment: NotificationMoment, reminderTime: String): Pair<String, String> = when (moment) {
    NotificationMoment.FirstOwnEntry ->
        stringResource(R.string.notification_screen_nudge_title, reminderTime) to
            stringResource(R.string.notification_screen_nudge_body)
    NotificationMoment.JoinedGroup, NotificationMoment.CreatedGroup ->
        stringResource(R.string.notification_screen_group_title) to
            stringResource(R.string.notification_screen_group_body)
    NotificationMoment.RecordedPayment ->
        stringResource(R.string.notification_screen_payment_title) to
            stringResource(R.string.notification_screen_payment_body)
}

/**
 * What Kharcha Paani tells you, and when: whether the phone will show anything at all (with the one
 * fix that works from here), payments to confirm, the daily reminder, and quiet hours.
 */
@Composable
fun NotificationsSheet(
    isOpen: Boolean,
    access: NotificationAccess,
    prefs: NotificationPrefs,
    reminder: ReminderSettings,
    onTurnOn: () -> Unit,
    onPaymentsToConfirmChange: (Boolean) -> Unit,
    onQuietHoursChange: (QuietHours) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (minuteOfDay: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) {
        NotificationsContent(access, prefs, reminder, onTurnOn, onPaymentsToConfirmChange, onQuietHoursChange, onReminderEnabledChange, onReminderTimeChange)
    }
}

@Composable
fun NotificationsContent(
    access: NotificationAccess,
    prefs: NotificationPrefs,
    reminder: ReminderSettings,
    onTurnOn: () -> Unit,
    onPaymentsToConfirmChange: (Boolean) -> Unit,
    onQuietHoursChange: (QuietHours) -> Unit,
    onReminderEnabledChange: (Boolean) -> Unit,
    onReminderTimeChange: (minuteOfDay: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    fun time(minute: Int) = ReminderSchedule.label(minute, is24Hour)
    fun pickTime(initial: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(context, { _, hour, minute -> onPicked(hour * 60 + minute) }, initial / 60, initial % 60, is24Hour).show()
    }
    val allowed = access == NotificationAccess.Allowed

    SheetScaffold(modifier = modifier) {
        SheetTitle(
            title = stringResource(R.string.notification_screen_title),
            supporting = if (allowed) stringResource(R.string.notification_screen_supporting) else stringResource(R.string.notification_screen_off_supporting),
        )
        if (!allowed) {
            SettingRow(
                title = stringResource(R.string.notification_screen_off),
                detail = if (access == NotificationAccess.CanAsk) stringResource(R.string.notification_screen_enable_hint) else stringResource(R.string.notification_screen_settings_hint),
                position = RowPosition.Single,
                modifier = Modifier.padding(bottom = Spacing.l).testTag("notifications_off"),
            ) {
                TextButton(onClick = onTurnOn, modifier = Modifier.testTag("notifications_turn_on")) {
                    Text(stringResource(R.string.notification_screen_on), style = MaterialTheme.typography.labelLarge.copy(color = WarmAmberPrimary, fontWeight = FontWeight.Bold))
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow(
                title = stringResource(R.string.notification_screen_payments),
                detail = stringResource(R.string.notification_screen_payments_hint),
                position = RowPosition.First,
                modifier = Modifier
                    .toggleable(value = prefs.paymentsToConfirm, role = Role.Switch, onValueChange = onPaymentsToConfirmChange)
                    .testTag("setting_payments_to_confirm"),
            ) { KpSwitch(prefs.paymentsToConfirm) }
            ReminderRows(
                reminder = reminder,
                notificationsAllowed = allowed,
                onEnabledChange = { enabled ->
                    onReminderEnabledChange(enabled)
                    // Turning it on is the moment to ask, if the phone won't show it yet.
                    if (enabled && !allowed) onTurnOn()
                },
                onTimeChange = onReminderTimeChange,
                onAllowNotifications = onTurnOn,
            )
            val quiet = prefs.quietHours
            SettingRow(
                title = stringResource(R.string.notification_screen_quiet),
                detail = if (quiet.enabled) stringResource(R.string.notification_screen_quiet_detail, time(quiet.startMinute), time(quiet.endMinute)) else stringResource(R.string.notification_screen_quiet_off),
                position = if (quiet.enabled) RowPosition.Middle else RowPosition.Last,
                modifier = Modifier
                    .toggleable(value = quiet.enabled, role = Role.Switch, onValueChange = { onQuietHoursChange(quiet.copy(enabled = it)) })
                    .testTag("setting_quiet_hours"),
            ) { KpSwitch(quiet.enabled) }
            if (quiet.enabled) {
                SettingRow(
                    title = stringResource(R.string.notification_screen_starts),
                    detail = time(quiet.startMinute),
                    position = RowPosition.Middle,
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.notification_screen_quiet_start_action)) {
                        pickTime(quiet.startMinute) { onQuietHoursChange(quiet.copy(startMinute = it)) }
                    },
                ) { Chevron() }
                SettingRow(
                    title = stringResource(R.string.notification_screen_ends),
                    detail = time(quiet.endMinute),
                    position = RowPosition.Last,
                    modifier = Modifier.clickable(onClickLabel = stringResource(R.string.notification_screen_quiet_end_action)) {
                        pickTime(quiet.endMinute) { onQuietHoursChange(quiet.copy(endMinute = it)) }
                    },
                ) { Chevron() }
            }
        }
        if (!prefs.paymentsToConfirm) {
            // D3: these can be turned off, but not without saying what that costs.
            Text(
                stringResource(R.string.notification_screen_payments_off),
                style = MaterialTheme.typography.bodySmall,
                color = WarmAmberLight,
                modifier = Modifier
                    .padding(top = Spacing.m)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .testTag("payments_to_confirm_off_warning"),
            )
        } else {
            Text(
                stringResource(R.string.notification_screen_private),
                style = MaterialTheme.typography.bodySmall,
                color = SubMutedWarmGray,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
    }
}

@Composable
private fun KpSwitch(checked: Boolean) {
    Switch(
        checked = checked,
        onCheckedChange = null,
        colors = SwitchDefaults.colors(
            checkedTrackColor = WarmAmberPrimary,
            checkedThumbColor = CharcoalBg,
            uncheckedTrackColor = CharcoalElevated,
            uncheckedThumbColor = MutedWarmGray,
            uncheckedBorderColor = OutlineInteractive,
        ),
    )
}

@Composable
private fun Chevron() {
    Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
}
