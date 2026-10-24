package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.domain.UpiPayee
import com.vaibhav.kharchapaani.domain.UpiLink
import com.vaibhav.kharchapaani.domain.HisaabItemiser
import com.vaibhav.kharchapaani.ui.format.AskMessage
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.groups.RecordInGroupSection
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import kotlin.math.abs

private val NoGroups = SharedRecordGroups.Options(groups = emptyList(), withoutAccount = emptyList(), involvesGroupmates = false)

@Composable
fun SettleUpSheet(
    isOpen: Boolean,
    person: PersonEntity?,
    suggestedAmount: Double = 0.0,
    groupOptions: SharedRecordGroups.Options = NoGroups,
    onCreateGroup: () -> Unit = {},
    onConfirm: (fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long?) -> Unit,
    onDismiss: () -> Unit,
    upiPayee: UpiPayee? = null,
    hisaab: HisaabItemiser.Hisaab? = null,
    onAsk: ((amount: Double, message: String) -> Unit)? = null,
) {
    if (!isOpen || person == null) return
    KpModalSheet(onDismiss = onDismiss) {
        SettleUpContent(person, suggestedAmount, groupOptions, onCreateGroup, onConfirm, upiPayee = upiPayee, hisaab = hisaab, onAsk = onAsk)
    }
}

/**
 * Recording that money changed hands with one friend. It opens on what's owed right now, pointing the
 * way the balance runs, so the common case is one tap.
 */
@Composable
fun SettleUpContent(
    person: PersonEntity,
    suggestedAmount: Double,
    groupOptions: SharedRecordGroups.Options,
    onCreateGroup: () -> Unit,
    onConfirm: (fromPersonId: Long, toPersonId: Long, amount: Double, note: String, groupId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    // Set when this account has a UPI ID: shown as a QR for the amount when the friend is paying.
    upiPayee: UpiPayee? = null,
    // Ask-to-pay: what the friend owes, line by line (null when they owe nothing), and the share.
    hisaab: HisaabItemiser.Hisaab? = null,
    onAsk: ((amount: Double, message: String) -> Unit)? = null,
) {
    // suggestedAmount > 0: they owe me, so they're the one paying.
    var theyPaid by rememberSaveable(person.id) { mutableStateOf(suggestedAmount >= 0) }
    var amount by rememberSaveable(person.id) { mutableStateOf(if (abs(suggestedAmount) > 0) Money.editable(abs(suggestedAmount)) else "") }
    var note by rememberSaveable(person.id) { mutableStateOf("") }
    var selectedGroupId by remember(person.id, groupOptions) { mutableStateOf(groupOptions.onlyGroup?.id) }

    val value = amount.toDoubleOrNull() ?: 0.0
    val needsGroup = groupOptions.canSync && selectedGroupId == null

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (groupOptions.involvesGroupmates && !groupOptions.canSync) "Save on this phone only" else "Record settlement",
                disabledLabel = if (value <= 0) "Add an amount" else "Choose a group",
                enabled = value > 0 && !needsGroup,
                onClick = {
                    val groupId = selectedGroupId.takeIf { groupOptions.canSync }
                    if (theyPaid) onConfirm(person.id, ME, value, note, groupId) else onConfirm(ME, person.id, value, note, groupId)
                },
                buttonTestTag = "confirm_settle_button",
            )
        },
    ) {
        SheetTitle(
            title = "Settle up with ${person.name}",
            supporting = when {
                suggestedAmount >= 0.005 -> "${person.name} owes you ${Money.format(suggestedAmount)}."
                suggestedAmount <= -0.005 -> "You owe ${person.name} ${Money.format(-suggestedAmount)}."
                else -> "You're even right now. Record a payment anyway if one happened."
            },
        )
        SegmentedControl(
            options = listOf(true, false),
            selected = theyPaid,
            // First names: two segments of "Rohan Mehta paid you" / "You paid Rohan Mehta" barely fit.
            label = { if (it) "${firstName(person.name)} paid you" else "You paid ${firstName(person.name)}" },
            onSelect = { theyPaid = it },
            accentFor = { if (it) ChartreuseSecondary else WarmAmberPrimary },
            modifier = Modifier.fillMaxWidth(),
        )
        BigAmountField(
            amount = amount,
            onAmountChange = { amount = it },
            accent = if (theyPaid) ChartreuseSecondary else WarmAmberPrimary,
            testTag = "settle_amount_input",
        )
        SheetRow(label = "Note") {
            SheetTextInput(value = note, onValueChange = { note = it }, placeholder = "Optional, like “Goa hotel”")
        }
        // Ask-to-pay (eng review D2): only where the money comes to you and they actually owe you.
        // Above the QR (owner, 2026-10-23): below it, the ask was off screen until you scrolled.
        if (onAsk != null && hisaab != null && theyPaid && value > 0) {
            val message = remember(person.name, hisaab, value, upiPayee) { AskMessage.build(person.name, hisaab, value, upiPayee?.upiId) }
            AskToPaySection(
                firstName = firstName(person.name),
                message = message,
                onAsk = { onAsk(value, message) },
                modifier = Modifier.padding(top = Spacing.m),
            )
        }
        // A code on both sides (owner, 2026-09-27): whoever is receiving the money first, and the
        // other person's too when both have one. With a friend who isn't on the app it's your code.
        MoneyQrSection(
            person = person,
            myPayee = upiPayee,
            amount = value,
            note = note.ifBlank { "Kharcha Paani settle up" },
            moneyComesToMe = theyPaid,
            modifier = Modifier.padding(top = Spacing.m),
        )
        RecordInGroupSection(
            otherPersonName = person.name,
            options = groupOptions,
            selectedGroupId = selectedGroupId,
            onSelectGroup = { selectedGroupId = it },
            onCreateGroup = onCreateGroup,
            modifier = Modifier.padding(top = Spacing.m),
        )
    }
}

private const val ME = -1L

/**
 * The ask's message as it will be sent (eng review D6: previewed here, edited, if at all, in the app
 * it's sent from) and the button that opens the share sheet.
 */
@Composable
private fun AskToPaySection(firstName: String, message: String, onAsk: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text("Ask $firstName to pay", style = MaterialTheme.typography.titleSmall)
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .padding(top = Spacing.s)
                .fillMaxWidth()
                .clip(KpShapes.medium)
                .background(CharcoalCard)
                .padding(Spacing.m)
                .testTag("ask_preview"),
        )
        KpButton(
            label = "Ask $firstName to pay",
            onClick = onAsk,
            fillWidth = true,
            onClickLabel = "Share this hisaab with $firstName",
            testTag = "ask_to_pay_button",
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

private fun firstName(name: String) = name.trim().substringBefore(' ').ifEmpty { name }
