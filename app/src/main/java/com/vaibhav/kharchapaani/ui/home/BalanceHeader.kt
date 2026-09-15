package com.vaibhav.kharchapaani.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.components.bounceClick
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import kotlin.math.abs

/**
 * The top of Home is the balance itself: what the split ledger nets to, in the brand gradient, with
 * the one thing to do about it next to it. No app-title row above it.
 */
@Composable
fun BalanceHeader(
    netBalance: Double,
    accountInitial: String,
    onOpenAccount: () -> Unit,
    onOpenSimplifyDebts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owedToMe = netBalance >= 0.005
    val iOwe = netBalance <= -0.005
    val eyebrow = when {
        owedToMe -> "Friends owe you"
        iOwe -> "You owe"
        else -> "All square"
    }

    Column(modifier.fillMaxWidth().testTag("hero_net_balance_card")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = eyebrow.uppercase(),
                style = EyebrowText,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            AccountAvatar(initial = accountInitial, onClick = onOpenAccount)
        }
        AnimatedRollingNumber(
            targetValue = abs(netBalance),
            prefix = "₹",
            style = MoneyText.hero,
            brush = BrandGradient.horizontal(),
        )
        if (owedToMe || iOwe) {
            TextButton(
                onClick = onOpenSimplifyDebts,
                // Pulled left by the button's own inset so its label lines up with the number.
                modifier = Modifier.offset(x = -Spacing.m).testTag("home_simplify_button"),
            ) {
                Text("Simplify who pays whom", style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
            }
        } else {
            Text(
                text = "Nobody owes anybody right now.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun AccountAvatar(initial: String, onClick: () -> Unit) {
    // One clickable rather than IconButton + bounceClick, so TalkBack sees a single "Account" button.
    // Compose expands the touch target to 48dp.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(WarmAmberPrimary.copy(alpha = 0.18f))
            .bounceClick(onClick = onClick)
            .semantics {
                contentDescription = "Account"
                role = Role.Button
            }
            .testTag("home_account_button"),
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleLarge,
            color = WarmAmberLight,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** This month at a glance: what I spent (my share) against what came in. */
@Composable
fun MonthStrip(analytics: MonthlyAnalytics, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
    ) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            MonthFigure(label = "Spent this month", amount = analytics.totalExpense, color = OffWhiteText, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .padding(horizontal = Spacing.l)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(CharcoalBorder)
            )
            MonthFigure(label = "Came in", amount = analytics.totalIncome, color = ChartreuseSecondary, modifier = Modifier.weight(1f))
        }
        if (analytics.dailyStreak >= 2) {
            Text(
                text = "Logged ${analytics.dailyStreak} days in a row",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}

@Composable
private fun MonthFigure(label: String, amount: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label.uppercase(), style = EyebrowText)
        AnimatedRollingNumber(targetValue = amount, style = MoneyText.title, color = color)
    }
}
