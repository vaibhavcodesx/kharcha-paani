package com.vaibhav.kharchapaani.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.GlassPanel
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.LocalHazeState
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmBottomSheet(
    isOpen: Boolean,
    isParsing: Boolean,
    isEditing: Boolean,
    type: String,
    amount: String,
    merchant: String,
    category: String,
    paymentMethod: String,
    note: String,
    date: Long,
    groupId: Long?,
    availableGroups: List<GroupEntity>,
    isSplit: Boolean,
    splitMode: SplitMode,
    paidByPersonId: Long,
    shares: List<ShareInput>,
    onTypeChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onGroupIdChange: (Long?) -> Unit,
    onIsSplitChange: (Boolean) -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onPaidByPersonIdChange: (Long) -> Unit,
    onTogglePersonShare: (Long) -> Unit,
    onExactAmountChange: (Long, Double) -> Unit,
    onPercentageChange: (Long, Double) -> Unit,
    onOpenAddFriend: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return
    val focusManager = LocalFocusManager.current
    ModalBottomSheet(
        onDismissRequest = {
            focusManager.clearFocus()
            onDismiss()
        },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CharcoalSurface,
        contentColor = OffWhiteText,
        shape = KpShapes.sheet,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = Spacing.m)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(SquircleShape(2.dp))
                    .background(CharcoalBorder)
            )
        },
    ) {
        ConfirmSheetContent(
            isParsing = isParsing, isEditing = isEditing, type = type, amount = amount, merchant = merchant,
            category = category, paymentMethod = paymentMethod, note = note, date = date, groupId = groupId,
            availableGroups = availableGroups, isSplit = isSplit, splitMode = splitMode, paidByPersonId = paidByPersonId,
            shares = shares, onTypeChange = onTypeChange, onAmountChange = onAmountChange, onMerchantChange = onMerchantChange,
            onCategoryChange = onCategoryChange, onPaymentMethodChange = onPaymentMethodChange, onNoteChange = onNoteChange,
            onDateChange = onDateChange, onGroupIdChange = onGroupIdChange, onIsSplitChange = onIsSplitChange,
            onSplitModeChange = onSplitModeChange, onPaidByPersonIdChange = onPaidByPersonIdChange,
            onTogglePersonShare = onTogglePersonShare, onExactAmountChange = onExactAmountChange,
            onPercentageChange = onPercentageChange, onOpenAddFriend = onOpenAddFriend,
            onSave = {
                focusManager.clearFocus()
                onSave()
            },
        )
    }
}

private enum class Detail { Category, Payment, When, Group }

