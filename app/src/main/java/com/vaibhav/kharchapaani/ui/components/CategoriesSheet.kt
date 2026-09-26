package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

@Composable
fun CategoriesSheet(isOpen: Boolean, prefs: CategoryPrefs, onChange: ((CategoryPrefs) -> CategoryPrefs) -> Unit, onDismiss: () -> Unit) {
    if (!isOpen) return
    KpModalSheet(onDismiss = onDismiss) { CategoriesContent(prefs, onChange) }
}

private enum class ListKind { Categories, PaymentMethods }

/**
 * Hide the defaults you never use and add your own. Changes apply to pickers from now on; entries
 * already logged keep the category or method they have.
 */
@Composable
fun CategoriesContent(prefs: CategoryPrefs, onChange: ((CategoryPrefs) -> CategoryPrefs) -> Unit, modifier: Modifier = Modifier) {
    var kind by rememberSaveable { mutableStateOf(ListKind.Categories) }

    SheetScaffold(modifier = modifier) {
        SheetTitle(
            title = "Categories & payment methods",
            supporting = "Hide the ones you never use and add your own. Entries you've logged keep theirs.",
        )
        SegmentedControl(
            options = ListKind.entries,
            selected = kind,
            label = { if (it == ListKind.Categories) "Categories" else "Payment methods" },
            onSelect = { kind = it },
            testTagFor = { "categories_tab_${it.name}" },
            modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xl),
        )

        when (kind) {
            ListKind.Categories -> {
                DefaultChoice(
                    title = "Category",
                    value = Categories.defaultCategory("expense", prefs),
                    choices = Categories.expenseCategories(prefs),
                    withIcons = true,
                    tag = "default_category",
                    onPick = { picked -> onChange { it.copy(defaultCategory = picked) } },
                )
                ChoiceSection(
                    title = "Spending",
                    defaults = Categories.defaultExpense,
                    custom = prefs.customExpense,
                    hidden = prefs.hiddenCategories,
                    locked = setOf(Categories.OTHER),
                    showBadge = true,
                    addLabel = "New spending category",
                    tag = "expense",
                    existing = Categories.defaultExpense + Categories.defaultIncome + prefs.customExpense + prefs.customIncome,
                    onToggleHidden = { name, hide -> onChange { it.copy(hiddenCategories = if (hide) it.hiddenCategories + name else it.hiddenCategories - name) } },
                    onAdd = { name -> onChange { it.copy(customExpense = it.customExpense + name) } },
                    onRemove = { name -> onChange { it.copy(customExpense = it.customExpense - name) } },
                )
                ChoiceSection(
                    title = "Income",
                    defaults = Categories.defaultIncome,
                    custom = prefs.customIncome,
                    hidden = prefs.hiddenCategories,
                    locked = setOf(Categories.OTHER_INCOME),
                    showBadge = true,
                    addLabel = "New income category",
                    tag = "income",
                    existing = Categories.defaultExpense + Categories.defaultIncome + prefs.customExpense + prefs.customIncome,
                    onToggleHidden = { name, hide -> onChange { it.copy(hiddenCategories = if (hide) it.hiddenCategories + name else it.hiddenCategories - name) } },
                    onAdd = { name -> onChange { it.copy(customIncome = it.customIncome + name) } },
                    onRemove = { name -> onChange { it.copy(customIncome = it.customIncome - name) } },
                    modifier = Modifier.padding(top = Spacing.xl),
                )
            }
            ListKind.PaymentMethods -> {
                DefaultChoice(
                    title = "Paid with",
                    value = Categories.defaultPaymentMethod(prefs),
                    choices = Categories.paymentMethods(prefs),
                    withIcons = false,
                    tag = "default_payment",
                    onPick = { picked -> onChange { it.copy(defaultPaymentMethod = picked) } },
                )
                ChoiceSection(
                    title = "Payment methods",
                    defaults = Categories.defaultPaymentMethods,
                    custom = prefs.customPaymentMethods,
                    hidden = prefs.hiddenPaymentMethods,
                    locked = setOf(Categories.OTHER),
                    showBadge = false,
                    addLabel = "New payment method",
                    tag = "payment",
                    existing = Categories.defaultPaymentMethods + prefs.customPaymentMethods,
                    onToggleHidden = { name, hide -> onChange { it.copy(hiddenPaymentMethods = if (hide) it.hiddenPaymentMethods + name else it.hiddenPaymentMethods - name) } },
                    onAdd = { name -> onChange { it.copy(customPaymentMethods = it.customPaymentMethods + name) } },
                    onRemove = { name -> onChange { it.copy(customPaymentMethods = it.customPaymentMethods - name) } },
                )
            }
        }
    }
}

