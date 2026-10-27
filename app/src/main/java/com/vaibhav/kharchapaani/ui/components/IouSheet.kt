package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
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
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.ui.format.Money
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
    upiPayee: UpiPayee? = null,
) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) {
        IouContent(people, groupOptionsFor, onCreateGroup, onConfirm, upiPayee = upiPayee)
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
    // This account's UPI ID, for the code a friend scans when you borrow from them.
    upiPayee: UpiPayee? = null,
) {
    val lentLabel = stringResource(R.string.payment_you_lent)
    val borrowedLabel = stringResource(R.string.payment_you_borrowed)
    val lentNote = stringResource(R.string.payment_lent_note)
    val borrowedNote = stringResource(R.string.payment_borrowed_note)
    val defaultUpiNote = stringResource(R.string.payment_iou_upi_note)
    var lent by rememberSaveable { mutableStateOf(true) }
    var personId by rememberSaveable { mutableStateOf(people.singleOrNull()?.id ?: NOBODY) }
    var amount by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    val person = people.firstOrNull { it.id == personId }
    val groupOptions = remember(personId, groupOptionsFor) {
        if (person == null) SharedRecordGroups.Options(emptyList(), emptyList(), involvesGroupmates = false) else groupOptionsFor(personId)
    }
    var selectedGroupId by remember(groupOptions) { mutableStateOf(groupOptions.onlyGroup?.id) }

    val value = Money.parseInput(amount) ?: 0.0
    val needsGroup = groupOptions.canSync && selectedGroupId == null
    val missing = when {
        people.isEmpty() -> stringResource(R.string.payment_add_friend)
        person == null -> if (lent) stringResource(R.string.payment_choose_recipient) else stringResource(R.string.payment_choose_lender)
        value <= 0 -> stringResource(R.string.payment_add_amount)
        needsGroup -> stringResource(R.string.payment_choose_group)
        else -> null
    }
    fun record() {
        val chosen = person ?: return
        val note = reason.ifBlank { if (lent) lentNote else borrowedNote }
        val groupId = selectedGroupId.takeIf { groupOptions.canSync }
        if (lent) onConfirm(ME, chosen.id, value, note, groupId) else onConfirm(chosen.id, ME, value, note, groupId)
    }
    var checkLargeAmount by remember { mutableStateOf(false) }
    if (checkLargeAmount) {
        LargeAmountDialog(value, onConfirm = { checkLargeAmount = false; record() }, onDismiss = { checkLargeAmount = false })
    }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (groupOptions.involvesGroupmates && !groupOptions.canSync) stringResource(R.string.payment_local_only) else stringResource(R.string.payment_record_iou),
                disabledLabel = missing ?: "",
                enabled = missing == null,
                onClick = { if (value > Money.LARGE_AMOUNT) checkLargeAmount = true else record() },
                buttonTestTag = "confirm_iou_button",
            )
        },
    ) {
        SheetTitle(title = stringResource(R.string.payment_iou_title), supporting = stringResource(R.string.payment_iou_body))
        SegmentedControl(
            options = listOf(true, false),
            selected = lent,
            label = { if (it) lentLabel else borrowedLabel },
            onSelect = { lent = it },
            accentFor = { if (it) WarmAmberPrimary else ChartreuseSecondary },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(if (lent) stringResource(R.string.payment_to) else stringResource(R.string.payment_from), style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.xs))
        if (people.isEmpty()) {
            Text(stringResource(R.string.payment_no_friends), style = MaterialTheme.typography.bodyMedium)
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
        SheetRow(label = stringResource(R.string.payment_for)) {
            SheetTextInput(value = reason, onValueChange = { reason = it }, placeholder = stringResource(R.string.payment_reason_hint))
        }
        // The same code as settling up, either way round: lend and pay into theirs (or show yours),
        // borrow and they scan yours.
        if (person != null) {
            MoneyQrSection(
                person = person,
                myPayee = upiPayee,
                amount = value,
                note = reason.ifBlank { defaultUpiNote },
                moneyComesToMe = !lent,
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        RecordInGroupSection(
            otherPersonName = person?.name ?: stringResource(R.string.payment_them),
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
