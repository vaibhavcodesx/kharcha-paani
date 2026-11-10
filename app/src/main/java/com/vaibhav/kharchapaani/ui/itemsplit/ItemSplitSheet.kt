package com.vaibhav.kharchapaani.ui.itemsplit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitDraft
import com.vaibhav.kharchapaani.domain.ItemSplitMath
import com.vaibhav.kharchapaani.domain.ItemSplitShares
import com.vaibhav.kharchapaani.domain.SplitItem
import com.vaibhav.kharchapaani.domain.SplitPerson
import com.vaibhav.kharchapaani.ui.components.ChoiceChip
import com.vaibhav.kharchapaani.ui.components.GlassActionBar
import com.vaibhav.kharchapaani.ui.components.KpButton
import com.vaibhav.kharchapaani.ui.components.KpButtonTier
import com.vaibhav.kharchapaani.ui.components.KpModalSheet
import com.vaibhav.kharchapaani.ui.components.QrCode
import com.vaibhav.kharchapaani.ui.components.RowPosition
import com.vaibhav.kharchapaani.ui.components.SheetRow
import com.vaibhav.kharchapaani.ui.components.SheetScaffold
import com.vaibhav.kharchapaani.ui.components.SheetTextInput
import com.vaibhav.kharchapaani.ui.components.SheetTitle
import com.vaibhav.kharchapaani.ui.components.shimmerLoading
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.util.LegalLinks
import kotlin.math.abs

/** Everything the split sheet can ask for. One object, so the shell passes it through in one line. */
class ItemSplitActions(
    val onDismiss: () -> Unit,
    val onSetActive: (personId: String) -> Unit,
    val onAddPerson: (name: String, ledgerId: Long?) -> Unit,
    val onRemovePerson: (personId: String) -> Unit,
    val onToggle: (itemId: String) -> Unit,
    val onSetWeight: (itemId: String, weight: Int) -> Unit,
    val onSaveItem: (id: String?, name: String, quantity: Int, amountPaise: Long) -> Unit,
    val onRemoveItem: (itemId: String) -> Unit,
    val onReadItems: () -> Unit,
    val onShare: () -> Unit,
    val onStopSharing: () -> Unit,
    val onSendLink: (link: String) -> Unit,
    val onUse: () -> Unit,
)

/** A friend who could be added to the table: their id in the ledger and their name. */
data class TableCandidate(val ledgerId: Long, val name: String)

/**
 * Split a bill by who had what (docs/designs/split-by-items.md). Pick a person, tap their items;
 * or share the link and let everyone tap their own. "Use this split" hands each person's amount
 * back to the entry sheet.
 */
@Composable
fun ItemSplitSheet(state: ItemSplitUiState, candidates: List<TableCandidate>, actions: ItemSplitActions) {
    val draft = state.draft
    if (!state.isOpen || draft == null) return
    KpModalSheet(onDismiss = actions.onDismiss) {
        ItemSplitContent(state = state, draft = draft, candidates = candidates, actions = actions)
    }
}

