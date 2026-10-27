package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R
import androidx.compose.ui.platform.testTag
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText

/**
 * Shown once, before the first thing goes to Google Gemini: a quick add, a bill, a statement or an
 * Insights question (plan T1.10, owner decision D2). The words are the owner's: on Gemini's free tier
 * Google may use what's sent to improve its models and its reviewers may read it, and Play's User Data
 * policy and India's DPDP Act both want that said plainly. "Type it myself" keeps everything else
 * working.
 */
@Composable
fun AiNoticeSheet(onGotIt: () -> Unit, onTypeMyself: () -> Unit) {
    KpModalSheet(onDismiss = onTypeMyself) { AiNoticeContent(onGotIt, onTypeMyself) }
}

@Composable
fun AiNoticeContent(onGotIt: () -> Unit, onTypeMyself: () -> Unit, modifier: Modifier = Modifier) {
    SheetScaffold(
        modifier = modifier,
        actionBar = { GlassActionBar(label = stringResource(R.string.ai_display_notice_got_it), enabled = true, onClick = onGotIt, buttonTestTag = "ai_notice_got_it") },
    ) {
        SheetTitle(title = stringResource(R.string.ai_display_notice_title), supporting = stringResource(R.string.ai_display_notice_body))
        TextButton(onClick = onTypeMyself, modifier = Modifier.fillMaxWidth().testTag("ai_notice_type_myself")) {
            Text(stringResource(R.string.ai_display_notice_manual), style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
        }
    }
}
