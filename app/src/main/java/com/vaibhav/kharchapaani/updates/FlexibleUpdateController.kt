package com.vaibhav.kharchapaani.updates

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager

/** Minimal contract for the test-first Play integration; behaviour is added after red CI. */
internal class FlexibleUpdateController(
    private val manager: AppUpdateManager,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
    private val onReady: (Boolean) -> Unit,
    private val wasOffered: Boolean = false,
    private val onOffered: () -> Unit = {},
) {
    fun resume() {}
    fun complete() {}
    fun stop() {}
}
