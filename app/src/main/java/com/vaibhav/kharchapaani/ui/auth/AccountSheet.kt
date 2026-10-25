package com.vaibhav.kharchapaani.ui.auth

import android.app.TimePickerDialog
import android.text.format.DateUtils
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
import com.vaibhav.kharchapaani.data.auth.AuthState
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
    onReminderEnabledChange: (Boolean) -> Unit = {},
    onReminderTimeChange: (minuteOfDay: Int) -> Unit = {},
    onAllowNotifications: () -> Unit = {},
    colour: ColourChoice = ColourChoice(),
    onSaveHue: (Int) -> Unit = {},
) {
    KpModalSheet(onDismiss = onDismiss) {
        AccountSheetContent(
            state = state,
            syncStatus = syncStatus,
            askBeforeDiscarding = askBeforeDiscarding,
            onSaveProfile = onSaveProfile,
            onAskBeforeDiscardingChange = onAskBeforeDiscardingChange,
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
            onReminderEnabledChange = onReminderEnabledChange,
            onReminderTimeChange = onReminderTimeChange,
            onAllowNotifications = onAllowNotifications,
            colour = colour,
            onSaveHue = onSaveHue,
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
    onReminderEnabledChange: (Boolean) -> Unit = {},
    onReminderTimeChange: (minuteOfDay: Int) -> Unit = {},
    onAllowNotifications: () -> Unit = {},
    colour: ColourChoice = ColourChoice(),
    onSaveHue: (Int) -> Unit = {},
) {
    val saved = (state.profile as? ProfileState.Loaded)?.profile
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
        actionBar = if (changed || state.isSavingProfile) {
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
        // Your own colour once you have one (design item 11): the same one groupmates see you in.
        val mine = state.hue?.let(::hueColour)
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
                    .semantics { contentDescription = "Your avatar" },
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
                    text = cleanName ?: state.displayName ?: "Your account",
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

        Eyebrow("Profile")
        if (saved == null) {
            Text(
                "Your name and avatar can be changed once your cloud profile is reachable.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = Spacing.m),
            )
        } else {
            SheetRow(label = "Name", position = RowPosition.Single) {
                SheetTextInput(value = name, onValueChange = { name = it }, placeholder = "Your name", fieldTestTag = "profile_name_input")
            }
            Text(
                text = when {
                    state.profileSaveFailed -> "Couldn't save your profile. Check your connection and try again."
                    name.isNotBlank() && cleanName == null -> "Keep it to ${ProfileEdit.MAX_NAME_LENGTH} characters."
                    else -> "Groupmates see your name and avatar."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.profileSaveFailed || (name.isNotBlank() && cleanName == null)) WarmAmberLight else SubMutedWarmGray,
                modifier = Modifier
                    .padding(top = Spacing.s, bottom = Spacing.l)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )

            // Collapsed until wanted: open, the grid would push everything else off the sheet.
            Box(Modifier.testTag("avatar_row")) {
                ExpandableDetail(
                    label = "Avatar",
                    value = emoji ?: "Initials",
                    expanded = avatarOpen,
                    onToggle = { avatarOpen = !avatarOpen },
                    position = RowPosition.Single,
                ) {
                    AvatarGrid(
                        initial = (cleanName ?: state.email)?.firstOrNull()?.uppercase() ?: "?",
                        selected = emoji,
                        onSelect = { emoji = it },
                    )
                }
            }
            if (saved != null) {
                ColourRow(colour = colour, saved = state.hue, onSave = onSaveHue, error = state.hueSaveError)
            }
            Spacer(Modifier.height(Spacing.xl))
        }

        Eyebrow("Account")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CloudStatusRow(
                profile = state.profile,
                isRefreshPending = (state.authState as? AuthState.SignedIn)?.isRefreshPending == true,
                onRetry = onRetryProfile,
            )
            SyncStatusRow(status = syncStatus, onRetryFailed = onRetryFailedSync)
        }
        Text(
            // Said precisely: before 1.3.0 "your expenses back up" was true only of unsplit ones. Since
            // then new splits and IOUs with friends who aren't on the app back up too; the ones recorded
            // before that stay on this phone (owner rule), so the backup still matters. The second
            // sentence is the remedy.
            text = "Your expenses back up to your account and keep working offline, including splits and " +
                "IOUs with friends who aren't on the app. Ones from before version 1.3 stay on this " +
                "phone only, so back those up below. Shared groups sync with everyone in them.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.xl),
        )

        Eyebrow("Settings")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingRow(
                title = "UPI ID for getting paid",
                detail = upiId ?: "Add yours so friends can scan to pay you",
                position = RowPosition.First,
                modifier = Modifier.clickable(onClickLabel = "Set up UPI", onClick = onOpenUpi).testTag("setting_upi"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = "Subscriptions",
                detail = "Charges that repeat, added when they're due",
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = "Open subscriptions", onClick = onOpenSubscriptions).testTag("setting_subscriptions"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = "Categories & payment methods",
                detail = "Hide defaults, add your own",
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = "Edit categories", onClick = onOpenCategories).testTag("setting_categories"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = "Ask before discarding an entry",
                detail = "When you close a half-filled expense",
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
            ReminderRows(
                reminder = reminder,
                notificationsAllowed = notificationsAllowed,
                onEnabledChange = { enabled ->
                    onReminderEnabledChange(enabled)
                    // Turning it on is the moment to ask, if the phone won't show it yet.
                    if (enabled && !notificationsAllowed) onAllowNotifications()
                },
                onTimeChange = onReminderTimeChange,
                onAllowNotifications = onAllowNotifications,
            )
            SettingRow(
                title = "Export to a spreadsheet",
                detail = "A month, dates or everything, as CSV",
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = "Open export", onClick = onOpenExport).testTag("setting_export"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            // Deliberately worded as insurance rather than a chore. A normal update keeps all of
            // this — tested by installing one release over another and watching a local friend, a
            // subscription and a custom category come through. What loses it is uninstalling, a
            // new phone, or a change of signing key.
            SettingRow(
                title = "Back up what's only on this phone",
                detail = "Local friends, subscriptions and your own categories, as a file",
                position = RowPosition.Middle,
                modifier = Modifier.clickable(onClickLabel = "Back up local data", onClick = onBackUp).testTag("setting_backup"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
            SettingRow(
                title = "Restore from a backup",
                detail = "Adds back anything missing; never overwrites what's here",
                position = RowPosition.Last,
                modifier = Modifier.clickable(onClickLabel = "Restore from a backup", onClick = onRestore).testTag("setting_restore"),
            ) {
                Icon(KpIcons.ChevronDown, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp).rotate(-90f))
            }
        }

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
            Text(text = "Sign out", style = MaterialTheme.typography.titleMedium.copy(color = OffWhiteText))
        }
        // Play requires deleting an account to be easy to find in the app (plan T1.1).
        TextButton(
            onClick = onDeleteAccount,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.s).testTag("account_delete"),
        ) {
            Text("Delete my account", style = MaterialTheme.typography.labelLarge, color = DestructiveText)
        }
    }
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
    Column(Modifier.padding(top = 2.dp).testTag("colour_row")) {
        ExpandableDetail(
            label = "Colour",
            value = if (saved == null) "Choosing one" else "Yours everywhere",
            expanded = open,
            onToggle = { open = !open },
            position = RowPosition.Single,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.m)) {
                    Box(Modifier.size(28.dp).clip(KpShapes.small).background(picked?.let(::hueColour) ?: CharcoalElevated))
                    Text(
                        "You wear this colour everywhere: on your phone and on every groupmate's.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = Spacing.m),
                    )
                }
                HuePicker(hue = picked, taken = colour.taken, onHueChange = { picked = it })
                val current = picked
                if (current != null && current != saved) {
                    TextButton(onClick = { onSave(current) }, modifier = Modifier.testTag("colour_save")) {
                        Text("Save colour", style = MaterialTheme.typography.labelLarge.copy(color = WarmAmberPrimary, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        val note = error ?: colour.clashWith?.let { "Your colour is close to $it's. Pick another so you're easy to tell apart." }
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
private fun SettingRow(
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
private fun ReminderRows(
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
        title = "Daily reminder",
        detail = when {
            !reminder.enabled -> "Off"
            !notificationsAllowed -> "Notifications are off for Kharcha Paani, so it can't appear"
            else -> "At $time if nothing's logged that day"
        },
        position = RowPosition.Middle,
        modifier = Modifier
            .toggleable(value = reminder.enabled, role = Role.Switch, onValueChange = onEnabledChange)
            .testTag("setting_reminder"),
    ) {
        if (reminder.enabled && !notificationsAllowed) {
            TextButton(onClick = onAllowNotifications, modifier = Modifier.testTag("setting_reminder_allow")) {
                Text(
                    "Allow",
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
            title = "Reminder time",
            detail = time,
            position = RowPosition.Middle,
            modifier = Modifier
                .clickable(onClickLabel = "Change the reminder time") {
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
) {
    val unreachable = isRefreshPending || profile == ProfileState.Unavailable
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KpShapes.medium)
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
                isRefreshPending -> "Offline. Your account reconnects on its own."
                profile == ProfileState.Unavailable -> "Can't reach your cloud account right now"
                profile is ProfileState.Loaded -> "Cloud profile ready"
                profile == ProfileState.Missing -> "Cloud profile not found"
                else -> "Checking your cloud profile…"
            },
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        )
        if (profile == ProfileState.Unavailable || profile == ProfileState.Missing) {
            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KpShapes.medium)
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
            text = syncStatusText(status, now = System.currentTimeMillis()) { lastSyncedAt ->
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
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = WarmAmberPrimary,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

internal fun syncStatusText(status: SyncStatus, now: Long, relativeTime: (Long) -> String): String = when {
    status.failedChanges == 1 -> "1 change couldn't sync"
    status.failedChanges > 1 -> "${status.failedChanges} changes couldn't sync"
    status.isSyncing -> "Syncing…"
    status.unsyncedChanges == 1 -> "1 change waiting to sync"
    status.unsyncedChanges > 1 -> "${status.unsyncedChanges} changes waiting to sync"
    status.lastSyncedAt != null && now - status.lastSyncedAt < JUST_NOW_MILLIS -> "All changes synced"
    status.lastSyncedAt != null -> "All changes synced · ${relativeTime(status.lastSyncedAt)}"
    else -> "Not synced yet"
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
