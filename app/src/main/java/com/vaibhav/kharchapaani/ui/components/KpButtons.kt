package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.BrandGradient
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.OutlineInteractive
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

/**
 * How much an action is worth, in four steps.
 *
 * Before this the app had two: a gradient pill, and bare coloured text with no container at all.
 * Everything that was not "Add expense" or the add button was the second one — "Settle up" on a
 * real debt between two named people looked exactly like "See who pays whom", which only scrolls
 * you somewhere. Colour alone was carrying the whole weight of "this is tappable" *and* "this
 * matters", and it cannot carry both.
 *
 * The two middle tiers are not an invention: [Outline] is the treatment "Invite people" and
 * "Sign out" already shared, named and made reusable.
 */
enum class KpButtonTier {
    /** Gradient fill. The one thing this screen is for. Add expense, Save, the dock's add button. */
    Primary,

    /** A visible boundary, no fill. A real action with a consequence: settle up, invite, join. */
    Outline,

    /** A low-opacity fill, no boundary. Repeatable and low-stakes: record a payment, export. */
    Ghost,

    /** Colour only. Inline links that take you somewhere and change nothing. */
    Link,
}

/**
 * A button at one of the four [KpButtonTier]s.
 *
 * @param fillWidth true for a full-width action at the foot of a sheet; false to size to its label.
 */
@Composable
fun KpButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tier: KpButtonTier = KpButtonTier.Outline,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    onClickLabel: String? = null,
    testTag: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val contentColor = when {
        !enabled -> MutedWarmGray
        tier == KpButtonTier.Primary -> CharcoalBg
        tier == KpButtonTier.Link -> WarmAmberLight
        else -> OffWhiteText
    }
    val shape = if (tier == KpButtonTier.Primary) KpShapes.action else KpShapes.medium

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            // minimumInteractiveComponentSize only pads the *reported* touch target and left a
            // short label like "Join" 42dp wide on screen; defaultMinSize grows the box itself.
            .then(
                if (fillWidth) Modifier.heightIn(min = 52.dp)
                else Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            )
            .clip(shape)
            .then(
                when {
                    tier == KpButtonTier.Primary && enabled -> Modifier.background(BrandGradient.diagonal())
                    tier == KpButtonTier.Primary -> Modifier.background(SolidColor(CharcoalElevated))
                    tier == KpButtonTier.Outline -> Modifier.border(1.dp, OutlineInteractive, shape)
                    tier == KpButtonTier.Ghost -> Modifier.background(GhostFill)
                    else -> Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = if (tier == KpButtonTier.Primary) CharcoalBg else WarmAmberLight),
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (tier == KpButtonTier.Link) Spacing.s else Spacing.l, vertical = Spacing.s)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp).padding(end = 0.dp))
            Box(Modifier.size(Spacing.s))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Ghost's fill, and the reason it is a translucent white rather than a colour.
 *
 * It used to be `CharcoalElevated` (#22201D), which against the `CharcoalCard` (#1E1C1A) that most
 * Ghost buttons sit on differs by four points per channel — about 1.05:1. The container was
 * effectively not drawn: "Record" on a friend row and "Read this trip" in its card both read as
 * plain labels, and the trip one was reported as not being clickable at all.
 *
 * A fixed opaque colour cannot work for a tier used on two different surfaces. A wash over whatever
 * is behind it can: it lands around #343230 on a card and #262523 on the page, visible on both
 * without ever becoming an Outline.
 */
private val GhostFill = Color.White.copy(alpha = 0.10f)

/** Colour is not the only signal, so a disabled tier keeps its shape. Exposed for tests. */
val KpButtonTier.hasContainer: Boolean
    get() = this != KpButtonTier.Link
