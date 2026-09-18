package com.vaibhav.kharchapaani.ui.components

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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text("PAID BY", style = EyebrowText)
        if (payerLockedBy != null) {
            Text(
                "You paid. Only $payerLockedBy, who added it, can change that.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("payer_locked_note"),
            )
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                ChoiceChip(label = "You", selected = paidByPersonId == ME, onClick = { onPaidByPersonIdChange(ME) })
                shares.filter { it.personId != ME }.forEach { share ->
                    ChoiceChip(label = share.personName, selected = paidByPersonId == share.personId, onClick = { onPaidByPersonIdChange(share.personId) })
                }
            }
        }

        SegmentedControl(
            options = SplitMode.entries,
            selected = splitMode,
            label = {
                when (it) {
                    SplitMode.EQUAL -> "Equally"
                    SplitMode.EXACT -> "By amount"
                    SplitMode.PERCENTAGE -> "By percent"
                }
            },
            onSelect = onSplitModeChange,
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("SPLIT BETWEEN", style = EyebrowText, modifier = Modifier.weight(1f))
            // A friend added here is only on this phone, so can't be in a groupmate's group expense.
            if (payerLockedBy == null) {
                TextButton(onClick = onOpenAddFriend) {
                    Text("Add a friend", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
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

        val total = amount.toDoubleOrNull() ?: 0.0
        val selected = shares.filter { it.isSelected }
        val problem = when (splitMode) {
            SplitMode.EXACT -> {
                val left = total - selected.sumOf { it.amount }
                when {
                    abs(left) <= 0.005 -> null
                    left > 0 -> "${Money.format(left)} still to assign"
                    else -> "${Money.format(-left)} more than the total"
                }
            }
            SplitMode.PERCENTAGE -> {
                val sum = selected.sumOf { it.percentage }
                if (abs(100.0 - sum) > 0.1) "Percentages add up to ${String.format(Locale.US, "%.1f", sum)}%, not 100%" else null
            }
            SplitMode.EQUAL -> null
        }
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
            Text(
                text = share.personName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (share.isSelected) OffWhiteText else SubMutedWarmGray,
                modifier = Modifier.padding(start = Spacing.m),
            )
        }
        if (share.isSelected) {
            when (splitMode) {
                SplitMode.EQUAL -> Text(Money.format(share.amount), style = MoneyText.row, color = OffWhiteText)
                SplitMode.EXACT -> NumberBox(
                    value = if (share.amount > 0) Money.input(share.amount) else "",
                    prefix = "₹",
                    suffix = "",
                    onValueChange = { onExactAmountChange(it.toDoubleOrNull() ?: 0.0) },
                    description = "${share.personName}'s amount",
                )
                SplitMode.PERCENTAGE -> NumberBox(
                    value = if (share.percentage > 0) Money.input(share.percentage) else "",
                    prefix = "",
                    suffix = "%",
                    onValueChange = { onPercentageChange(it.toDoubleOrNull() ?: 0.0) },
                    description = "${share.personName}'s percentage",
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
    if ((text.toDoubleOrNull() ?: 0.0) != (value.toDoubleOrNull() ?: 0.0)) text = value
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
            onValueChange = {
                text = it
                onValueChange(it)
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
