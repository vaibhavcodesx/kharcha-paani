package com.vaibhav.kharchapaani.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary

/**
 * A screen, or a section of one, with nothing in it yet.
 *
 * Part B of the PRD names the generic version — "a centered icon + bold headline + gray subtext,
 * all default-Material-spaced, with no personality or specific next action" — as an AI-slop tell to
 * eliminate. So this one has a hand-drawn illustration rather than a glyph, the text sits beside it
 * rather than under it, and the body is expected to say what to do next rather than restate the
 * heading.
 *
 * The illustrations are deliberately **not** in [com.vaibhav.kharchapaani.ui.icons.KpIcons]: that
 * is a strict 24-unit grid of line icons, and mixing an illustration into it would put two icon
 * languages in one row — the same inconsistency the design audit flagged about group cover art.
 * They appear here and nowhere else.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    @DrawableRes illustration: Int,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.xl)
            .testTag("empty_state"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(illustration),
                contentDescription = null,
                // One dimension only: these are hand-drawn and each keeps its own aspect ratio, so
                // forcing a square box would squash them.
                contentScale = ContentScale.FillHeight,
                colorFilter = ColorFilter.tint(WarmAmberPrimary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .height(72.dp)
                    .padding(end = Spacing.l)
                    .clearAndSetSemantics {},
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (action != null) {
            Row(Modifier.padding(top = Spacing.l)) { action() }
        }
    }
}