/** The sheet's body without its window, so it can be rendered and tested on its own. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemSplitContent(
    state: ItemSplitUiState,
    draft: ItemSplitDraft,
    candidates: List<TableCandidate>,
    actions: ItemSplitActions,
    modifier: Modifier = Modifier,
) {
    val shares = remember(draft.items, draft.people, draft.claims, draft.totalPaise) { ItemSplitMath.shares(draft.split) }
    val active = draft.people.firstOrNull { it.id == draft.activePersonId } ?: draft.host
    val you = stringResource(R.string.item_split_you)
    fun nameOf(person: SplitPerson) = if (person.isHost) you else person.name
    val busy = state.startingShare

    var addingPerson by rememberSaveable { mutableStateOf(false) }
    // The item being changed: its id, "" for a new one, null when the editor is closed.
    var editingItem by rememberSaveable { mutableStateOf<String?>(null) }

    if (addingPerson) {
        AddPersonDialog(
            candidates = candidates.filter { c -> draft.people.none { it.ledgerId == c.ledgerId } },
            onAdd = { name, ledgerId ->
                actions.onAddPerson(name, ledgerId)
                addingPerson = false
            },
            onDismiss = { addingPerson = false },
        )
    }
    editingItem?.let { id ->
        ItemEditorDialog(
            item = draft.items.firstOrNull { it.id == id },
            onSave = { name, quantity, paise ->
                actions.onSaveItem(id.ifEmpty { null }, name, quantity, paise)
                editingItem = null
            },
            onRemove = {
                actions.onRemoveItem(id)
                editingItem = null
            },
            onDismiss = { editingItem = null },
        )
    }

    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = stringResource(R.string.item_split_use),
                disabledLabel = stringResource(if (draft.items.isEmpty()) R.string.item_split_use_need_items else R.string.item_split_use_need_people),
                enabled = draft.items.isNotEmpty() && draft.people.size > 1 && !busy && !state.reading,
                onClick = actions.onUse,
                summary = rupees(draft.totalPaise),
                detail = pluralStringResource(R.plurals.item_split_people_count, draft.people.size, draft.people.size),
                buttonTestTag = "item_split_use_button",
            )
        },
    ) {
        SheetTitle(
            title = stringResource(R.string.item_split_title),
            eyebrow = draft.title.takeIf { it.isNotBlank() },
            supporting = stringResource(R.string.item_split_supporting),
        )

        // Who is at the table, and whose picks a tap changes.
        Text(stringResource(R.string.item_split_table), style = EyebrowText, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
            draft.people.forEach { person ->
                ChoiceChip(
                    label = if (person.done) stringResource(R.string.item_split_person_done, nameOf(person)) else nameOf(person),
                    selected = person.id == active?.id,
                    onClick = { actions.onSetActive(person.id) },
                    modifier = Modifier.testTag("item_split_person_${person.name}"),
                )
            }
            ChoiceChip(
                label = stringResource(R.string.item_split_add_person),
                selected = false,
                icon = KpIcons.Add,
                role = Role.Button,
                enabled = !busy,
                onClick = { addingPerson = true },
                modifier = Modifier.testTag("item_split_add_person"),
            )
        }
        if (active != null && !active.isHost) {
            KpButton(
                label = stringResource(R.string.item_split_remove_person, active.name),
                onClick = { actions.onRemovePerson(active.id) },
                tier = KpButtonTier.Link,
                enabled = !busy,
                testTag = "item_split_remove_person",
            )
        }

        // The bill.
        Text(
            text = if (active == null || active.isHost) stringResource(R.string.item_split_items_you) else stringResource(R.string.item_split_items_for, active.name),
            style = EyebrowText,
            modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).semantics { heading() },
        )
        when {
            state.reading -> Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(stringResource(R.string.item_split_reading), style = MaterialTheme.typography.bodyMedium, color = MutedWarmGray, modifier = Modifier.padding(bottom = Spacing.s))
                repeat(4) { Box(Modifier.fillMaxWidth().height(56.dp).clip(KpShapes.medium).background(CharcoalCard).shimmerLoading()) }
            }
            draft.items.isEmpty() -> Column(
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
                modifier = Modifier.fillMaxWidth().clip(KpShapes.large).background(CharcoalCard).padding(Spacing.l),
            ) {
                Text(stringResource(R.string.item_split_empty), style = MaterialTheme.typography.bodyMedium, color = MutedWarmGray)
                if (state.canRead) {
                    KpButton(
                        label = stringResource(R.string.item_split_read_photo),
                        onClick = actions.onReadItems,
                        tier = KpButtonTier.Outline,
                        icon = KpIcons.Scan,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "item_split_read_photo",
                    )
                }
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                draft.items.forEachIndexed { index, item ->
                    ItemRow(
                        item = item,
                        position = RowPosition.of(index, draft.items.size),
                        claimers = draft.claims.filter { it.itemId == item.id && it.weight > 0 }
                            .mapNotNull { claim -> draft.people.firstOrNull { it.id == claim.personId }?.let { nameOf(it) to claim.weight } },
                        activeName = active?.let { nameOf(it) }.orEmpty(),
                        activeIsHost = active?.isHost != false,
                        activeWeight = draft.claims.firstOrNull { it.itemId == item.id && it.personId == active?.id }?.weight ?: 0,
                        activePart = active?.let { shares.parts[item.id]?.get(it.id) },
                        enabled = !busy,
                        onToggle = { actions.onToggle(item.id) },
                        onSetWeight = { actions.onSetWeight(item.id, it) },
                        onEdit = { editingItem = item.id },
                    )
                }
            }
        }
        if (!state.reading) {
            KpButton(
                label = stringResource(R.string.item_split_add_item),
                onClick = { editingItem = "" },
                tier = KpButtonTier.Link,
                icon = KpIcons.Add,
                enabled = !busy,
                testTag = "item_split_add_item",
            )
        }

        if (draft.items.isNotEmpty()) {
            BillSums(shares = shares, totalPaise = draft.totalPaise)
            SharesCard(draft = draft, shares = shares, nameOf = ::nameOf)
            ShareWithTable(state = state, draft = draft, actions = actions)
        }
    }
}

@Composable
private fun ItemRow(
    item: SplitItem,
    position: RowPosition,
    claimers: List<Pair<String, Int>>,
    activeName: String,
    activeIsHost: Boolean,
    activeWeight: Int,
    activePart: Long?,
    enabled: Boolean,
    onToggle: () -> Unit,
    onSetWeight: (Int) -> Unit,
    onEdit: () -> Unit,
) {
    val picked = activeWeight > 0
    val pickedState = stringResource(if (picked) R.string.item_split_state_picked else R.string.item_split_state_not_picked, activeName)
    Column(Modifier.fillMaxWidth().clip(position.shape).background(if (picked) WarmAmberPrimary.copy(alpha = 0.12f) else CharcoalCard)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 60.dp)
                    .toggleable(value = picked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                    .semantics { stateDescription = pickedState }
                    .padding(start = Spacing.l, top = Spacing.s, bottom = Spacing.s)
                    .testTag("item_split_item_${item.name}"),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(Tick)
                        .background(if (picked) WarmAmberPrimary else CharcoalCard)
                        .border(1.dp, if (picked) WarmAmberPrimary else OutlineInteractive, Tick),
                ) {
                    if (picked) Icon(KpIcons.Check, contentDescription = null, tint = CharcoalBg, modifier = Modifier.size(16.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = Spacing.m)) {
                    Text(
                        text = if (item.quantity > 1) stringResource(R.string.item_split_item_with_quantity, item.name, item.quantity) else item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OffWhiteText,
                    )
                    Text(
                        text = when {
                            claimers.isEmpty() -> stringResource(R.string.item_split_nobody_yet)
                            // Their part is said whenever it isn't simply the price.
                            picked && claimers.size > 1 && activePart != null && activeIsHost ->
                                stringResource(R.string.item_split_part_you, claimers.joinToString { it.first }, rupees(activePart))
                            picked && claimers.size > 1 && activePart != null ->
                                stringResource(R.string.item_split_part_other, claimers.joinToString { it.first }, activeName, rupees(activePart))
                            else -> claimers.joinToString { it.first }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (claimers.isEmpty()) SubMutedWarmGray else MutedWarmGray,
                    )
                }
                Text(rupees(item.amountPaise), style = MoneyText.row, color = OffWhiteText, maxLines = 1)
            }
            IconButton(onClick = onEdit, enabled = enabled, modifier = Modifier.size(48.dp)) {
                Icon(KpIcons.Write, contentDescription = stringResource(R.string.item_split_edit_item, item.name), tint = MutedWarmGray, modifier = Modifier.size(18.dp))
            }
        }
        if (picked && item.quantity > 1) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = Spacing.m, bottom = Spacing.s),
            ) {
                Text(
                    text = if (activeIsHost) stringResource(R.string.item_split_how_many_you) else stringResource(R.string.item_split_how_many, activeName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedWarmGray,
                    modifier = Modifier.weight(1f),
                )
                StepButton(label = "−", description = stringResource(R.string.item_split_fewer), enabled = enabled && activeWeight > 1) { onSetWeight(activeWeight - 1) }
                Text(
                    text = activeWeight.toString(),
                    style = MoneyText.row,
                    color = OffWhiteText,
                    modifier = Modifier.width(32.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                StepButton(
                    label = "+",
                    description = stringResource(R.string.item_split_more),
                    enabled = enabled && activeWeight < minOf(item.quantity, ItemSplitController.MAX_WEIGHT),
                ) { onSetWeight(activeWeight + 1) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(KpShapes.small)
            .border(1.dp, if (enabled) OutlineInteractive else CharcoalBorder, KpShapes.small)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        Text(label, style = MoneyText.title, color = if (enabled) OffWhiteText else SubMutedWarmGray)
    }
}

/** Items, what the bill adds or takes off on top of them, and the total: the three have to agree. */
@Composable
private fun BillSums(shares: ItemSplitShares, totalPaise: Long) {
    Column(
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        modifier = Modifier.padding(top = Spacing.l).fillMaxWidth().clip(KpShapes.large).background(CharcoalCard).padding(Spacing.l),
    ) {
        SumLine(stringResource(R.string.item_split_sum_items), rupees(shares.itemsPaise))
        if (shares.extrasPaise != 0L) {
            SumLine(
                stringResource(if (shares.extrasPaise > 0) R.string.item_split_sum_extras else R.string.item_split_sum_discount),
                rupees(abs(shares.extrasPaise)),
            )
        }
        SumLine(stringResource(R.string.item_split_sum_total), rupees(totalPaise), strong = true)
        // Tax and service charge together rarely pass a third of the bill; more means a misread price.
        if (shares.itemsPaise > 0 && abs(shares.extrasPaise) * 100 > shares.itemsPaise * 35) {
            Text(
                stringResource(R.string.item_split_mismatch),
                style = MaterialTheme.typography.bodySmall,
                color = WarmAmberLight,
                modifier = Modifier.padding(top = Spacing.xs).testTag("item_split_mismatch"),
            )
        }
    }
}

