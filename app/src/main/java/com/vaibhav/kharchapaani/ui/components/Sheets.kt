package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.GlassPanel
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.LocalHazeState
import com.vaibhav.kharchapaani.ui.theme.MoneyText
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

/**
 * The one sheet window every task in the app opens in: full height from the start, the shared grab
 * bar, no close button (swipe down or back). Focus is cleared on dismiss so the keyboard goes too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KpModalSheet(
    onDismiss: () -> Unit,
    discardGuard: DiscardGuard? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val guard by rememberUpdatedState(discardGuard)
    var askingToDiscard by remember { mutableStateOf(false) }
    var discarding by remember { mutableStateOf(false) }
    val mustAsk = { !discarding && guard?.hasUnsavedInput?.invoke() == true }

    // A swipe down or a tap outside is refused while there's something to lose, and asks instead.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && mustAsk()) {
                askingToDiscard = true
                false
            } else {
                true
            }
        },
    )
    ModalBottomSheet(
        onDismissRequest = {
            if (mustAsk()) {
                // Back hides the sheet without asking the state first, so bring it back to ask.
                askingToDiscard = true
                scope.launch { sheetState.show() }
            } else {
                focusManager.clearFocus()
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = CharcoalSurface,
        contentColor = OffWhiteText,
        shape = KpShapes.sheet,
        dragHandle = { SheetHandle() },
    ) {
        content()
        val shownGuard = guard
        if (askingToDiscard && shownGuard != null) {
            DiscardDialog(
                guard = shownGuard,
                onKeepEditing = { askingToDiscard = false },
                onDiscard = {
                    askingToDiscard = false
                    discarding = true
                    focusManager.clearFocus()
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
            )
        }
    }
}

/** Makes a sheet ask before closing while [hasUnsavedInput] says closing would lose something. */
class DiscardGuard(
    val title: String,
    val message: String,
    val hasUnsavedInput: () -> Boolean,
)

@Composable
private fun DiscardDialog(guard: DiscardGuard, onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        containerColor = CharcoalSurface,
        shape = KpShapes.large,
        title = { Text(guard.title, style = MaterialTheme.typography.headlineMedium) },
        text = { Text(guard.message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", style = MaterialTheme.typography.labelLarge, color = DestructiveText)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text("Keep editing", style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
            }
        },
        titleContentColor = OffWhiteText,
    )
}

/**
 * A sheet's body scrolling under a floating [actionBar]. The bar samples this body for its glass, and
 * both live in the sheet's own window; the body leaves room at the end so its last row can scroll
 * clear of the bar. With no bar, the body just scrolls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SheetScaffold(
    modifier: Modifier = Modifier,
    actionBar: (@Composable BoxScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    val hazeState = rememberHazeState()
    val density = LocalDensity.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val clearOfBar = remember(density, navBarBottom, actionBar != null) {
        ClearOfBarSpec(if (actionBar != null) with(density) { (ActionBarClearance + navBarBottom).toPx() } else 0f)
    }
    Box(modifier.fillMaxWidth().imePadding()) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides clearOfBar) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .hazeSource(hazeState)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.page),
            ) {
                body()
                Spacer(Modifier.navigationBarsPadding().height(if (actionBar != null) ActionBarClearance else Spacing.xl))
            }
        }
        if (actionBar != null) {
            CompositionLocalProvider(LocalHazeState provides hazeState) {
                Box(Modifier.align(Alignment.BottomCenter), content = actionBar)
            }
        }
    }
}

/**
 * The sheet's primary action, floating in Liquid Glass. The glass is only the rim: an optional
 * summary and the gradient button sit on the solid core. Disabled, the button says what's missing
 * ([disabledLabel]) instead of just dimming.
 */
@Composable
fun GlassActionBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    disabledLabel: String = label,
    summary: String? = null,
    detail: String? = null,
    buttonTestTag: String? = null,
) {
    GlassPanel(
        shape = SquircleShape(28.dp),
        contentPadding = Spacing.xs,
        modifier = modifier
            .navigationBarsPadding()
            .padding(Spacing.m)
            .fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            if (summary != null) {
                Column(Modifier.weight(1f).padding(horizontal = Spacing.s)) {
                    Text(summary, style = MoneyText.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!detail.isNullOrBlank()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .then(if (summary == null) Modifier.weight(1f) else Modifier)
                    .heightIn(min = 52.dp)
                    .clip(KpShapes.action)
                    .background(if (enabled) BrandGradient.diagonal() else SolidColor(CharcoalElevated))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    .padding(horizontal = Spacing.xl)
                    .then(if (buttonTestTag != null) Modifier.testTag(buttonTestTag) else Modifier),
            ) {
                Text(
                    text = if (enabled) label else disabledLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) CharcoalBg else MutedWarmGray,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A sheet's title: an optional small label above, the title, and one line on what happens. */
@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, supporting: String? = null) {
    Column(modifier.fillMaxWidth().padding(bottom = Spacing.l)) {
        if (eyebrow != null) Text(eyebrow.uppercase(), style = EyebrowText, modifier = Modifier.padding(bottom = Spacing.xs))
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.xs))
    }
}

/** A large centred amount, the lead of any sheet that records money. */
@Composable
fun BigAmountField(
    amount: String,
    onAmountChange: (String) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Row(horizontalArrangement = Arrangement.Center, modifier = modifier.fillMaxWidth().padding(vertical = Spacing.xl)) {
        // A smaller symbol on the number's baseline, so the amount leads rather than the "₹".
        Text("₹", style = MoneyText.large, color = accent, modifier = Modifier.alignByBaseline())
        Box(Modifier.padding(start = Spacing.xs).alignByBaseline()) {
            if (amount.isEmpty()) Text("0", style = MoneyText.hero, color = SubMutedWarmGray)
            BasicTextField(
                value = amount,
                onValueChange = onAmountChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = MoneyText.hero.copy(color = OffWhiteText),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    // As wide as what's typed, so "₹" and the number stay centred together.
                    .width(IntrinsicSize.Min)
                    .widthIn(min = 2.dp)
                    .semantics { contentDescription = "Amount in rupees" }
                    .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            )
        }
    }
}

/** A labelled row in a sheet's grouped panel: the label at a fixed width, then whatever it holds. */
@Composable
fun SheetRow(
    label: String,
    modifier: Modifier = Modifier,
    position: RowPosition = RowPosition.Single,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(position.shape)
            .background(CharcoalCard)
            .padding(horizontal = Spacing.l, vertical = Spacing.s),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(SheetLabelWidth))
        content()
    }
}

/** A text entry inside a [SheetRow]: plain text on the row, a muted placeholder while empty. */
@Composable
fun RowScope.SheetTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    fieldTestTag: String? = null,
) {
    Box(modifier.weight(1f)) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = SubMutedWarmGray, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = OffWhiteText),
            cursorBrush = SolidColor(WarmAmberPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier),
        )
    }
}

/**
 * Scrolls a requested item into the part of the sheet not covered by the floating action bar. The
 * default treats the whole viewport as visible, so a focused field could stop right behind the bar.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class ClearOfBarSpec(private val barHeightPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val visible = (containerSize - barHeightPx).coerceAtLeast(0f)
        val end = offset + size
        return when {
            offset >= 0f && end <= visible -> 0f
            offset < 0f || size > visible -> offset
            else -> end - visible
        }
    }
}

val SheetLabelWidth = 96.dp
private val ActionBarClearance = 104.dp
