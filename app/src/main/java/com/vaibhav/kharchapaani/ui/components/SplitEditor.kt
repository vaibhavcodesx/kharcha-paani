package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.util.Locale
import kotlin.math.abs

/** Who paid, how it's divided, and each person's part. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SplitEditor(
    amount: String,
    splitMode: SplitMode,
    paidByPersonId: Long,
    shares: List<ShareInput>,
    onSplitModeChange: (SplitMode) -> Unit,
    onPaidByPersonIdChange: (Long) -> Unit,
    onTogglePersonShare: (Long) -> Unit,
    onExactAmountChange: (Long, Double) -> Unit,
    onPercentageChange: (Long, Double) -> Unit,
    onOpenAddFriend: () -> Unit,
    modifier: Modifier = Modifier,
    // Who added the expense, when only they can change who paid.
    payerLockedBy: String? = null,
    // Who added the expense, when their guests are in it and only they can change the split.
    splitLockedBy: String? = null,
) {
    val equalLabel = stringResource(R.string.entry_equally)
    val exactLabel = stringResource(R.string.entry_by_amount)
    val percentLabel = stringResource(R.string.entry_by_percent)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(stringResource(R.string.entry_paid_by), style = EyebrowText)
        if (payerLockedBy != null) {
            Text(
                stringResource(R.string.entry_payer_locked, payerLockedBy),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("payer_locked_note"),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ChoiceChip(label = stringResource(R.string.entry_you), selected = paidByPersonId == ME, onClick = { onPaidByPersonIdChange(ME) })
                // A guest has no account and can't be anyone's payer in a shared group.
                shares.filter { it.personId != ME && !it.isGuest }.forEach { share ->
                    ChoiceChip(label = share.personName, selected = paidByPersonId == share.personId, onClick = { onPaidByPersonIdChange(share.personId) })
                }
            }
        }

        if (splitLockedBy != null) {
            Text(
                stringResource(R.string.entry_guests_locked, splitLockedBy),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("split_locked_note"),
            )
            shares.filter { it.isSelected }.forEach { share ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (share.personId == ME) stringResource(R.string.entry_you) else share.personName, style = MaterialTheme.typography.bodyLarge, color = OffWhiteText, modifier = Modifier.weight(1f))
                    Text(Money.format(share.amount), style = MoneyText.row, color = OffWhiteText)
                }
            }
            return@Column
        }

        SegmentedControl(
            options = SplitMode.entries,
            selected = splitMode,
            label = {
                when (it) {
                    SplitMode.EQUAL -> equalLabel
                    SplitMode.EXACT -> exactLabel
                    SplitMode.PERCENTAGE -> percentLabel
                }
            },
            onSelect = onSplitModeChange,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.entry_split_between), style = EyebrowText, modifier = Modifier.weight(1f))
            // A friend added here would be a guest, and only whoever added the expense can change its guests.
            if (payerLockedBy == null) {
                TextButton(onClick = onOpenAddFriend) {
                    Text(stringResource(R.string.entry_add_friend), style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                }
            }
        }

        Column {
            shares.forEach { share ->
                ShareRow(
                    share = share,
                    splitMode = splitMode,
                    onToggle = { onTogglePersonShare(share.personId) },
                    onExactAmountChange = { onExactAmountChange(share.personId, it) },
                    onPercentageChange = { onPercentageChange(share.personId, it) },
                )
            }
        }

        val problem = splitProblem(LocalContext.current, amount, splitMode, shares)
        if (problem != null) {
            Text(problem, style = MaterialTheme.typography.bodyMedium, color = WarmAmberLight)
        }
    }
}

@Composable
private fun ShareRow(
    share: ShareInput,
    splitMode: SplitMode,
    onToggle: () -> Unit,
    onExactAmountChange: (Double) -> Unit,
    onPercentageChange: (Double) -> Unit,
) {
    val personLabel = if (share.personId == ME) stringResource(R.string.entry_you) else share.personName
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(KpShapes.small)
                .toggleable(value = share.isSelected, role = Role.Checkbox, onValueChange = { onToggle() }),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(SquircleCheck)
                    .background(if (share.isSelected) WarmAmberPrimary else CharcoalCard)
                    .border(1.dp, if (share.isSelected) WarmAmberPrimary else OutlineInteractive, SquircleCheck),
            ) {
                if (share.isSelected) Icon(KpIcons.Check, contentDescription = null, tint = CharcoalBg, modifier = Modifier.size(16.dp))
            }
            Column(Modifier.padding(start = Spacing.m)) {
                Text(
                    text = personLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (share.isSelected) OffWhiteText else SubMutedWarmGray,
                )
                if (share.isGuest) {
                    // Not in the group: their share is yours to cover, and they owe it to you.
                    Text(stringResource(R.string.entry_your_guest), style = MaterialTheme.typography.bodySmall, color = SubMutedWarmGray)
                }
            }
        }
        if (share.isSelected) {
            when (splitMode) {
                SplitMode.EQUAL -> Text(Money.format(share.amount), style = MoneyText.row, color = OffWhiteText)
                SplitMode.EXACT -> NumberBox(
                    value = if (share.amount > 0) Money.input(share.amount) else "",
                    prefix = "₹",
                    suffix = "",
                    onValueChange = { onExactAmountChange(Money.parseInput(it) ?: 0.0) },
                    description = stringResource(R.string.entry_person_amount, personLabel),
                )
                SplitMode.PERCENTAGE -> NumberBox(
                    value = if (share.percentage > 0) Money.input(share.percentage) else "",
                    prefix = "",
                    suffix = "%",
                    onValueChange = { onPercentageChange(Money.parseInput(it) ?: 0.0) },
                    description = stringResource(R.string.entry_person_percentage, personLabel),
                )
            }
        }
    }
}

/**
 * Keeps what's being typed: rebuilding the text from the parsed number turned "12." into "12" and
 * swallowed the decimal point. It only resyncs when [value] means a different number (the split was
 * recalculated elsewhere).
 */