/**
 * What a new entry starts on. Only the choices still offered can be picked, and the value shown is
 * what a new entry will actually get: if the chosen one is hidden later, that's the first one left.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultChoice(
    title: String,
    value: String,
    choices: List<String>,
    withIcons: Boolean,
    tag: String,
    onPick: (String) -> Unit,
) {
    var open by rememberSaveable(tag) { mutableStateOf(false) }
    Column(Modifier.padding(bottom = Spacing.xl).testTag(tag)) {
        Text("New entries".uppercase(), style = EyebrowText, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
        ExpandableDetail(
            label = title,
            value = value,
            icon = if (withIcons) CategoryHelper.getCategoryIcon(value) else KpIcons.Card,
            iconTint = if (withIcons) CategoryHelper.getCategoryColor(value) else MutedWarmGray,
            expanded = open,
            onToggle = { open = !open },
            position = RowPosition.Single,
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                choices.forEach { choice ->
                    ChoiceChip(
                        label = choice,
                        selected = choice == value,
                        icon = if (withIcons) CategoryHelper.getCategoryIcon(choice) else null,
                        iconTint = if (withIcons) CategoryHelper.getCategoryColor(choice) else MutedWarmGray,
                        accent = if (withIcons) CategoryHelper.getCategoryColor(choice) else WarmAmberPrimary,
                        onClick = {
                            onPick(choice)
                            open = false
                        },
                        modifier = Modifier.testTag("${tag}_$choice"),
                    )
                }
            }
        }
        Text(
            text = "What a new entry starts on. Entries you've logged keep theirs.",
            style = MaterialTheme.typography.bodySmall,
            color = SubMutedWarmGray,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

@Composable
private fun ChoiceSection(
    title: String,
    defaults: List<String>,
    custom: List<String>,
    hidden: Set<String>,
    locked: Set<String>,
    showBadge: Boolean,
    addLabel: String,
    tag: String,
    existing: List<String>,
    onToggleHidden: (name: String, hide: Boolean) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(tag) { mutableStateOf("") }
    val cleanDraft = Categories.cleanNewName(draft, existing)
    // Custom ones sit before the locked "Other", matching the pickers.
    val rows = defaults.filterNot { it in locked } + custom + defaults.filter { it in locked }

    Column(modifier) {
        Text(title.uppercase(), style = EyebrowText, modifier = Modifier.padding(bottom = Spacing.s).semantics { heading() })
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rows.forEachIndexed { index, name ->
                val isCustom = name in custom
                ChoiceRow(
                    name = name,
                    showBadge = showBadge,
                    position = RowPosition.of(index, rows.size + 1),
                    tag = "choice_${tag}_$name",
                    trailing = {
                        when {
                            isCustom -> IconButton(onClick = { onRemove(name) }, modifier = Modifier.testTag("remove_${tag}_$name")) {
                                Icon(KpIcons.Delete, contentDescription = "Remove $name", tint = MutedWarmGray, modifier = Modifier.size(20.dp))
                            }
                            name in locked -> Text("Always shown", style = MaterialTheme.typography.bodySmall, color = SubMutedWarmGray)
                            // A quiet tick, not a switch: a column of bright switches drew the eye away
                            // from the names (seen in the render review).
                            name !in hidden -> Icon(KpIcons.Check, contentDescription = null, tint = WarmAmberLight, modifier = Modifier.padding(end = Spacing.s).size(20.dp))
                            else -> Text("Hidden", style = MaterialTheme.typography.bodySmall, color = SubMutedWarmGray, modifier = Modifier.padding(end = Spacing.s))
                        }
                    },
                    toggle = if (!isCustom && name !in locked) ({ onToggleHidden(name, name !in hidden) }) else null,
                    shown = name !in hidden,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RowPosition.Last.shape)
                    .background(CharcoalCard)
                    .padding(start = Spacing.l, end = Spacing.xs),
            ) {
                SheetTextInput(value = draft, onValueChange = { draft = it }, placeholder = addLabel, fieldTestTag = "add_${tag}_input")
                TextButton(
                    onClick = {
                        cleanDraft?.let(onAdd)
                        draft = ""
                    },
                    enabled = cleanDraft != null,
                    modifier = Modifier.testTag("add_${tag}_button"),
                ) {
                    Text("Add", style = MaterialTheme.typography.labelLarge, color = if (cleanDraft != null) WarmAmberLight else SubMutedWarmGray)
                }
            }
        }
        if (draft.isNotBlank() && cleanDraft == null) {
            Text(
                text = if (draft.trim().length > Categories.MAX_NAME_LENGTH) "Keep it to ${Categories.MAX_NAME_LENGTH} characters." else "That one's already there.",
                style = MaterialTheme.typography.bodySmall,
                color = WarmAmberLight,
                modifier = Modifier.padding(top = Spacing.s),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    name: String,
    showBadge: Boolean,
    position: RowPosition,
    tag: String,
    shown: Boolean,
    toggle: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .then(if (toggle != null) Modifier.toggleable(value = shown, role = Role.Checkbox, onValueChange = { toggle() }) else Modifier)
            .padding(start = Spacing.l, end = Spacing.m, top = Spacing.xs, bottom = Spacing.xs)
            .testTag(tag),
    ) {
        if (showBadge) CategoryBadge(name, Modifier.padding(end = Spacing.m))
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (shown) OffWhiteText else SubMutedWarmGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}
