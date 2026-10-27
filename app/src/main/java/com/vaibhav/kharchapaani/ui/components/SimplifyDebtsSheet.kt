package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.SimplifiedDebt
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

@Composable
fun SimplifyDebtsSheet(isOpen: Boolean, simplifiedDebts: List<SimplifiedDebt>, onDismiss: () -> Unit) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { SimplifyDebtsContent(simplifiedDebts) }
}

/** The fewest payments that clear everyone's balances, in plain words. A view, so no action bar. */
@Composable
fun SimplifyDebtsContent(simplifiedDebts: List<SimplifiedDebt>, modifier: Modifier = Modifier) {
    SheetScaffold(modifier = modifier) {
        SheetTitle(
            title = stringResource(R.string.debt_management_title),
            supporting = if (simplifiedDebts.isEmpty()) {
                stringResource(R.string.debt_management_empty)
            } else {
                stringResource(R.string.debt_management_explanation)
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            simplifiedDebts.forEachIndexed { index, debt ->
                val iPay = debt.fromPersonId == ME
                val iGet = debt.toPersonId == ME
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .clip(RowPosition.of(index, simplifiedDebts.size).shape)
                        .background(CharcoalCard)
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.l, vertical = Spacing.m),
                ) {
                    Icon(KpIcons.Lend, contentDescription = null, tint = if (iGet) ChartreuseSecondary else WarmAmberLight, modifier = Modifier.size(20.dp))
                    Text(
                        text = when {
                            iPay -> stringResource(R.string.debt_management_pay, debt.toPersonName)
                            iGet -> stringResource(R.string.debt_management_receive, debt.fromPersonName)
                            else -> stringResource(R.string.debt_management_between, debt.fromPersonName, debt.toPersonName)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = Spacing.m),
                    )
                    Text(Money.format(debt.amount), style = MoneyText.row, color = if (iGet) ChartreuseSecondary else OffWhiteText)
                }
            }
        }
    }
}

private const val ME = -1L