@Composable
private fun SumLine(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (strong) OffWhiteText else MutedWarmGray, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MoneyText.title else MoneyText.row, color = OffWhiteText, maxLines = 1)
    }
}

@Composable
private fun SharesCard(draft: ItemSplitDraft, shares: ItemSplitShares, nameOf: (SplitPerson) -> String) {
    Text(stringResource(R.string.item_split_shares), style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).semantics { heading() })
    Column(Modifier.fillMaxWidth().clip(KpShapes.large).background(CharcoalCard).padding(horizontal = Spacing.l, vertical = Spacing.s)) {
        draft.people.forEach { person ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(nameOf(person), style = MaterialTheme.typography.bodyLarge, color = OffWhiteText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(
                    rupees(shares.people[person.id]?.totalPaise ?: 0),
                    style = MoneyText.row,
                    color = OffWhiteText,
                    maxLines = 1,
                    modifier = Modifier.testTag("item_split_share_${person.name}"),
                )
            }
        }
    }
    if (shares.unclaimedCount > 0) {
        Text(
            pluralStringResource(R.plurals.item_split_unpicked, shares.unclaimedCount, shares.unclaimedCount, rupees(shares.unclaimedPaise)),
            style = MaterialTheme.typography.bodySmall,
            color = MutedWarmGray,
            modifier = Modifier.padding(top = Spacing.s).testTag("item_split_unpicked"),
        )
    }
}

