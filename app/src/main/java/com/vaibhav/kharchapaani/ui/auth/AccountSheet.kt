package com.vaibhav.kharchapaani.ui.auth

import com.vaibhav.kharchapaani.R
import androidx.compose.ui.res.stringResource
import android.app.TimePickerDialog
import android.text.format.DateUtils
import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import com.vaibhav.kharchapaani.ui.components.ExpandableDetail
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.diagnostics.CrashReporting
import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.util.LegalLinks
import com.vaibhav.kharchapaani.util.Support
import com.vaibhav.kharchapaani.data.auth.Profile
import com.vaibhav.kharchapaani.domain.ProfileEdit
import com.vaibhav.kharchapaani.reminder.ReminderSchedule
import com.vaibhav.kharchapaani.reminder.ReminderSettings
import com.vaibhav.kharchapaani.ui.components.DestructiveText
import com.vaibhav.kharchapaani.ui.components.GlassActionBar
import com.vaibhav.kharchapaani.ui.components.hueColour
import com.vaibhav.kharchapaani.ui.components.HuePicker
import com.vaibhav.kharchapaani.ui.components.KpModalSheet
import com.vaibhav.kharchapaani.ui.components.RowPosition
import com.vaibhav.kharchapaani.ui.components.SheetRow
import com.vaibhav.kharchapaani.ui.components.BrandMark
import com.vaibhav.kharchapaani.ui.components.SheetScaffold
import com.vaibhav.kharchapaani.ui.components.SheetTextInput
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/** The profile: name and emoji avatar groupmates see, the account's cloud state, and settings. */
@Composable
fun AccountSheet(
    state: AuthUiState,
    syncStatus: SyncStatus,
    askBeforeDiscarding: Boolean,
    onSaveProfile: (name: String, emoji: String?) -> Unit,
    onAskBeforeDiscardingChange: (Boolean) -> Unit,
    shareBillPhotos: Boolean = true,
    onShareBillPhotosChange: (Boolean) -> Unit = {},
    onOpenExport: () -> Unit,
    onBackUp: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRetryFailedSync: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    upiId: String? = null,
    onOpenUpi: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    reminder: ReminderSettings = ReminderSettings(),
    notificationsAllowed: Boolean = true,
    onOpenNotifications: () -> Unit = {},
    colour: ColourChoice = ColourChoice(),
    onSaveHue: (Int) -> Unit = {},
    onOpenLicences: () -> Unit = {},
    /** When the phone has no app to open a link or an email with: what to tell the person instead. */
    onNoApp: (String) -> Unit = {},
    aiEnabled: Boolean = true,
    onAiEnabledChange: (Boolean) -> Unit = {},
    isLocal: Boolean = false,
    onSignInFromLocal: () -> Unit = {},
    onUseLocal: () -> Unit = {},
) {
    // Read here rather than passed in: the setting lives with crash reporting, which starts before
    // anything else and can't wait for a view model. Null in a build with no Firebase project.
    val context = LocalContext.current
    var crashReports by remember { mutableStateOf(if (CrashReporting.isAvailable) CrashReporting.isEnabled(context) else null) }
    KpModalSheet(onDismiss = onDismiss) {
        AccountSheetContent(
            state = state,
            syncStatus = syncStatus,
            askBeforeDiscarding = askBeforeDiscarding,
            onSaveProfile = onSaveProfile,
            onAskBeforeDiscardingChange = onAskBeforeDiscardingChange,
            shareBillPhotos = shareBillPhotos,
            onShareBillPhotosChange = onShareBillPhotosChange,
            onOpenExport = onOpenExport,
            onBackUp = onBackUp,
            onRestore = onRestore,
            onRetryFailedSync = onRetryFailedSync,
            onRetryProfile = onRetryProfile,
            onSignOut = onSignOut,
            onDeleteAccount = onDeleteAccount,
            upiId = upiId,
            onOpenUpi = onOpenUpi,
            onOpenCategories = onOpenCategories,
            onOpenSubscriptions = onOpenSubscriptions,
            reminder = reminder,
            notificationsAllowed = notificationsAllowed,
            onOpenNotifications = onOpenNotifications,
            colour = colour,
            onSaveHue = onSaveHue,
            onOpenLicences = onOpenLicences,
            onNoApp = onNoApp,
            aiEnabled = aiEnabled,
            onAiEnabledChange = onAiEnabledChange,
            isLocal = isLocal,
            onSignInFromLocal = onSignInFromLocal,
            onUseLocal = onUseLocal,
            crashReports = crashReports,
            onCrashReportsChange = {
                CrashReporting.setEnabled(context, it)
                crashReports = it
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountSheetContent(
    state: AuthUiState,
    syncStatus: SyncStatus,
    askBeforeDiscarding: Boolean,
    onSaveProfile: (name: String, emoji: String?) -> Unit,
    onAskBeforeDiscardingChange: (Boolean) -> Unit,
    shareBillPhotos: Boolean = true,
    onShareBillPhotosChange: (Boolean) -> Unit = {},
    onOpenExport: () -> Unit,
    onBackUp: () -> Unit = {},
    onRestore: () -> Unit = {},
    onRetryFailedSync: () -> Unit,
    onRetryProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
    upiId: String? = null,
    onOpenUpi: () -> Unit = {},
    onOpenCategories: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    reminder: ReminderSettings = ReminderSettings(),
    notificationsAllowed: Boolean = true,
    onOpenNotifications: () -> Unit = {},
    colour: ColourChoice = ColourChoice(),
    onSaveHue: (Int) -> Unit = {},
    onOpenLicences: () -> Unit = {},
    /** When the phone has no app to open a link or an email with: what to tell the person instead. */
    onNoApp: (String) -> Unit = {},
    aiEnabled: Boolean = true,
    onAiEnabledChange: (Boolean) -> Unit = {},
    isLocal: Boolean = false,
    onSignInFromLocal: () -> Unit = {},
    onUseLocal: () -> Unit = {},
    /** Whether crash reports are on; null when this build can't send any, and the switch isn't shown. */
    crashReports: Boolean? = null,
    onCrashReportsChange: (Boolean) -> Unit = {},
) {
    val saved =(state.profile as? ProfileState.Loaded)?.profile
    // Keyed on what's saved, so a successful save (or a reload) resets the fields to it.
    var name by rememberSaveable(saved?.displayName) { mutableStateOf(saved?.displayName.orEmpty()) }
    var emoji by rememberSaveable(saved?.avatarEmoji) { mutableStateOf(saved?.avatarEmoji) }
    var avatarOpen by rememberSaveable { mutableStateOf(false) }
    val cleanName = ProfileEdit.cleanName(name)
    val changed = saved != null && ProfileEdit.hasChanges(saved.displayName, saved.avatarEmoji, name, emoji)
    val previewAvatar = emoji ?: (cleanName ?: state.email)?.firstOrNull()?.uppercase() ?: "?"

    SheetScaffold(
        modifier = modifier,
        // The bar only appears once there's something to save.
        actionBar = if (!isLocal && (changed || state.isSavingProfile)) {
            {
                GlassActionBar(
                    label = "Save profile",
                    disabledLabel = when {
                        state.isSavingProfile -> "Saving…"
                        name.isBlank() -> "Add your name"
                        else -> "Name is too long"
                    },
                    enabled = cleanName != null && !state.isSavingProfile,
                    onClick = { onSaveProfile(name, emoji) },
                    buttonTestTag = "profile_save_button",
                )
            }
        } else {
            null
        },
    ) {
        // No brand mark appeared anywhere in the app, which is a real part of why a settings page
        // reads as generic (backlog item 19).
        BrandMark(Modifier.padding(bottom = Spacing.l))
        if (isLocal) {
            Eyebrow(stringResource(R.string.guest_on_this_phone))
            Text(
                stringResource(R.string.guest_local_explanation),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.m).testTag("account_local_explanation"),
            )
            OutlinedButton(
                onClick = onSignInFromLocal,
                enabled = !state.isSigningIn,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("account_local_sign_in"),
            ) {
                Text(stringResource(if (state.isSigningIn) R.string.guest_signing_in else R.string.guest_sign_in))
            }
            Text(
                stringResource(R.string.guest_separate_ledgers),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xl),
            )
        } else {
            // Your own colour once you have one (design item 11): the same one groupmates see you in.
            val mine = state.hue?.let(::hueColour)
            val avatarDescription = stringResource(R.string.account_avatar_content_description)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xl)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background((mine ?: WarmAmberPrimary).copy(alpha = 0.18f))
                        // The brand gradient as a ring. It is the app's one distinctive mark and it
                        // was used only as a fill — on the add button and the hero figure — never as
                        // an edge. This is the one avatar in the app that is *yours*, which is the
                        // right place for it and the reason it goes nowhere else.
                        .then(if (mine != null) Modifier.border(2.dp, mine, CircleShape) else Modifier.border(2.dp, BrandGradient.diagonal(), CircleShape))
                        .semantics { contentDescription = avatarDescription },
                ) {
                    Text(
                        text = previewAvatar,
                        fontSize = if (emoji != null) 34.sp else 28.sp,
                        color = mine ?: WarmAmberLight,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                    Text(
                        text = cleanName ?: state.displayName ?: stringResource(R.string.account_title_fallback),
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    state.email?.let {
                        Text(text = it, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Eyebrow(stringResource(R.string.account_eyebrow_profile))
            if (saved == null) {
                Text(
                    stringResource(R.string.account_profile_unreachable),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = Spacing.m),
                )
            } else {
                // One connected group, like the settings below: three rows that all describe you,
                // with the helper line once, under them, instead of between the first two.
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SheetRow(label = stringResource(R.string.account_name_label), position = RowPosition.First) {
                        SheetTextInput(value = name, onValueChange = { name = it }, placeholder = stringResource(R.string.account_name_placeholder), fieldTestTag = "profile_name_input")
                    }
                    // Collapsed until wanted: open, the grid would push everything else off the sheet.
                    Box(Modifier.testTag("avatar_row")) {
                        ExpandableDetail(
                            label = stringResource(R.string.account_avatar_label),
                            value = emoji ?: stringResource(R.string.account_avatar_initials),
                            expanded = avatarOpen,
                            onToggle = { avatarOpen = !avatarOpen },
                            position = RowPosition.Middle,
                        ) {
                            AvatarGrid(
                                initial = (cleanName ?: state.email)?.firstOrNull()?.uppercase() ?: "?",
                                selected = emoji,
                                onSelect = { emoji = it },
                            )
                        }
                    }
                    ColourRow(colour = colour, saved = state.hue, onSave = onSaveHue, error = state.hueSaveError)
                }
                Text(
                    text = when {
                        state.profileSaveFailed -> stringResource(R.string.account_profile_save_failed)
                        name.isNotBlank() && cleanName == null -> stringResource(R.string.account_name_too_long, ProfileEdit.MAX_NAME_LENGTH)
                        else -> stringResource(R.string.account_name_helper)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.profileSaveFailed || (name.isNotBlank() && cleanName == null)) WarmAmberLight else SubMutedWarmGray,
                    modifier = Modifier
                        .padding(top = Spacing.s)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
                Spacer(Modifier.height(Spacing.xl))
            }

            Eyebrow(stringResource(R.string.account_eyebrow_account))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                CloudStatusRow(
                    profile = state.profile,
                    isRefreshPending = (state.authState as? AuthState.SignedIn)?.isRefreshPending == true,
                    onRetry = onRetryProfile,
                    position = RowPosition.First,
                )
                SyncStatusRow(status = syncStatus, onRetryFailed = onRetryFailedSync, position = RowPosition.Last)
            }
            Text(
                // No version history here (owner, 2026-11-06): the few records from before 1.3 that stay on
                // the phone are covered by Export everything below, which every phone has.
                text = stringResource(R.string.account_backup_explanation),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xl),
            )

            TextButton(
                onClick = onUseLocal,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account_use_local"),
            ) { Text(stringResource(R.string.guest_use_local)) }
            Text(stringResource(R.string.guest_separate_ledgers), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = Spacing.xl))
        }

        Eyebrow(stringResource(R.string.account_eyebrow_settings))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow(
                title = stringResource(R.string.account_setting_upi_title),
                detail = upiId ?: stringResource(R.string.account_setting_upi_empty),
                position = RowPosition.First,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_setting_upi_click_label), onClick = onOpenUpi).testTag("setting_upi"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = stringResource(R.string.account_setting_subscriptions_title),
                detail = stringResource(R.string.account_setting_subscriptions_detail),
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_setting_subscriptions_click_label), onClick = onOpenSubscriptions).testTag("setting_subscriptions"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = stringResource(R.string.account_setting_categories_title),
                detail = stringResource(R.string.account_setting_categories_detail),
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_setting_categories_click_label), onClick = onOpenCategories).testTag("setting_categories"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = stringResource(R.string.account_setting_discard_prompt_title),
                detail = stringResource(R.string.account_setting_discard_prompt_detail),
                position = RowPosition.Middle,
                modifier = Modifier
                    .toggleable(value = askBeforeDiscarding, role = Role.Switch, onValueChange = onAskBeforeDiscardingChange)
                    .testTag("setting_ask_before_discarding"),
            ) {
                Switch(
                    checked = askBeforeDiscarding,
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
            SettingRow(
                title = stringResource(R.string.bill_photo_share_title),
                detail = stringResource(R.string.bill_photo_share_detail),
                position = RowPosition.Middle,
                modifier = Modifier
                    .toggleable(value = shareBillPhotos, role = Role.Switch, onValueChange = onShareBillPhotosChange)
                    .testTag("setting_share_bill_photos"),
            ) {
                Switch(
                    checked = shareBillPhotos,
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
            SettingRow(
                title = stringResource(R.string.ai_choice_title),
                detail = stringResource(if (isLocal) R.string.guest_ai_needs_account else if (aiEnabled) R.string.ai_choice_enabled_detail else R.string.ai_choice_disabled_detail),
                position = RowPosition.Middle,
                modifier = Modifier.toggleable(value = aiEnabled, role = Role.Switch, onValueChange = onAiEnabledChange)
                    .testTag("setting_ai_enabled"),
            ) {
                Switch(checked = aiEnabled, onCheckedChange = null, colors = SwitchDefaults.colors(
                    checkedTrackColor = WarmAmberPrimary, checkedThumbColor = CharcoalBg,
                    uncheckedTrackColor = CharcoalElevated, uncheckedThumbColor = MutedWarmGray,
                    uncheckedBorderColor = OutlineInteractive,
                ))
            }
            val reminderTime = ReminderSchedule.label(reminder.minuteOfDay, android.text.format.DateFormat.is24HourFormat(LocalContext.current))
            SettingRow(
                title = stringResource(R.string.account_setting_notifications_title),
                detail = when {
                    !notificationsAllowed -> stringResource(R.string.account_setting_notifications_off)
                    reminder.enabled -> stringResource(R.string.account_setting_notifications_reminder_and_payments, reminderTime)
                    else -> stringResource(R.string.account_setting_notifications_payments_only)
                },
                position = RowPosition.Last,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_setting_notifications_click_label), onClick = onOpenNotifications).testTag("setting_notifications"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
        }

        // What leaves or comes back to this phone, apart from the switches above: these are
        // actions you run now and then, not settings.
        Spacer(Modifier.height(Spacing.xl))
        Eyebrow(stringResource(R.string.account_eyebrow_data))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow(
                title = stringResource(R.string.account_setting_export_title),
                detail = stringResource(R.string.account_setting_export_detail),
                position = RowPosition.First,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_setting_export_click_label), onClick = onOpenExport).testTag("setting_export"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            // Deliberately worded as insurance rather than a chore. A normal update keeps all of
            // this — tested by installing one release over another and watching a local friend, a
            // subscription and a custom category come through. What loses it is uninstalling, a
            // new phone, or a change of signing key.
            SettingRow(
                title = stringResource(R.string.full_backup_ui_export_title),
                detail = stringResource(R.string.full_backup_ui_export_detail),
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.full_backup_ui_export_title), onClick = onBackUp).testTag("setting_backup"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = stringResource(R.string.full_backup_ui_restore_title),
                detail = stringResource(R.string.full_backup_ui_restore_detail),
                position = RowPosition.Last,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.full_backup_ui_restore_title), onClick = onRestore).testTag("setting_restore"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
        }

        HelpAndAbout(
            onOpenLicences = onOpenLicences,
            onNoApp = onNoApp,
            crashReports = crashReports,
            onCrashReportsChange = onCrashReportsChange,
            modifier = Modifier.padding(top = Spacing.xl),
        )

        if (!isLocal) {
            OutlinedButton(
                onClick = onSignOut,
                shape = KpShapes.medium,
                // SubMutedWarmGray, not CharcoalBorder: the boundary needs 3:1 against the sheet (3.17 vs 1.28).
                border = BorderStroke(1.dp, SubMutedWarmGray),
                modifier = Modifier
                    .padding(top = Spacing.xl)
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("account_sign_out"),
            ) {
                Text(text = stringResource(R.string.shell_copy_ledger_sign_out), style = MaterialTheme.typography.titleMedium.copy(color = OffWhiteText))
            }
            // Play requires deleting an account to be easy to find in the app (plan T1.1).
            TextButton(
                onClick = onDeleteAccount,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("account_delete"),
            ) {
                Text(stringResource(R.string.delete_account_action_delete), style = MaterialTheme.typography.labelLarge, color = DestructiveText)
            }
        }
    }
}

