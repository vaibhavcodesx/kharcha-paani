package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.domain.AiPrompt
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.EyebrowText
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.WarmAmberLight

/**
 * The questions the AI can be asked about this month, and the answer to whichever was asked.
 *
 * Four named questions rather than a chat box: a blank field makes the person do the work of
 * knowing what to ask, and every answer arrives cold. The chip that asked stays selected, so it is
 * always clear which question the text below answers.
 *
 * Designed in `docs/ai-prompt-set-design.md`, approved 2026-09-19.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AskSection(
    prompts: List<AiPrompt>,
    asked: AiPrompt?,
    answer: String?,
    isGenerating: Boolean,
    failed: Boolean,
    hasSpending: Boolean,
    asksLeft: Int,
    possessive: String,
    onAsk: (AiPrompt) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard)
            .padding(Spacing.l)
            .testTag("ask_section"),
    ) {
        Text("Ask about $possessive money", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        // Wrapped, not scrolled: three chips do not fit one phone-width row, and a horizontal
        // scroller hid the third question behind an edge with nothing to say it was there.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
            verticalArrangement = Arrangement.spacedBy(Spacing.s),
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.m),
        ) {
            prompts.forEach { prompt ->
                ChoiceChip(
                    label = prompt.label,
                    selected = prompt == asked,
                    enabled = hasSpending && asksLeft > 0 && !isGenerating,
                    onClick = { onAsk(prompt) },
                    modifier = Modifier.testTag("ask_${prompt.name}"),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = Spacing.m)) {
            AskAnswer(
                asked = asked,
                answer = answer,
                isGenerating = isGenerating,
                failed = failed,
                hasSpending = hasSpending,
                asksLeft = asksLeft,
                possessive = possessive,
                onRetry = { asked?.let(onAsk) },
            )
        }
    }
}

/**
 * The answer area, with every state it can be in.
 *
 * Loading, empty and error each get their own treatment rather than collapsing into one spinner —
 * Part B of the PRD names that collapse as an AI-slop tell.
 */
@Composable
fun AskAnswer(
    asked: AiPrompt?,
    answer: String?,
    isGenerating: Boolean,
    failed: Boolean,
    hasSpending: Boolean,
    asksLeft: Int,
    possessive: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        when {
            isGenerating -> {
                // The label goes above the shimmer, where the answer's own label will be — so the
                // block doesn't reshuffle the moment the text arrives.
                Text(asked?.eyebrow?.uppercase() ?: "READING", style = EyebrowText)
                listOf(1f, 0.94f, 0.62f).forEach { width ->
                    Box(
                        Modifier
                            .fillMaxWidth(width)
                            .height(14.dp)
                            .clip(KpShapes.small)
                            .background(CharcoalElevated)
                            .shimmerLoading()
                    )
                }
            }

            !hasSpending -> Text(
                "Log a few expenses first; there's nothing to read yet.",
                style = MaterialTheme.typography.bodyMedium,
            )

            asksLeft <= 0 -> Text(
                "That's today's readings used up. They start again tomorrow.",
                style = MaterialTheme.typography.bodyMedium,
            )

            answer != null -> {
                if (asked != null) Text(asked.eyebrow.uppercase(), style = EyebrowText)
                Text(answer, style = MaterialTheme.typography.bodyLarge)
                if (failed) {
                    RetryLine("Couldn't refresh it just now.", onRetry)
                }
            }

            failed -> RetryLine("Couldn't get an answer just now.", onRetry)

            else -> Text(
                "Pick a question. Nothing runs until you tap one.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun RetryLine(message: String, onRetry: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = WarmAmberLight)
        KpButton(
            label = "Try again",
            onClick = onRetry,
            tier = KpButtonTier.Link,
            testTag = "ask_retry",
        )
    }
}
