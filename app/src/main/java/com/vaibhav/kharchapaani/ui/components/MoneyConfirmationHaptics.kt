package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.flow.Flow

/** One feedback event per completed local write; a failed/empty action never reaches this flow. */
@Composable
fun MoneyConfirmationHaptics(confirmations: Flow<Unit>) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(confirmations, haptics) {
        confirmations.collect { haptics.performHapticFeedback(HapticFeedbackType.Confirm) }
    }
}
