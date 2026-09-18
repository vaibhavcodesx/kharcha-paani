package com.vaibhav.kharchapaani.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.components.AccountAvatar
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.pressBounce
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyOut
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import kotlin.math.abs

/**
 * The top of Home is what this month has cost, in the brand gradient, with the streak underneath.
 *
 * It used to be the split ledger's net balance. Owner decision, 2026-09-18: spending is what
 * matters daily, while a debt is occasional and already has a screen of its own in Splits. So owed
 * and owed-to-you demote to [MonthStrip] below, and the gradient stays with the hero rather than
 * following the debt down.
 */
@Composable
fun BalanceHeader(
    monthlyExpense: Double,
    dailyStreak: Int,
    accountAvatar: String,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    syncStatus: SyncStatus = SyncStatus(),
) {
    Column(modifier.fillMaxWidth().testTag("hero_month_spend_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Spent this month".uppercase(),
                style = EyebrowText,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            AccountAvatar(avatar = accountAvatar, syncStatus = syncStatus, onClick = onOpenAccount)
        }
        AnimatedRollingNumber(
            targetValue = monthlyExpense,
            prefix = "₹",
            style = MoneyText.hero,
            brush = BrandGradient.horizontal(),
        )
        if (dailyStreak >= 2) {
            Text(
                text = "Logged $dailyStreak days in a row",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * The two figures under the hero: what came in this month, and where the split ledger stands.
 *
 * The debt sits here rather than in the header (owner decision, 2026-09-18), and it keeps the one
 * action it had: tapping it opens "who pays whom", the sheet the header link used to open. With
 * nobody owing anybody it is a plain figure with nothing to tap.
 */
@Composable
fun MonthStrip(
    analytics: MonthlyAnalytics,
    netBalance: Double,
    onOpenSimplifyDebts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owedToMe = netBalance >= 0.005
    val iOwe = netBalance <= -0.005
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        MonthFigure(
            label = "Came in",
            amount = analytics.totalIncome,
            color = MoneyIn,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier
                .padding(horizontal = Spacing.xl)
                .width(1.dp)
                .fillMaxHeight()
                .background(CharcoalBorder)
        )
        MonthFigure(
            label = when {
                owedToMe -> "Friends owe you"
                iOwe -> "You owe"
                else -> "All square"
            },
            amount = abs(netBalance),
            color = when {
                owedToMe -> MoneyIn
                iOwe -> MoneyOut
                else -> MutedWarmGray
            },
            onClick = onOpenSimplifyDebts.takeIf { owedToMe || iOwe },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthFigure(
    label: String,
    amount: Double,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    // No clip and no bounded ripple: the label sits hard against this column's top
                    // left, so a rounded clip bites the first glyph of both lines ("YOU OWE" lost
                    // its Y, "Simplify" its S). A press bounce gives the feedback instead, the way
                    // the account avatar above it already does.
                    Modifier
                        .pressBounce(interactionSource)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = "Simplify who pays whom",
                            onClick = onClick,
                        )
                        .testTag("home_simplify_button")
                }
            )
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(label.uppercase(), style = EyebrowText)
        AnimatedRollingNumber(targetValue = amount, style = MoneyText.title, color = color)
        if (onClick != null) {
            Text(
                text = "Simplify who pays whom",
                style = MaterialTheme.typography.bodySmall,
                color = WarmAmberLight,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}