/**
 * The sheet's body without the sheet window, so it can be rendered and tested on its own.
 *
 * It reads as a confirmation of what was understood (amount first, then each detail as a row that
 * opens its choices in place) rather than a form to fill. Save floats in a glass bar over the
 * content, so it's always in reach; the bar samples this sheet's own content, which lives in the
 * same window.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmSheetContent(
    isParsing: Boolean,
    isEditing: Boolean,
    type: String,
    amount: String,
    merchant: String,
    category: String,
    paymentMethod: String,
    note: String,
    date: Long,
    groupId: Long?,
    availableGroups: List<GroupEntity>,
    isSplit: Boolean,
    splitMode: SplitMode,
    paidByPersonId: Long,
    shares: List<ShareInput>,
    onTypeChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPaymentMethodChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onDateChange: (Long) -> Unit,
    onGroupIdChange: (Long?) -> Unit,
    onIsSplitChange: (Boolean) -> Unit,
    onSplitModeChange: (SplitMode) -> Unit,
    onPaidByPersonIdChange: (Long) -> Unit,
    onTogglePersonShare: (Long) -> Unit,
    onExactAmountChange: (Long, Double) -> Unit,
    onPercentageChange: (Long, Double) -> Unit,
    onOpenAddFriend: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isIncome = type == "income"
    val accent = if (isIncome) ChartreuseSecondary else WarmAmberPrimary
    var open by rememberSaveable { mutableStateOf<Detail?>(null) }
    fun toggle(detail: Detail) {
        open = if (open == detail) null else detail
    }
    val hazeState = rememberHazeState()
    val context = LocalContext.current
    val dateText = remember(date) { describeDate(date) }
    val group = availableGroups.firstOrNull { it.id == groupId }

    Box(modifier.fillMaxWidth().imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .hazeSource(hazeState)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.page),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isEditing && isIncome -> "Edit income"
                        isEditing -> "Edit expense"
                        isIncome -> "New income"
                        else -> "New expense"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                SegmentedControl(
                    options = listOf("expense", "income"),
                    selected = type,
                    label = { if (it == "income") "Income" else "Expense" },
                    onSelect = onTypeChange,
                    accentFor = { if (it == "income") ChartreuseSecondary else WarmAmberPrimary },
                    modifier = Modifier.width(196.dp),
                )
            }

            if (isParsing) {
                ParsingPlaceholder()
                return@Column
            }

            AmountField(amount = amount, onAmountChange = onAmountChange, accent = accent)

            // What was understood, one row per detail.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                DetailRow(label = "Merchant", position = RowPosition.First) {
                    Box(Modifier.weight(1f)) {
                        if (merchant.isEmpty()) {
                            Text(if (isIncome) "Who paid you" else "Where or what", style = MaterialTheme.typography.bodyLarge, color = SubMutedWarmGray)
                        }
                        BasicTextField(
                            value = merchant,
                            onValueChange = onMerchantChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = OffWhiteText),
                            cursorBrush = SolidColor(WarmAmberPrimary),
                            modifier = Modifier.fillMaxWidth().testTag("confirm_merchant_input"),
                        )
                    }
                }
                ExpandableDetail(
                    label = "Category",
                    value = category,
                    icon = CategoryHelper.getCategoryIcon(category),
                    iconTint = CategoryHelper.getCategoryColor(category),
                    expanded = open == Detail.Category,
                    onToggle = { toggle(Detail.Category) },
                    position = RowPosition.Middle,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        CategoryHelper.allCategories.forEach { cat ->
                            ChoiceChip(
                                label = cat,
                                selected = cat == category,
                                icon = CategoryHelper.getCategoryIcon(cat),
                                iconTint = CategoryHelper.getCategoryColor(cat),
                                accent = CategoryHelper.getCategoryColor(cat),
                                onClick = {
                                    onCategoryChange(cat)
                                    open = null
                                },
                            )
                        }
                    }
                }
                ExpandableDetail(
                    label = if (isIncome) "Received via" else "Paid with",
                    value = paymentMethod,
                    expanded = open == Detail.Payment,
                    onToggle = { toggle(Detail.Payment) },
                    position = RowPosition.Middle,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        CategoryHelper.allPaymentMethods.forEach { method ->
                            ChoiceChip(label = method, selected = method == paymentMethod, onClick = {
                                onPaymentMethodChange(method)
                                open = null
                            })
                        }
                    }
                }
                ExpandableDetail(
                    label = "When",
                    value = dateText,
                    icon = KpIcons.Calendar,
                    expanded = open == Detail.When,
                    onToggle = { toggle(Detail.When) },
                    position = if (availableGroups.isEmpty()) RowPosition.Last else RowPosition.Middle,
                ) {
                    val now = System.currentTimeMillis()
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        ChoiceChip("Now", selected = false, onClick = { onDateChange(now); open = null })
                        ChoiceChip("2 hours ago", selected = false, onClick = { onDateChange(now - 2 * HOUR); open = null })
                        ChoiceChip("Yesterday", selected = false, onClick = { onDateChange(now - 24 * HOUR); open = null })
                        ChoiceChip(
                            label = "Pick a date…",
                            selected = false,
                            icon = KpIcons.Calendar,
                            onClick = { pickDateTime(context, date) { onDateChange(it); open = null } },
                            modifier = Modifier.testTag("transaction_date_picker_button"),
                        )
                    }
                }
                if (availableGroups.isNotEmpty()) {
                    ExpandableDetail(
                        label = "Group",
                        value = group?.let { "${it.emoji} ${it.name}" } ?: "None",
                        expanded = open == Detail.Group,
                        onToggle = { toggle(Detail.Group) },
                        position = RowPosition.Last,
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                            ChoiceChip("None", selected = groupId == null, onClick = { onGroupIdChange(null); open = null })
                            availableGroups.forEach { g ->
                                ChoiceChip("${g.emoji} ${g.name}", selected = g.id == groupId, onClick = { onGroupIdChange(g.id); open = null })
                            }
                        }
                    }
                }
            }

            if (!isIncome) {
                Text("WHO'S IT FOR", style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s))
                SegmentedControl(
                    options = listOf(false, true),
                    selected = isSplit,
                    label = { if (it) "Split it" else "Just me" },
                    onSelect = onIsSplitChange,
                    testTagFor = { if (it) "split_with_friends_button" else "for_me_button" },
                    modifier = Modifier.fillMaxWidth(),
                )
                AnimatedVisibility(visible = isSplit, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    SplitEditor(
                        amount = amount, splitMode = splitMode, paidByPersonId = paidByPersonId, shares = shares,
                        onSplitModeChange = onSplitModeChange, onPaidByPersonIdChange = onPaidByPersonIdChange,
                        onTogglePersonShare = onTogglePersonShare, onExactAmountChange = onExactAmountChange,
                        onPercentageChange = onPercentageChange, onOpenAddFriend = onOpenAddFriend,
                        modifier = Modifier.padding(top = Spacing.m),
                    )
                }
            }

            DetailRow(label = "Note", position = RowPosition.Single, modifier = Modifier.padding(top = Spacing.xl)) {
                Box(Modifier.weight(1f)) {
                    if (note.isEmpty()) Text("Optional", style = MaterialTheme.typography.bodyLarge, color = SubMutedWarmGray)
                    BasicTextField(
                        value = note,
                        onValueChange = onNoteChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OffWhiteText),
                        cursorBrush = SolidColor(WarmAmberPrimary),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Room to scroll the last row clear of the floating save bar.
            Spacer(Modifier.navigationBarsPadding().height(SaveBarClearance))
        }

        if (!isParsing) {
            CompositionLocalProvider(LocalHazeState provides hazeState) {
                SaveBar(
                    amount = amount,
                    summary = listOfNotNull(merchant.ifBlank { null } ?: category, paymentMethod.ifBlank { null }).joinToString(" · "),
                    isEditing = isEditing,
                    onSave = onSave,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun AmountField(amount: String, onAmountChange: (String) -> Unit, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
    ) {
        Text("₹", style = MoneyText.hero, color = accent)
        Box(Modifier.padding(start = Spacing.xs)) {
            if (amount.isEmpty()) Text("0", style = MoneyText.hero, color = SubMutedWarmGray)
            BasicTextField(
                value = amount,
                onValueChange = onAmountChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MoneyText.hero.copy(color = OffWhiteText),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    // As wide as what's typed, so "₹" and the number stay centred together.
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 2.dp)
                    .semantics { contentDescription = "Amount in rupees" }
                    .testTag("confirm_amount_input"),
            )
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    position: RowPosition,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(LabelWidth))
        content()
    }
}

@Composable
private fun ExpandableDetail(
    label: String,
    value: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    position: RowPosition,
    icon: ImageVector? = null,
    iconTint: Color = MutedWarmGray,
    choices: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(position.shape).background(CharcoalCard)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Close choices" else "Change $label", onClick = onToggle)
                .semantics { if (expanded) stateDescription = "Choices shown" }
                .padding(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(LabelWidth))
            if (icon != null) Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(end = Spacing.s).size(18.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) KpIcons.Close else KpIcons.ChevronDown,
                contentDescription = null,
                tint = MutedWarmGray,
                modifier = Modifier.size(18.dp),
            )
        }
        AnimatedVisibility(visible = expanded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Box(Modifier.padding(start = Spacing.l, end = Spacing.l, bottom = Spacing.m)) { choices() }
        }
    }
}

/**
 * Save, floating over the sheet. The glass is only the rim: the summary and the button sit on the
 * solid core. Disabled, the button says what's missing instead of just dimming.
 */