/** The link: off, being made, or up with its code. Without an account there's nobody to share as. */
@Composable
private fun ShareWithTable(state: ItemSplitUiState, draft: ItemSplitDraft, actions: ItemSplitActions) {
    Text(stringResource(R.string.item_split_share_title), style = EyebrowText, modifier = Modifier.padding(top = Spacing.xl, bottom = Spacing.s).semantics { heading() })
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.m),
        modifier = Modifier.fillMaxWidth().clip(KpShapes.large).background(CharcoalCard).padding(Spacing.l),
    ) {
        val link = draft.link
        when {
            !state.canShare -> Text(stringResource(R.string.item_split_share_local), style = MaterialTheme.typography.bodyMedium, color = MutedWarmGray, modifier = Modifier.fillMaxWidth())
            link != null -> {
                val url = LegalLinks.splitLink(link.token)
                QrCode(text = url, contentDescription = stringResource(R.string.item_split_share_scan), size = 184.dp)
                Text(stringResource(R.string.item_split_share_scan), style = MaterialTheme.typography.bodyLarge, color = OffWhiteText)
                Text(stringResource(R.string.item_split_share_expires), style = MaterialTheme.typography.bodySmall, color = MutedWarmGray)
                KpButton(
                    label = stringResource(R.string.item_split_share_send),
                    onClick = { actions.onSendLink(url) },
                    tier = KpButtonTier.Outline,
                    icon = KpIcons.Send,
                    fillWidth = true,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "item_split_send_link",
                )
                KpButton(label = stringResource(R.string.item_split_share_stop), onClick = actions.onStopSharing, tier = KpButtonTier.Link, testTag = "item_split_stop_sharing")
            }
            else -> {
                Text(stringResource(R.string.item_split_share_body), style = MaterialTheme.typography.bodyMedium, color = MutedWarmGray, modifier = Modifier.fillMaxWidth())
                if (state.startingShare) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 52.dp).semantics { liveRegion = LiveRegionMode.Polite }) {
                        CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.s))
                        Text(stringResource(R.string.item_split_share_starting), style = MaterialTheme.typography.bodyMedium, color = MutedWarmGray)
                    }
                } else {
                    KpButton(
                        label = stringResource(R.string.item_split_share_button),
                        onClick = actions.onShare,
                        tier = KpButtonTier.Outline,
                        icon = KpIcons.Send,
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "item_split_share_button",
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPersonDialog(candidates: List<TableCandidate>, onAdd: (name: String, ledgerId: Long?) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val typed = name.trim()
    val shown = remember(candidates, typed) {
        candidates.filter { typed.isEmpty() || it.name.contains(typed, ignoreCase = true) }.take(MAX_CANDIDATES)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            modifier = Modifier.fillMaxWidth().clip(KpShapes.large).background(CharcoalSurface).padding(Spacing.l).verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.item_split_add_person_title), style = MaterialTheme.typography.titleLarge, color = OffWhiteText, modifier = Modifier.semantics { heading() })
            SheetRow(label = stringResource(R.string.item_split_add_person_name)) {
                SheetTextInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.item_split_add_person_hint),
                    fieldTestTag = "item_split_person_name_input",
                    maxLength = ItemSplitController.MAX_NAME,
                )
            }
            if (shown.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                    shown.forEach { candidate ->
                        ChoiceChip(label = candidate.name, selected = false, role = Role.Button, onClick = { onAdd(candidate.name, candidate.ledgerId) })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s, Alignment.End), modifier = Modifier.fillMaxWidth()) {
                KpButton(label = stringResource(R.string.item_split_cancel), onClick = onDismiss, tier = KpButtonTier.Link)
                KpButton(
                    label = stringResource(R.string.item_split_add_person_add),
                    // A typed name that is a friend's is that friend, not a second person with their name.
                    onClick = { onAdd(typed, candidates.firstOrNull { it.name.equals(typed, ignoreCase = true) }?.ledgerId) },
                    tier = KpButtonTier.Primary,
                    enabled = typed.isNotEmpty(),
                    testTag = "item_split_person_add_button",
                )
            }
        }
    }
}