/**
 * Help & about (plan T1.2, REL H5): a way to write to us with the details a report needs, the
 * policy and terms the sign-in screen agreed to, the licences, and which version this is.
 */
@Composable
private fun HelpAndAbout(
    onOpenLicences: () -> Unit,
    onNoApp: (String) -> Unit,
    crashReports: Boolean?,
    onCrashReportsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun openPage(url: String) {
        if (!LegalLinks.open(context, url)) onNoApp(context.getString(R.string.account_help_no_browser, url))
    }
    Column(modifier) {
        Eyebrow(stringResource(R.string.account_eyebrow_help))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow(
                title = stringResource(R.string.account_help_feedback_title),
                detail = stringResource(R.string.account_help_feedback_detail),
                position = RowPosition.First,
                modifier = Modifier
                    .clickable(onClickLabel = stringResource(R.string.account_help_feedback_click_label)) {
                        if (!Support.email(context, subject = context.getString(R.string.account_help_feedback_subject), body = "")) {
                            onNoApp(context.getString(R.string.account_help_no_email, Support.EMAIL))
                        }
                    }
                    .testTag("help_feedback"),
            ) { HelpChevron() }
            SettingRow(
                title = stringResource(R.string.account_help_privacy_title),
                detail = stringResource(R.string.account_help_privacy_detail),
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_help_privacy_click_label)) { openPage(LegalLinks.PRIVACY_POLICY) }.testTag("help_privacy"),
            ) { HelpChevron() }
            SettingRow(
                title = stringResource(R.string.account_help_terms_title),
                detail = stringResource(R.string.account_help_terms_detail),
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_help_terms_click_label)) { openPage(LegalLinks.TERMS) }.testTag("help_terms"),
            ) { HelpChevron() }
            if (crashReports != null) {
                // On unless turned off, as the privacy policy says.
                SettingRow(
                    title = stringResource(R.string.account_help_crash_title),
                    detail = stringResource(R.string.account_help_crash_detail),
                    position = RowPosition.Middle,
                    modifier = Modifier
                        .toggleable(value = crashReports, role = Role.Switch, onValueChange = onCrashReportsChange)
                        .testTag("help_crash_reports"),
                ) {
                    Switch(
                        checked = crashReports,
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
            }
            SettingRow(
                title = stringResource(R.string.account_help_licences_title),
                detail = stringResource(R.string.account_help_licences_detail),
                position = RowPosition.Last,
                modifier = Modifier.clickable(onClickLabel = stringResource(R.string.account_help_licences_click_label), onClick = onOpenLicences).testTag("help_licences"),
            ) { HelpChevron() }
        }
        Text(
            stringResource(R.string.account_help_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            style = MaterialTheme.typography.bodySmall,
            color = SubMutedWarmGray,
            modifier = Modifier.padding(top = Spacing.s).testTag("help_version"),
        )
    }
}