@Composable
private fun SaveBar(amount: String, summary: String, isEditing: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val value = amount.toDoubleOrNull() ?: 0.0
    val canSave = value > 0
    GlassPanel(
        shape = SquircleShape(28.dp),
        coreShape = SquircleShape(23.dp),
        bezel = 5.dp,
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = Spacing.m, vertical = Spacing.m)
            .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(Spacing.s)) {
            Column(Modifier.weight(1f).padding(horizontal = Spacing.s)) {
                Text(if (canSave) Money.format(value) else "₹0", style = MoneyText.title)
                if (summary.isNotBlank()) {
                    Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .clip(KpShapes.action)
                    .then(if (canSave) Modifier.background(BrandGradient.diagonal()) else Modifier.background(CharcoalElevated))
                    .clickable(enabled = canSave, role = Role.Button, onClick = onSave)
                    .padding(horizontal = Spacing.xl)
                    .testTag("confirm_save_button"),
            ) {
                Text(
                    text = when {
                        !canSave -> "Add an amount"
                        isEditing -> "Update"
                        else -> "Save"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (canSave) CharcoalBg else MutedWarmGray,
                )
            }
        }
    }
}

@Composable
private fun ParsingPlaceholder() {
    Column(Modifier.padding(vertical = Spacing.xl), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .padding(bottom = Spacing.xl)
                .align(Alignment.CenterHorizontally)
                .size(width = 160.dp, height = 48.dp)
                .clip(KpShapes.small)
                .background(CharcoalCard)
                .shimmerLoading()
        )
        listOf(RowPosition.First, RowPosition.Middle, RowPosition.Last).forEach { position ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(position.shape)
                    .background(CharcoalCard)
                    .shimmerLoading()
            )
        }
        Text(
            "Reading your entry…",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.l).align(Alignment.CenterHorizontally),
        )
    }
}

private fun describeDate(millis: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = millis }
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Today, $time"
        sameYear && dayDiff == 1 -> "Yesterday, $time"
        sameYear -> SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(millis)) + ", $time"
        else -> SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis)) + ", $time"
    }
}

private fun pickDateTime(context: android.content.Context, initial: Long, onPicked: (Long) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(
        context,
        { _, year, month, day ->
            cal.set(year, month, day)
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                    onPicked(cal.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                false,
            ).show()
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH),
    ).show()
}

private const val HOUR = 60 * 60 * 1000L
private val LabelWidth = 96.dp
private val SaveBarClearance = 104.dp
