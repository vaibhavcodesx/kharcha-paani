package com.vaibhav.kharchapaani.ui.shell

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.components.pressBounce
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.GlassPanel
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape

enum class AppTab(val label: String, val icon: ImageVector, val testTag: String) {
    Activity("Activity", KpIcons.Activity, "tab_activity"),
    Splits("Splits", KpIcons.Splits, "tab_splits"),
    Insights("Insights", KpIcons.Insights, "tab_insights"),
}

private val DockHeight = 68.dp
private val DockShape = SquircleShape(26.dp)
/** The tab wells inset from the pane's edge; concentric with [DockShape]. */
private val DockPadding = 6.dp
private val DockItemShape = SquircleShape(20.dp)

/** Where floating messages sit: just above the dock and the system bar. */
@Composable
fun snackbarClearance(): Dp =
    DockHeight + Spacing.m * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** How much bottom padding scrolling content needs to clear the dock and the system bar. */
@Composable
fun dockClearance(): Dp =
    DockHeight + Spacing.m * 2 + Spacing.l +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * The app's navigation: a glass dock floating above content, with the add action as its own glass
 * button beside it. Labels and icons sit on the dock's solid core, never on the blur itself.
 */
@Composable
fun KpDock(
    currentTab: AppTab,
    onSelectTab: (AppTab) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .navigationBarsPadding()
            // On tablets and unfolded phones a full-width dock puts tabs out of thumb reach.
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .padding(horizontal = Spacing.page, vertical = Spacing.m),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassPanel(
            shape = DockShape,
            contentPadding = DockPadding,
            modifier = Modifier.weight(1f).height(DockHeight),
        ) {
            Row(Modifier.fillMaxSize().selectableGroup()) {
                AppTab.entries.forEach { tab ->
                    DockItem(tab = tab, selected = tab == currentTab, onClick = { onSelectTab(tab) })
                }
            }
        }
        AddButton(onClick = onAdd)
    }
}

@Composable
private fun RowScope.DockItem(tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val wellAlpha by animateFloatAsState(if (selected) 1f else 0f, label = "dock_well")
    val labelColor by animateColorAsState(if (selected) OffWhiteText else MutedWarmGray, label = "dock_label")

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(DockItemShape)
            // A lift out of the pane, not a hole cut into it: a dark fill read as a gap in the
            // glass, now that the pane is the glass rather than a frame around one.
            .background(Color.White.copy(alpha = 0.10f * wellAlpha))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = ripple(),
            )
            .testTag(tab.testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            // The active icon carries the brand gradient; the others stay quiet.
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier
                    .size(22.dp)
                    .gradientTint(),
            )
        } else {
            Icon(imageVector = tab.icon, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(Spacing.xxs))
        Text(text = tab.label, style = MaterialTheme.typography.labelMedium, color = labelColor)
    }
}

/** Replaces what's drawn with the brand gradient, keeping its alpha (so an icon's tone survives). */
private fun Modifier.gradientTint(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = BrandGradient.diagonal(), blendMode = BlendMode.SrcIn)
    }

/**
 * The add action is the brand, not a piece of glass. It used to be a gradient square inside a glass
 * rim, which put a thick tinted frame around it and shrank the gradient to a fraction of the
 * button; the whole button is the gradient now, matched to the dock's height beside it.
 */
@Composable
private fun AddButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(DockHeight)
            .pressBounce(interactionSource)
            .clip(KpShapes.action)
            .background(BrandGradient.diagonal())
            .clickable(interactionSource = interactionSource, indication = ripple(color = CharcoalBg), onClick = onClick)
            .semantics { contentDescription = "Add expense" }
            .testTag("dock_add"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = KpIcons.Add, contentDescription = null, tint = CharcoalBg, modifier = Modifier.size(26.dp))
    }
}
