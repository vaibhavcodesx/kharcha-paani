package com.vaibhav.kharchapaani.reviews

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/** No UI of our own, no history replay, and no candidate retained across backgrounding. */
@Composable
internal fun ReviewAfterSettlement(moments: Flow<Unit>, enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.activity() } ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canPrompt by rememberUpdatedState(enabled)
    val eligibility = remember(context) { ReviewEligibility(context) }
    val manager = remember(context) { runCatching { ReviewManagerFactory.create(context.applicationContext) }.getOrNull() } ?: return
    val requester = remember(activity, manager, eligibility, lifecycle) {
        ReviewRequester(activity, manager, eligibility) { canPrompt && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
    }
    LaunchedEffect(moments, requester, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            moments.collect {
                // Let dismissal and any error/snackbar settle before checking the current UI.
                delay(750)
                requester.request()
            }
        }
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
