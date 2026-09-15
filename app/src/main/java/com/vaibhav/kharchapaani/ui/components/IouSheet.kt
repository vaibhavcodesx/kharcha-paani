package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.groups.RecordInGroupSection
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

@Composable
fun IouSheet(
    isOpen: Boolean,
    people: List<PersonEntity>,
    groupOptionsFor: (personId: Long) -> SharedRecordGroups.Options,
    onCreateGroup: () -> Unit,
    onConfirm: (fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) {
        IouContent(people, groupOptionsFor, onCreateGroup, onConfirm)
    }
}

/**
 * Money lent or borrowed outside any expense. Nobody is preselected when there's a choice to make:
 * the old dialog picked the first friend, which made it easy to log against the wrong person.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IouContent(
    people: List<PersonEntity>,
    groupOptionsFor: (personId: Long) -> SharedRecordGroups.Options,
    onCreateGroup: () -> Unit,
    onConfirm: (fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var lent by rememberSaveable { mutableStateOf(true) }
    var personId by rememberSaveable { mutableStateOf(people.singleOrNull()?.id ?: NOBODY) }
    var amount by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val person = people.firstOrNull { it.id == personId }
    val groupOptions = remember(personId, groupOptionsFor) {
        if (person == null) SharedRecordGroups.Options(emptyList(), emptyList(), involvesGroupmates = false) else groupOptionsFor(personId)
    }
    var selectedGroupId by remember(groupOptions) { mutableStateOf(groupOptions.onlyGroup?.id) }

    val value = amount.toDoubleOrNull() ?: 0.0
    val needsGroup = groupOptions.canSync && selectedGroupId == null
    val missing = when {
        people.isEmpty() -> "Add a friend first"
        person == null -> if (lent) "Choose who you lent to" else "Choose who lent to you"
        value <= 0 -> "Add an amount"
        needsGroup -> "Choose a group"
        else -> null
    }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (groupOptions.involvesGroupmates && !groupOptions.canSync) "Save on this phone only" else "Record IOU",
                disabledLabel = missing ?: "",
                enabled = missing == null,
                onClick = {
                    val chosen = person ?: return@GlassActionBar
                    val note = reason.ifBlank { if (lent) "Informal Loan" else "Borrowed Amount" }
                    val groupId = selectedGroupId.takeIf { groupOptions.canSync }
                    if (lent) onConfirm(ME, chosen.id, value, note, groupId) else onConfirm(chosen.id, ME, value, note, groupId)
                },
                buttonTestTag = "confirm_iou_button",
            )
        },
    ) {
        SheetTitle(title = "Log an IOU", supporting = "Money lent or borrowed that isn't part of an expense.")
        SegmentedControl(
            options = listOf(true, false),
            selected = lent,
            label = { if (it) "You lent" else "You borrowed" },
            onSelect = { lent = it },
            accentFor = { if (it) WarmAmberPrimary else ChartreuseSecondary },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(if (lent) "TO" else "FROM", style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs))
        if (people.isEmpty()) {
            Text("You haven't added any friends yet. Add one from Splits, then log the IOU.", style = MaterialTheme.typography.bodyMedium)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                people.forEach { p ->
                    ChoiceChip(
                        label = p.name,
                        selected = p.id == personId,
                        onClick = { personId = p.id },
                        modifier = Modifier.testTag("iou_person_${p.id}"),
                    )
                }
            }
        }
        BigAmountField(
            amount = amount,
            onAmountChange = { amount = it },
            accent = if (lent) WarmAmberPrimary else ChartreuseSecondary,
            testTag = "iou_amount_input",
        )
        SheetRow(label = "For") {
            SheetTextInput(value = reason, onValueChange = { reason = it }, placeholder = "Optional, like “hackathon food”")
        }
        RecordInGroupSection(
            otherPersonName = person?.name ?: "them",
            options = groupOptions,
            selectedGroupId = selectedGroupId,
            onSelectGroup = { selectedGroupId = it },
            onCreateGroup = onCreateGroup,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

private const val ME = -1L
private const val NOBODY = Long.MIN_VALUE