@Composable
private fun ItemEditorDialog(item: SplitItem?, onSave: (name: String, quantity: Int, amountPaise: Long) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(item?.name.orEmpty()) }
    var quantity by rememberSaveable { mutableStateOf(item?.quantity?.toString() ?: "1") }
    var amount by rememberSaveable { mutableStateOf(item?.let { Money.input(ItemSplitMath.rupees(it.amountPaise)) }.orEmpty()) }
    val price = Money.parseInput(amount)
    val count = quantity.toIntOrNull()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().clip(KpShapes.large).background(CharcoalSurface).padding(Spacing.l).verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(if (item == null) R.string.item_split_item_new else R.string.item_split_item_change),
                style = MaterialTheme.typography.titleLarge,
                color = OffWhiteText,
                modifier = Modifier.padding(bottom = Spacing.m).semantics { heading() },
            )
            SheetRow(label = stringResource(R.string.item_split_item_name), position = RowPosition.First) {
                SheetTextInput(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = stringResource(R.string.item_split_item_name_hint),
                    fieldTestTag = "item_split_item_name_input",
                    maxLength = ItemSplitController.MAX_ITEM_NAME,
                )
            }
            SheetRow(label = stringResource(R.string.item_split_item_quantity), position = RowPosition.Middle) {
                SheetTextInput(
                    value = quantity,
                    onValueChange = { typed -> if (typed.all(Char::isDigit)) quantity = typed },
                    placeholder = "1",
                    keyboardType = KeyboardType.Number,
                    fieldTestTag = "item_split_item_quantity_input",
                    maxLength = 2,
                )
            }
            SheetRow(label = stringResource(R.string.item_split_item_price), position = RowPosition.Last) {
                SheetTextInput(
                    value = amount,
                    onValueChange = { typed -> Money.acceptInput(typed)?.let { amount = it } },
                    placeholder = "0",
                    keyboardType = KeyboardType.Decimal,
                    fieldTestTag = "item_split_item_price_input",
                    prefix = "₹",
                )
            }
            Text(
                stringResource(R.string.item_split_item_price_note),
                style = MaterialTheme.typography.bodySmall,
                color = MutedWarmGray,
                modifier = Modifier.padding(top = Spacing.s, bottom = Spacing.m),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (item != null) {
                    KpButton(label = stringResource(R.string.item_split_item_remove), onClick = onRemove, tier = KpButtonTier.Link, icon = KpIcons.Delete, testTag = "item_split_item_remove_button")
                }
                Spacer(Modifier.weight(1f))
                KpButton(label = stringResource(R.string.item_split_cancel), onClick = onDismiss, tier = KpButtonTier.Link)
                KpButton(
                    label = stringResource(R.string.item_split_item_save),
                    onClick = { onSave(name, count ?: 1, ItemSplitMath.paise(price ?: 0.0)) },
                    tier = KpButtonTier.Primary,
                    enabled = name.isNotBlank() && price != null && price > 0 && (count ?: 0) >= 1,
                    testTag = "item_split_item_save_button",
                )
            }
        }
    }
}

private fun rupees(paise: Long): String = Money.format(ItemSplitMath.rupees(paise))

private val Tick = SquircleShape(7.dp)
private const val MAX_CANDIDATES = 12