@Composable
private fun HelpChevron() {
    Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
}

@Composable
private fun Eyebrow(text: String) {
    Text(text.uppercase(), style = EyebrowText, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
}

/** Initials, then every offered emoji, six to a row with the tiles sharing the width evenly. */
/** What the colour row needs to know about everyone else (design item 11). */
data class ColourChoice(
    /** Hues of everyone this account shares a group with: the picker keeps clear of them. */
    val taken: List<Int> = emptyList(),
    /** A groupmate whose colour is too close to this account's (after joining a group), if any. */
    val clashWith: String? = null,
)

/**
 * This account's one colour, the same on every groupmate's phone. Collapsed to a swatch until
 * opened; a clash after joining a group opens it with a note saying whose colour is too close.
 */
@Composable
private fun ColourRow(colour: ColourChoice, saved: Int?, onSave: (Int) -> Unit, error: String?) {
    var open by rememberSaveable { mutableStateOf(colour.clashWith != null) }
    var picked by rememberSaveable(saved) { mutableStateOf(saved) }
    Column(Modifier.testTag("colour_row")) {
        ExpandableDetail(
            label = stringResource(R.string.account_colour_label),
            value = if (saved == null) stringResource(R.string.account_colour_choosing) else stringResource(R.string.account_colour_yours),
            expanded = open,
            onToggle = { open = !open },
            position = RowPosition.Last,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.m)) {
                    Box(Modifier.size(28.dp).clip(KpShapes.small).background(picked?.let(::hueColour) ?: CharcoalElevated))
                    Text(
                        stringResource(R.string.account_colour_wear_description),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Spacing.m),
                    )
                }
                HuePicker(hue = picked, taken = colour.taken, onHueChange = { picked = it })
                val current = picked
                if (current != null && current != saved) {
                    TextButton(onClick = { onSave(current) }, modifier = Modifier.testTag("colour_save")) {
                        Text(stringResource(R.string.account_colour_save), style = MaterialTheme.typography.labelLarge.copy(color = WarmAmberPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        val note = error ?: colour.clashWith?.let { stringResource(R.string.account_colour_clash, it) }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = WarmAmberLight, modifier = Modifier.padding(top = Spacing.s).testTag("colour_note"))
        }
    }
}

@Composable
private fun AvatarGrid(initial: String, selected: String?, onSelect: (String?) -> Unit) {
    val choices: List<String?> = listOf(null) + ProfileEdit.avatarEmoji
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        choices.chunked(AVATAR_COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                row.forEach { choice ->
                    AvatarTile(
                        label = choice ?: "Initials",
                        selected = selected == choice,
                        onSelect = { onSelect(choice) },
                        testTag = choice?.let { "avatar_$it" } ?: "avatar_initials",
                        modifier = Modifier.weight(1f),
                    ) {
                        if (choice == null) {
                            Text(initial, style = MaterialTheme.typography.titleMedium, color = WarmAmberLight)
                        } else {
                            Text(choice, fontSize = 22.sp)
                        }
                    }
                }
                repeat(AVATAR_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val AVATAR_COLUMNS = 6

@Composable
private fun AvatarTile(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = SquircleShape(15.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) WarmAmberPrimary.copy(alpha = 0.18f) else CharcoalElevated)
            .border(if (selected) 2.dp else 0.dp, if (selected) WarmAmberPrimary else CharcoalElevated, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics { contentDescription = label }
            .testTag(testTag),
    ) {
        content()
    }
}

@Composable
internal fun SettingRow(
    title: String,
    detail: String,
    position: RowPosition,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .then(modifier)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = OffWhiteText)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}

/**
 * The daily reminder: a switch, and when it's on, the time. When the phone won't show notifications
 * the row says so and offers to fix it — a reminder that's "on" but can never appear would be the
 * kind of silent failure this app has been removing everywhere else.
 */
@Composable
internal fun ReminderRows(
    reminder: ReminderSettings,
    notificationsAllowed: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (minuteOfDay: Int) -> Unit,
    onAllowNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val is24Hour = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val time = ReminderSchedule.label(reminder.minuteOfDay, is24Hour)
    SettingRow(
        title = stringResource(R.string.account_setting_daily_reminder_title),
        detail = when {
            !reminder.enabled -> stringResource(R.string.account_setting_daily_reminder_off)
            !notificationsAllowed -> stringResource(R.string.account_setting_daily_reminder_blocked)
            else -> stringResource(R.string.account_setting_daily_reminder_scheduled, time)
        },
        position = RowPosition.Middle,
        modifier = Modifier
            .toggleable(value = reminder.enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .testTag("setting_reminder"),
    ) {
        if (reminder.enabled && !notificationsAllowed) {
            TextButton(onClick = onAllowNotifications, modifier = Modifier.testTag("setting_reminder_allow")) {
                Text(
                    stringResource(R.string.account_setting_daily_reminder_allow),
                    style = MaterialTheme.typography.labelLarge.copy(color = WarmAmberPrimary, fontWeight = FontWeight.Bold),
                )
            }
        }
        Switch(
            checked = reminder.enabled,
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
    if (reminder.enabled) {
        SettingRow(
            title = stringResource(R.string.account_setting_reminder_time_title),
            detail = time,
            position = RowPosition.Middle,
            modifier = Modifier
                .clickable(onClickLabel = stringResource(R.string.account_setting_reminder_time_click_label)) {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onTimeChange(hour * 60 + minute) },
                        reminder.minuteOfDay / 60,
                        reminder.minuteOfDay % 60,
                        is24Hour,
                    ).show()
                }
                .testTag("setting_reminder_time"),
        ) {
            Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
        }
    }
}

@Composable
private fun CloudStatusRow(
    profile: ProfileState,
    isRefreshPending: Boolean,
    onRetry: () -> Unit,
    position: RowPosition = RowPosition.Single,
) {
    val unreachable = isRefreshPending || profile == ProfileState.Unavailable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(position.shape)
            .background(CharcoalCard)
            .padding(start = 14.dp, end = 4.dp)
            .heightIn(min = 52.dp)
            .testTag("account_cloud_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            unreachable -> StatusIcon(Icons.Outlined.CloudOff, MutedWarmGray)
            profile == ProfileState.Loading || profile == ProfileState.Idle -> CircularProgressIndicator(
                color = WarmAmberPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            profile is ProfileState.Loaded -> StatusIcon(Icons.Outlined.CheckCircle, ChartreuseSecondary)
            else -> StatusIcon(Icons.Outlined.ErrorOutline, WarmAmberLight)
        }
        Text(
            text = when {
                isRefreshPending -> stringResource(R.string.account_cloud_offline)
                profile == ProfileState.Unavailable -> stringResource(R.string.account_cloud_unavailable)
                profile is ProfileState.Loaded -> stringResource(R.string.account_cloud_ready)
                profile == ProfileState.Missing -> stringResource(R.string.account_cloud_missing)
                else -> stringResource(R.string.account_cloud_checking)
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (profile == ProfileState.Unavailable || profile == ProfileState.Missing) {
            TextButton(onClick = onRetry) {
                Text(
                    text = stringResource(R.string.account_retry),
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = WarmAmberPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SyncStatusRow(
    status: SyncStatus,
    onRetryFailed: () -> Unit,
    position: RowPosition = RowPosition.Single,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(position.shape)
            .background(CharcoalCard)
            .padding(start = 14.dp, end = 4.dp)
            .heightIn(min = 52.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("account_sync_status"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            status.failedChanges > 0 -> StatusIcon(Icons.Outlined.ErrorOutline, WarmAmberLight)
            status.isSyncing -> CircularProgressIndicator(
                color = WarmAmberPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
            status.unsyncedChanges > 0 -> StatusIcon(Icons.Outlined.CloudUpload, MutedWarmGray)
            status.lastSyncedAt != null -> StatusIcon(Icons.Outlined.CloudDone, ChartreuseSecondary)
            else -> StatusIcon(Icons.Outlined.CloudQueue, MutedWarmGray)
        }
        Text(
            text = syncStatusText(context.resources, status, now = System.currentTimeMillis()) { lastSyncedAt ->
                DateUtils.getRelativeTimeSpanString(
                    lastSyncedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                ).toString()
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (status.failedChanges > 0) {
            TextButton(onClick = onRetryFailed) {
                Text(
                    text = stringResource(R.string.account_retry),
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = WarmAmberPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

internal fun syncStatusText(
    resources: Resources,
    status: SyncStatus,
    now: Long,
    relativeTime: (Long) -> String,
): String = when {
    status.failedChanges == 1 -> resources.getQuantityString(R.plurals.account_sync_failed, 1, 1)
    status.failedChanges > 1 -> resources.getQuantityString(R.plurals.account_sync_failed, status.failedChanges, status.failedChanges)
    status.isSyncing -> resources.getString(R.string.account_sync_syncing)
    status.unsyncedChanges == 1 -> resources.getQuantityString(R.plurals.account_sync_unsynced, 1, 1)
    status.unsyncedChanges > 1 -> resources.getQuantityString(R.plurals.account_sync_unsynced, status.unsyncedChanges, status.unsyncedChanges)
    status.lastSyncedAt != null && now - status.lastSyncedAt < JUST_NOW_MILLIS -> resources.getString(R.string.account_sync_synced_just_now)
    status.lastSyncedAt != null -> resources.getString(R.string.account_sync_synced_relative, relativeTime(status.lastSyncedAt))
    else -> resources.getString(R.string.account_sync_not_synced)
}

private const val JUST_NOW_MILLIS = 60_000L

@Composable
private fun StatusIcon(imageVector: ImageVector, tint: Color) {
    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(20.dp),
    )
}

@Preview
@Composable
private fun AccountSheetContentPreview() {
    SplitFlowTheme {
        Box(Modifier.background(CharcoalSurface)) {
            AccountSheetContent(
                state = AuthUiState(
                    authState = AuthState.SignedIn(userId = "u1", email = "vaibhav@example.com", isRefreshPending = false),
                    profile = ProfileState.Loaded(Profile(id = "u1", displayName = "Vaibhav", avatarEmoji = "🦊")),
                ),
                syncStatus = SyncStatus(lastSyncedAt = System.currentTimeMillis()),
                askBeforeDiscarding = true,
                onSaveProfile = { _, _ -> },
                onAskBeforeDiscardingChange = {},
                onOpenExport = {},
                onRetryFailedSync = {},
                onRetryProfile = {},
                onSignOut = {},
            )
        }
    }
}
