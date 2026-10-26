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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import com.vaibhav.kharchapaani.domain.GroupSort
import com.vaibhav.kharchapaani.data.settings.GroupSortPreferences
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
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.components.AccountAvatar
import com.vaibhav.kharchapaani.ui.components.EmptyState
import com.vaibhav.kharchapaani.ui.components.KpButton
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.components.KpButtonTier
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.ChartreuseSecondary
import com.vaibhav.kharchapaani.ui.theme.MoneyIn
import com.vaibhav.kharchapaani.ui.theme.MoneyOut
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.ui.components.SettlementItemCard
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
    modifier: Modifier = Modifier,
    // Settlement confirmation (design item 10): what's waiting on me, and on them.
    awaitingMe: List<ActivityItem.Settlement> = emptyList(),
    awaitingThem: List<ActivityItem.Settlement> = emptyList(),
    onAnswerSettlement: (Long) -> Unit = {},
    onWithdrawSettlement: (Long) -> Unit = {},
    // Ask-to-pay: person id to when you last asked them, for friends who still owe you.
    askedAt: Map<Long, Long> = emptyMap(),
) {
    val owedToMe = personBalances.filter { it.netBalance > 0 }.sumOf { it.netBalance }
    val iOwe = abs(personBalances.filter { it.netBalance < 0 }.sumOf { it.netBalance })
    var section by rememberSaveable { mutableStateOf(SplitsSection.Friends) }
    val context = LocalContext.current
    val sortPreferences = remember(context) { GroupSortPreferences(context) }
    var groupSort by remember(sortPreferences) { mutableStateOf(sortPreferences.read()) }
    val sortedGroups = remember(groups, groupSort) { groupSort.apply(groups) }
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
        if (awaitingMe.isNotEmpty()) {
            item(key = "awaiting_me_header") {
                Text("WAITING FOR YOU", style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).testTag("awaiting_me_header"))
            }
            itemsIndexed(awaitingMe, key = { _, it -> "awaiting_me_${it.settlement.id}" }) { index, item ->
                SettlementItemCard(
                    item = item,
                    onRetract = {},
                    onAnswer = { onAnswerSettlement(item.settlement.id) },
                    position = RowPosition.of(index, awaitingMe.size),
                    modifier = Modifier.animateItem().padding(top = if (index == 0) 0.dp else 2.dp),
                )
            }
        }
        if (awaitingThem.isNotEmpty()) {
            item(key = "awaiting_them_header") {
                Text("WAITING ON OTHERS", style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s))
            }
            itemsIndexed(awaitingThem, key = { _, it -> "awaiting_them_${it.settlement.id}" }) { index, item ->
                SettlementItemCard(
                    item = item,
                    onRetract = { onWithdrawSettlement(item.settlement.id) },
                    position = RowPosition.of(index, awaitingThem.size),
                    modifier = Modifier.animateItem().padding(top = if (index == 0) 0.dp else 2.dp),
                )
            }
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
                            icon = KpIcons.Group,
                            testTag = "add_friend_button",
                        )
                    }
                }
                if (friends.isEmpty()) {
                    item(key = "friends_empty") {
                        EmptyState(
                            illustration = R.drawable.kp_empty_friends,
                            title = "No friends yet",
                            body = "Add anyone you split with — a name and a colour is all it takes, and they don't need the app. Splits, IOUs and settling up all work the same. Share a group only when you want them to see it on their own phone.",
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
                        askedAt = askedAt[balance.person.id],
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
                        )
                        KpButton(
                            label = "New group",
                            onClick = onOpenCreateGroup,
                            tier = KpButtonTier.Outline,
                            icon = KpIcons.Add,
                            testTag = "create_group_button",
                        )
                    }
                }
                item(key = "group_sort") {
                    GroupSortPicker(groupSort) {
                        groupSort = it
                        sortPreferences.write(it)
                    }
                }
                if (groups.isEmpty()) {
                    item(key = "groups_empty") {
                        EmptyState(
                            illustration = R.drawable.kp_empty_groups,
                            title = "No groups yet",
                            body = "Make one for a trip or a flat. A shared group keeps everyone you invite in sync; join one with the code a friend sends.",
                        )
                    }
                }
                items(sortedGroups, key = { "group_${it.group.id}" }) { details ->
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
            // Zero is not a direction: a ₹0 debt in the debt colour reads as a debt at a glance.
            SummaryFigure("Owed to you", owedToMe, if (owedToMe >= 0.005) MoneyIn else MutedWarmGray, style, Modifier.weight(1f))
            Box(Modifier.padding(horizontal = Spacing.xl).width(1.dp).fillMaxHeight().background(CharcoalBorder))
            SummaryFigure("You owe", iOwe, if (iOwe >= 0.005) MoneyOut else MutedWarmGray, style, Modifier.weight(1f))
        }
        // Two real actions, not a footnote. As bare amber text with no container and no icon they
        // read as a caption under the figures, out of step with every other row in the app, which
        // pairs an icon with its label. "Log an IOU" in particular creates a record — Ghost is the
        // tier for exactly that: repeatable, low-stakes, and visibly a control.
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.padding(top = Spacing.m),
        ) {
            if (owedToMe >= 0.005 || iOwe >= 0.005) {
                KpButton(
                    label = "Who pays whom",
                    onClick = onOpenSimplifyDebts,
                    tier = KpButtonTier.Ghost,
                    icon = KpIcons.Settle,
                    testTag = "simplify_debts_button",
                )
            }
            KpButton(
                label = "Log an IOU",
                onClick = onOpenIou,
                tier = KpButtonTier.Ghost,
                icon = KpIcons.Lend,
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
    // Left-aligned and tight to the list beneath it. Right-aligned, a single "Add a friend" sat
    // alone in a wide empty band between the tabs and the first row, attached to neither — which
    // is what "feels odd and throws off the page" was looking at. Starting at the left puts it
    // under the tab it belongs to and reads as that list's own toolbar.
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.m, bottom = Spacing.s),
    ) {
        actions()
    }
}


@Composable
private fun GroupSortPicker(sort: GroupSort, onSelect: (GroupSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.testTag("group_sort_button")) {
            Text("Sort: ${sort.label}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            GroupSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    modifier = Modifier.semantics { selected = option == sort },
                    onClick = { expanded = false; onSelect(option) },
                )
            }
        }
    }
}
