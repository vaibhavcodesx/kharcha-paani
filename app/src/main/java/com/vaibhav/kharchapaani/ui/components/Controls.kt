package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * One option among several (a category, a payment method, a person). Unselected chips keep a 3:1
 * outline so they read as tappable on a card; the selected one takes the [accent].
 */
@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = MutedWarmGray,
    accent: Color = WarmAmberPrimary,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(KpShapes.small)
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) accent else OutlineInteractive), KpShapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = Spacing.m),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (selected) accent else iconTint, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) OffWhiteText else MutedWarmGray)
    }
}

/** Two or three mutually exclusive modes side by side (Expense / Income, Just me / Split). */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    accentFor: (T) -> Color = { WarmAmberPrimary },
    testTagFor: (T) -> String? = { null },
) {
    Row(
        modifier = modifier
            .clip(KpShapes.medium)
            .background(CharcoalBg)
            .padding(Spacing.xs)
            .selectableGroup(),
    ) {
        options.forEach { option ->
            Segment(
                text = label(option),
                selected = option == selected,
                accent = accentFor(option),
                onClick = { onSelect(option) },
                testTag = testTagFor(option),
            )
        }
    }
}

@Composable
private fun RowScope.Segment(text: String, selected: Boolean, accent: Color, onClick: () -> Unit, testTag: String?) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) accent else MutedWarmGray,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .weight(1f)
            .clip(SquircleSegment)
            .background(if (selected) CharcoalCard else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    )
}

private val SquircleSegment = SquircleShape(14.dp)
