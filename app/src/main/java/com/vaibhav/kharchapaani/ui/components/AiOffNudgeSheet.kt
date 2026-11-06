package com.vaibhav.kharchapaani.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.viewmodel.AiFeature

/**
 * AI is off and someone reached for something that needs it.
 *
 * It says, for this feature, what stayed on the phone and what turning AI on would send, and
 * offers to turn it on and carry straight on with what they tapped. The other button is the
 * feature's own way without AI where it has one ("Type it myself, keep the photo"), or "Not now".
 * Never a scold: AI off is a fine choice, and the last line says it can change again any time.
 */
@Composable
fun AiOffNudgeSheet(feature: AiFeature, onTurnOn: () -> Unit, onKeepOff: () -> Unit) {
    KpModalSheet(onDismiss = onKeepOff) { AiOffNudgeContent(feature, onTurnOn, onKeepOff) }
}

@Composable
fun AiOffNudgeContent(feature: AiFeature, onTurnOn: () -> Unit, onKeepOff: () -> Unit, modifier: Modifier = Modifier) {
    val copy = nudgeCopy(feature)
    SheetScaffold(
        modifier = modifier,
        actionBar = {
            GlassActionBar(
                label = stringResource(copy.turnOn),
                enabled = true,
                onClick = onTurnOn,
                buttonTestTag = "ai_off_nudge_turn_on",
            )
        },
    ) {
        SheetTitle(title = stringResource(copy.title), supporting = stringResource(copy.body))
        TextButton(onClick = onKeepOff, modifier = Modifier.fillMaxWidth().testTag("ai_off_nudge_keep_off")) {
            Text(stringResource(copy.keepOff), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
        }
        Text(
            stringResource(R.string.ai_off_nudge_footer),
            style = MaterialTheme.typography.bodySmall,
            color = SubMutedWarmGray,
            modifier = Modifier.padding(top = Spacing.s),
        )
    }
}

internal class NudgeCopy(
    @StringRes val title: Int,
    @StringRes val body: Int,
    @StringRes val keepOff: Int,
    @StringRes val turnOn: Int = R.string.ai_off_nudge_turn_on_continue,
)

internal fun nudgeCopy(feature: AiFeature): NudgeCopy = when (feature) {
    AiFeature.General, AiFeature.QuickAdd -> NudgeCopy(
        R.string.ai_off_nudge_general_title, R.string.ai_off_nudge_general_body,
        R.string.ai_off_nudge_keep_off, turnOn = R.string.ai_off_nudge_turn_on,
    )
    AiFeature.Bill -> NudgeCopy(R.string.ai_off_nudge_bill_title, R.string.ai_off_nudge_bill_body, R.string.ai_off_nudge_bill_instead)
    AiFeature.Statement -> NudgeCopy(R.string.ai_off_nudge_statement_title, R.string.ai_off_nudge_statement_body, R.string.ai_off_nudge_not_now)
    AiFeature.Voice -> NudgeCopy(R.string.ai_off_nudge_voice_title, R.string.ai_off_nudge_voice_body, R.string.ai_off_nudge_voice_instead)
    AiFeature.Insights -> NudgeCopy(R.string.ai_off_nudge_insights_title, R.string.ai_off_nudge_insights_body, R.string.ai_off_nudge_not_now)
    AiFeature.TripReview -> NudgeCopy(R.string.ai_off_nudge_trip_title, R.string.ai_off_nudge_trip_body, R.string.ai_off_nudge_not_now)
}
