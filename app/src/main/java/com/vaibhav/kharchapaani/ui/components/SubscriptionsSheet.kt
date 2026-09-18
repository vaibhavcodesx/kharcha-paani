package com.vaibhav.kharchapaani.ui.components

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import com.vaibhav.kharchapaani.domain.SubscriptionSchedule
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What a new subscription needs; the sheet hands this back when it's complete. */
data class NewSubscription(val name: String, val amount: Double, val category: String, val paymentMethod: String, val cycle: SubscriptionCycle, val start: LocalDate)

@Composable
fun SubscriptionsSheet(
    isOpen: Boolean,
    subscriptions: List<SubscriptionEntity>,
    prefs: CategoryPrefs,
    onAdd: (NewSubscription, chargeFromStart: Boolean) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) {
        SubscriptionsContent(subscriptions, prefs, onAdd, onCancel, onDelete, today = today)
    }
}

/**
 * Subscriptions are rules, not entries: each charge becomes a real expense when its date arrives, so
 * a month's spending only ever counts what has actually been charged. Adding one that started in the
 * past asks before filling those months in; cancelling stops what's to come and keeps what happened.
 */
@Composable
fun SubscriptionsContent(
    subscriptions: List<SubscriptionEntity>,
    prefs: CategoryPrefs,
    onAdd: (NewSubscription, chargeFromStart: Boolean) -> Unit,
    onCancel: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    val zone = ZoneId.systemDefault()
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Categories.expenseCategories(prefs).firstOrNull() ?: Categories.OTHER) }
    var method by rememberSaveable { mutableStateOf(Categories.paymentMethods(prefs).firstOrNull() ?: Categories.OTHER) }
    var cycle by rememberSaveable { mutableStateOf(SubscriptionCycle.MONTHLY) }
    var start by rememberSaveable { mutableStateOf(today) }
    var askBackdate by rememberSaveable { mutableStateOf(false) }

    val value = amount.toDoubleOrNull() ?: 0.0
    val cleanName = name.trim()
    val ready = cleanName.isNotEmpty() && value > 0
    val missed = remember(start, cycle, today) { SubscriptionSchedule.dueDates(start, cycle, today) }

    fun add(chargeFromStart: Boolean) {
        onAdd(NewSubscription(cleanName, value, category, method, cycle, start), chargeFromStart)
        name = ""
        amount = ""
        start = today
    }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = "Add subscription",
                disabledLabel = if (cleanName.isEmpty()) "Name it" else "Add an amount",
                enabled = ready,
                onClick = { if (missed.size > 1) askBackdate = true else add(chargeFromStart = true) },
                summary = if (value > 0) Money.format(value) else null,
                detail = if (ready) "${cycleLabel(cycle)} · from ${DayFormat.format(start)}" else null,
                buttonTestTag = "add_subscription_button",
            )
        },
    ) {
        SheetTitle(
            title = "Subscriptions",
            supporting = "Netflix, rent, the gym. Each charge is added to your spending on the day it's due, never before.",
        )

        if (subscriptions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = Spacing.xl)) {
                subscriptions.forEachIndexed { index, subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        position = RowPosition.of(index, subscriptions.size),
                        today = today,
                        zone = zone,
                        onCancel = { onCancel(subscription.id) },
                        onDelete = { onDelete(subscription.id) },
                    )
                }
            }
        }

        Text("Add one".uppercase(), style = EyebrowText, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SheetRow(label = "Name", position = RowPosition.First) {
                SheetTextInput(value = name, onValueChange = { name = it }, placeholder = "Netflix", fieldTestTag = "subscription_name_input")
            }
            SheetRow(label = "Amount", position = RowPosition.Middle) {
                SheetTextInput(
                    value = amount,
                    onValueChange = { amount = it },
                    placeholder = "649",
                    keyboardType = KeyboardType.Decimal,
                    fieldTestTag = "subscription_amount_input",
                )
            }
            SheetRow(label = "Every", position = RowPosition.Middle) {
                SegmentedControl(
                    options = SubscriptionCycle.entries,
                    selected = cycle,
                    label = { cycleLabel(it) },
                    onSelect = { cycle = it },
                    testTagFor = { "cycle_${it.name}" },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DatePickRow(label = "Starts", date = start, max = today, onPick = { start = it })
        }
        Text(
            text = when {
                missed.size > 1 -> "Started ${DayFormat.format(start)}: that's ${missed.size} charges so far. You'll be asked whether to add them."
                else -> "The first charge is added on ${DayFormat.format(start)}."
            },
            style = MaterialTheme.typography.bodySmall,
            color = SubMutedWarmGray,
            modifier = Modifier.padding(top = Spacing.s).testTag("subscription_start_note"),
        )

        CategoryPickRow(label = "Category", value = category, choices = Categories.expenseCategories(prefs), onPick = { category = it })
        CategoryPickRow(label = "Paid with", value = method, choices = Categories.paymentMethods(prefs), onPick = { method = it })

        if (askBackdate) {
            AlertDialog(
                onDismissRequest = { askBackdate = false },
                containerColor = CharcoalSurface,
                shape = KpShapes.large,
                modifier = Modifier.testTag("backdate_dialog"),
                title = { Text("Add the ${missed.size} charges since ${DayFormat.format(start)}?", style = MaterialTheme.typography.headlineMedium) },
                text = {
                    Text(
                        "They'll go into those months' spending as ${Money.format(value)} each. Otherwise it starts from the next one.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            askBackdate = false
                            add(chargeFromStart = true)
                        },
                        modifier = Modifier.testTag("backdate_add_button"),
                    ) {
                        Text("Add them", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            askBackdate = false
                            add(chargeFromStart = false)
                        },
                        modifier = Modifier.testTag("backdate_skip_button"),
                    ) {
                        Text("Start from the next one", style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
                    }
                },
                titleContentColor = OffWhiteText,
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: SubscriptionEntity,
    position: RowPosition,
    today: LocalDate,
    zone: ZoneId,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val cycle = runCatching { SubscriptionCycle.valueOf(subscription.cycle) }.getOrNull() ?: SubscriptionCycle.MONTHLY
    val start = Instant.ofEpochMilli(subscription.startDate).atZone(zone).toLocalDate()
    val cancelled = subscription.cancelledAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
    val next = SubscriptionSchedule.nextDue(start, cycle, today, cancelled)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .semantics(mergeDescendants = true) {}
            .padding(start = Spacing.l, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs)
            .testTag("subscription_${subscription.id}"),
    ) {
        CategoryBadge(subscription.category)
        Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
            Text(subscription.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    next == null -> "Cancelled"
                    else -> "${cycleLabel(cycle)} · next ${DayFormat.format(next)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (next == null) SubMutedWarmGray else MutedWarmGray,
                maxLines = 1,
            )
        }
        Text(Money.format(subscription.amount), style = MoneyText.row, color = if (next == null) SubMutedWarmGray else OffWhiteText)
        if (next == null) {
            IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_subscription_${subscription.id}")) {
                Icon(KpIcons.Delete, contentDescription = "Remove ${subscription.name}", tint = MutedWarmGray, modifier = Modifier.size(20.dp))
            }
        } else {
            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_subscription_${subscription.id}")) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        }
    }
}

@Composable
private fun DatePickRow(label: String, date: LocalDate, max: LocalDate, onPick: (LocalDate) -> Unit) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RowPosition.Last.shape)
            .background(CharcoalCard)
            .clickable(onClickLabel = "Change the start date") {
                DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
                    datePicker.maxDate = max.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.show()
            }
            .padding(horizontal = Spacing.l, vertical = Spacing.s)
            .testTag("subscription_start_row"),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(SheetLabelWidth))
        Text(DayFormat.format(date), style = MaterialTheme.typography.bodyLarge, color = OffWhiteText, modifier = Modifier.weight(1f))
        Icon(KpIcons.Calendar, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CategoryPickRow(label: String, value: String, choices: List<String>, onPick: (String) -> Unit) {
    var open by rememberSaveable(label) { mutableStateOf(false) }
    Column(Modifier.padding(top = Spacing.m)) {
        ExpandableDetail(
            label = label,
            value = value,
            expanded = open,
            onToggle = { open = !open },
            position = RowPosition.Single,
        ) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                choices.forEach { choice ->
                    ChoiceChip(
                        label = choice,
                        selected = choice == value,
                        onClick = {
                            onPick(choice)
                            open = false
                        },
                    )
                }
            }
        }
    }
}

private fun cycleLabel(cycle: SubscriptionCycle) = when (cycle) {
    SubscriptionCycle.WEEKLY -> "Weekly"
    SubscriptionCycle.MONTHLY -> "Monthly"
    SubscriptionCycle.YEARLY -> "Yearly"
}

private val DayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
