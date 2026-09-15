package com.vaibhav.kharchapaani.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.GlassPanel
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SquircleShape
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

/**
 * The app's messages ("Created Goa trip.", "Couldn't read that statement."), floating above the dock
 * in the same glass as the dock, text on its solid core. They replace Android toasts, which drew the
 * system's own grey bubble over the app and couldn't carry an action such as Undo.
 */
@Composable
fun KpSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = state, modifier = modifier) { data -> KpSnackbar(data) }
}

@Composable
private fun KpSnackbar(data: SnackbarData) {
    GlassPanel(
        shape = SquircleShape(24.dp),
        coreShape = SquircleShape(19.dp),
        bezel = 5.dp,
        modifier = Modifier
            .padding(horizontal = Spacing.page)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = OffWhiteText,
                modifier = Modifier.weight(1f).padding(vertical = Spacing.m),
            )
            val action = data.visuals.actionLabel
            if (action != null) {
                TextButton(onClick = data::performAction) {
                    Text(action, style = MaterialTheme.typography.labelLarge, color = WarmAmberLight)
                }
            }
        }
    }
}
