package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun PersonLedgerSheet(
    person: PersonEntity?,
    ledgerItems: List<PersonLedgerItem>,
    onSettleUp: (PersonEntity) -> Unit,
    onDismiss: () -> Unit
) {
    if (person == null) return
    KpModalSheet(onDismiss = onDismiss) { PersonLedgerContent(person, ledgerItems, onSettleUp) }
}

/** Everything between you and one friend: where it stands now, then every split and settlement. */
@Composable
fun PersonLedgerContent(
    person: PersonEntity,
    ledgerItems: List<PersonLedgerItem>,
    onSettleUp: (PersonEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }
    val net = ledgerItems.sumOf { it.netChange }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = if (abs(net) >= 0.005) "Settle up" else "Record a payment",
                enabled = true,
                onClick = { onSettleUp(person) },
                summary = if (abs(net) >= 0.005) Money.format(abs(net)) else "All settled",
                detail = when {
                    net >= 0.005 -> "${person.name} owes you"
                    net <= -0.005 -> "You owe ${person.name}"
                    else -> null
                },
                buttonTestTag = "ledger_settle_up_button",
            )
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xl)) {
            PersonAvatar(name = person.name, colorHex = person.avatarColor, size = 52.dp, emoji = person.avatarEmoji)
            Column(Modifier.weight(1f).padding(start = Spacing.m)) {
                Text(person.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Text(
                    text = when {
                        net >= 0.005 -> "Owes you ${Money.format(net)}"
                        net <= -0.005 -> "You owe ${Money.format(abs(net))}"
                        else -> "All settled"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (net >= 0.005) ChartreuseSecondary else MutedWarmGray,
                )
            }
        }

        Text("History · ${ledgerItems.size}", style = MaterialTheme.typography.titleSmall, color = OffWhiteText, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
        if (ledgerItems.isEmpty()) {
            Text("No splits or settlements with ${person.name} yet.", style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ledgerItems.forEachIndexed { index, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clip(RowPosition.of(index, ledgerItems.size).shape)
                        .background(CharcoalCard)
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.l, vertical = Spacing.m),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(KpShapes.small).background((if (item.isSettlement) ChartreuseSecondary else WarmAmberLight).copy(alpha = 0.14f)),
                    ) {
                        Icon(
                            imageVector = if (item.isSettlement) KpIcons.Settle else KpIcons.Splits,
                            contentDescription = null,
                            tint = if (item.isSettlement) ChartreuseSecondary else WarmAmberLight,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${dateFormat.format(Date(item.date))} · ${item.subtitle}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = Money.format(item.netChange, showPlus = true),
                        style = MoneyText.row,
                        color = if (item.netChange > 0) ChartreuseSecondary else OffWhiteText,
                    )
                }
            }
        }
    }
}
