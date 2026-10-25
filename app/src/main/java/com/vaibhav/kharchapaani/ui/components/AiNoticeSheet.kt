package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        actionBar = { GlassActionBar(label = "Got it", enabled = true, onClick = onGotIt, buttonTestTag = "ai_notice_got_it") },
    ) {
        SheetTitle(title = AI_NOTICE_TITLE, supporting = AI_NOTICE_BODY)
        TextButton(onClick = onTypeMyself, modifier = Modifier.fillMaxWidth().testTag("ai_notice_type_myself")) {
            Text("Type it myself", style = MaterialTheme.typography.labelLarge, color = OffWhiteText)
        }
    }
}

internal const val AI_NOTICE_TITLE = "Bills, statements and quick-add text are read by Google Gemini"
internal const val AI_NOTICE_BODY = "That's how the app fills in amounts for you. While Kharcha Paani is free, Google may use " +
    "what's sent to improve Gemini, and its reviewers may see it. It's never shown to other users, and we never sell your " +
    "data. Prefer not to? Type entries yourself: everything else works the same."
