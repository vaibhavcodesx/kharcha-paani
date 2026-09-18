package com.vaibhav.kharchapaani.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.ui.components.AnimatedRollingNumber
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.components.DeleteFriendDialog
import com.vaibhav.kharchapaani.ui.components.FriendRow
import com.vaibhav.kharchapaani.ui.components.RowPosition
import com.vaibhav.kharchapaani.ui.components.ScreenHeader
import com.vaibhav.kharchapaani.ui.components.pairedFigureStyle
import com.vaibhav.kharchapaani.ui.components.SegmentedControl
import com.vaibhav.kharchapaani.ui.components.TripCard
import com.vaibhav.kharchapaani.ui.shell.dockClearance
import com.vaibhav.kharchapaani.ui.components.AccountAvatar
import com.vaibhav.kharchapaani.ui.components.KpButton
import com.vaibhav.kharchapaani.ui.components.KpButtonTier
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyOut
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import kotlin.math.abs

private enum class SplitsSection { Friends, Groups }

@Composable
fun SplitsScreen(
    accountAvatar: String = "?",
    onOpenAccount: () -> Unit = {},
    syncStatus: SyncStatus = SyncStatus(),
    personBalances: List<PersonBalance>,
    groups: List<GroupWithDetails>,
    onOpenAddFriend: () -> Unit,
    onDeleteFriend: (Long) -> Unit,
    onOpenCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onSelectGroup: (GroupWithDetails) -> Unit,
    onDeleteGroup: (Long) -> Unit,
    onOpenSimplifyDebts: () -> Unit,
    onOpenIouDialog: () -> Unit,
    onSettleUp: (PersonEntity) -> Unit,
    onViewHistory: (PersonEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val owedToMe = personBalances.filter { it.netBalance > 0 }.sumOf { it.netBalance }
    val iOwe = abs(personBalances.filter { it.netBalance < 0 }.sumOf { it.netBalance })
    var section by rememberSaveable { mutableStateOf(SplitsSection.Friends) }
    var friendToDelete by remember { mutableStateOf<PersonBalance?>(null) }
    // Who needs attention first: the biggest balances either way, then everyone who's settled.
    val friends = remember(personBalances) {
        personBalances.sortedWith(compareBy<PersonBalance> { abs(it.netBalance) < 0.005 }.thenByDescending { abs(it.netBalance) }.thenBy { it.person.name })
    }

    DeleteFriendDialog(
        person = friendToDelete?.person,
        currentBalance = friendToDelete?.netBalance ?: 0.0,
        isOpen = friendToDelete != null,
        onConfirmDelete = { onDeleteFriend(it) },
        onDismiss = { friendToDelete = null }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CharcoalBg),
        contentPadding = PaddingValues(start = Spacing.page, end = Spacing.page, top = Spacing.l, bottom = dockClearance()),
    ) {
        item(key = "header") {
            ScreenHeader(title = "Splits", supporting = "Who owes whom, with friends and on trips") {
                AccountAvatar(avatar = accountAvatar, syncStatus = syncStatus, onClick = onOpenAccount)
            }
        }
        item(key = "summary") {
            BalanceSummary(
                owedToMe = owedToMe,
                iOwe = iOwe,
                onOpenSimplifyDebts = onOpenSimplifyDebts,
                onOpenIou = onOpenIouDialog,
                modifier = Modifier.padding(top = Spacing.l),
            )
        }
        item(key = "sections") {
            SegmentedControl(
                options = SplitsSection.entries,
                selected = section,
                label = {
                    when (it) {
                        SplitsSection.Friends -> "Friends · ${personBalances.size}"
                        SplitsSection.Groups -> "Groups · ${groups.size}"
                    }
                },
                onSelect = { section = it },
                testTagFor = { "splits_tab_${it.name.lowercase()}" },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            )
        }

        when (section) {
            SplitsSection.Friends -> {
                item(key = "friends_header") {
                    SectionActions {
                        KpButton(
                            label = "Add a friend",
                            onClick = onOpenAddFriend,
                            tier = KpButtonTier.Outline,
                            testTag = "add_friend_button",
                        )
                    }
                }
                if (friends.isEmpty()) {
                    item(key = "friends_empty") {
                        EmptySection(
                            title = "No friends yet",
                            body = "Add the people you split with. They don't need the app; to split across phones, share a group with them.",
                        )
                    }
                }
                itemsIndexed(friends, key = { _, it -> "friend_${it.person.id}" }) { index, balance ->
                    FriendRow(
                        balance = balance,
                        position = RowPosition.of(index, friends.size),
                        onOpenHistory = { onViewHistory(balance.person) },
                        onSettleUp = { onSettleUp(balance.person) },
                        onDelete = { friendToDelete = balance },
                        modifier = Modifier.animateItem().padding(top = if (index == 0) 0.dp else 2.dp),
                    )
                }
            }
            SplitsSection.Groups -> {
                item(key = "groups_header") {
                    SectionActions {
                        KpButton(
                            label = "Join",
                            onClick = onJoinGroup,
                            tier = KpButtonTier.Ghost,
                            testTag = "join_group_button",
                            modifier = Modifier.padding(end = Spacing.s),
                        )
                        KpButton(
                            label = "New group",
                            onClick = onOpenCreateGroup,
                            tier = KpButtonTier.Outline,
                            testTag = "create_group_button",
                        )
                    }
                }
                if (groups.isEmpty()) {
                    item(key = "groups_empty") {
                        EmptySection(
                            title = "No groups yet",
                            body = "Make one for a trip or a flat. A shared group keeps everyone you invite in sync; join one with the code a friend sends.",
                        )
                    }
                }
                items(groups, key = { "group_${it.group.id}" }) { details ->
                    TripCard(
                        details = details,
                        onOpen = { onSelectGroup(details) },
                        onDelete = { onDeleteGroup(details.group.id) },
                        modifier = Modifier.animateItem().padding(bottom = Spacing.m),
                    )
                }
            }
        }
    }
}

/**
 * The page's answer in two numbers, and what to do about them: see the fewest payments that clear
 * everything, or log money lent outside an expense.
 */
@Composable
private fun BalanceSummary(
    owedToMe: Double,
    iOwe: Double,
    onOpenSimplifyDebts: () -> Unit,
    onOpenIou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val style = pairedFigureStyle(owedToMe, iOwe)
            SummaryFigure("Owed to you", owedToMe, MoneyIn, style, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = Spacing.xl).width(1.dp).fillMaxHeight().background(CharcoalBorder))
            SummaryFigure("You owe", iOwe, MoneyOut, style, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = Spacing.s).offset(x = -Spacing.m)) {
            if (owedToMe >= 0.005 || iOwe >= 0.005) {
                KpButton(
                    label = "See who pays whom",
                    onClick = onOpenSimplifyDebts,
                    tier = KpButtonTier.Link,
                    testTag = "simplify_debts_button",
                )
            }
            KpButton(
                label = "Log an IOU",
                onClick = onOpenIou,
                tier = KpButtonTier.Link,
                testTag = "record_iou_button",
            )
        }
    }
}

@Composable
private fun SummaryFigure(label: String, amount: Double, color: androidx.compose.ui.graphics.Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}) {
        Text(label.uppercase(), style = EyebrowText)
        AnimatedRollingNumber(targetValue = amount, style = style, color = color)
    }
}

/** A section's create actions, at the end. The Friends | Groups control above already names it. */
@Composable
private fun SectionActions(actions: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.s, bottom = Spacing.xs),
    ) {
        actions()
    }
}

@Composable
private fun EmptySection(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.xl),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.xs))
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