@Composable
private fun NumberBox(value: String, prefix: String, suffix: String, onValueChange: (String) -> Unit, description: String) {
    var text by remember { mutableStateOf(value) }
    if ((Money.parseInput(text) ?: 0.0) != (Money.parseInput(value) ?: 0.0)) text = value
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(112.dp)
            .heightIn(min = 44.dp)
            .clip(KpShapes.small)
            .background(CharcoalBg)
            .border(1.dp, OutlineInteractive, KpShapes.small)
            .padding(horizontal = Spacing.m),
    ) {
        if (prefix.isNotEmpty()) Text(prefix, style = MoneyText.row, color = WarmAmberLight)
        BasicTextField(
            value = text,
            onValueChange = { typed ->
                Money.acceptInput(typed)?.let {
                    text = it
                    onValueChange(it)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = MoneyText.row.copy(color = OffWhiteText, textAlign = TextAlign.End),
            cursorBrush = SolidColor(WarmAmberPrimary),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = description },
        )
        if (suffix.isNotEmpty()) Text(suffix, style = MoneyText.row, color = WarmAmberLight)
    }
}

private val SquircleCheck = SquircleShape(7.dp)

private const val ME = -1L

/**
 * What stops this split from adding up, or null when it does. The same text shows under the
 * shares and on the disabled Save button: a split that didn't add up used to save anyway, which in
 * a shared group meant a sync that could only ever fail, and in a local one meant the unassigned
 * money silently fell out of everyone's balance.
 */
internal fun splitProblem(context: android.content.Context, amount: String, splitMode: SplitMode, shares: List<ShareInput>): String? {
    val total = Money.parseInput(amount) ?: 0.0
    val selected = shares.filter { it.isSelected }
    if (selected.isEmpty()) return context.getString(R.string.entry_pick_split_people)
    return when (splitMode) {
        SplitMode.EXACT -> {
            val left = total - selected.sumOf { it.amount }
            when {
                abs(left) <= 0.005 -> null
                left > 0 -> context.getString(R.string.entry_still_to_assign, Money.format(left))
                else -> context.getString(R.string.entry_over_total, Money.format(-left))
            }
        }
        SplitMode.PERCENTAGE -> {
            val sum = selected.sumOf { it.percentage }
            // Each share is rounded to 0.1%, so an even split of 3 is 33.3 x 3 = 99.9 (and 99.899...
            // in floating point, which a flat 0.1 tolerance refused). Allow half a step per person.
            if (abs(100.0 - sum) > 0.05 * selected.size + 1e-9) context.getString(R.string.entry_percent_problem, String.format(Locale.US, "%.1f", sum)) else null
        }
        SplitMode.EQUAL -> null
    }
}
