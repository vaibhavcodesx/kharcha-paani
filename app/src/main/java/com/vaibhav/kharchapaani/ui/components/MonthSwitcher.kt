package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.format.MonthNames
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import java.time.YearMonth

/** Steps a month back or forward, between the first month with anything in it and [latest]. */
@Composable
fun MonthSwitcher(
    month: YearMonth,
    earliest: YearMonth,
    latest: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(horizontal = Spacing.xs),
    ) {
        StepButton(
            label = stringResource(R.string.shared_chrome_previous),
            enabled = month > earliest,
            rotation = 90f,
            onClick = { onMonthChange(month.minusMonths(1)) },
            tag = "month_previous",
        )
        Text(
            text = MonthNames.title(month),
            style = MaterialTheme.typography.titleMedium,
            color = OffWhiteText,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("month_label"),
        )
        StepButton(
            label = stringResource(R.string.shared_chrome_next),
            enabled = month < latest,
            rotation = -90f,
            onClick = { onMonthChange(month.plusMonths(1)) },
            tag = "month_next",
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, rotation: Float, onClick: () -> Unit, tag: String) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp).testTag(tag)) {
        Icon(
            imageVector = KpIcons.ChevronDown,
            contentDescription = label,
            tint = if (enabled) OffWhiteText else SubMutedWarmGray.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}
