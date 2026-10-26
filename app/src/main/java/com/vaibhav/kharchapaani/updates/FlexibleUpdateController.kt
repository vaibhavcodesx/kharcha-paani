package com.vaibhav.kharchapaani.updates

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/** Play owns consent/download; only an explicit complete() can restart this app. */
internal class FlexibleUpdateController(
    private val manager: AppUpdateManager,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
    private val onReady: (Boolean) -> Unit,
    wasOffered: Boolean = false,
    private val onOffered: () -> Unit = {},
    private val allowOffer: () -> Boolean = { true },
) {
    private var active = true
    private var offered = wasOffered
    private var ready = false
    private val listener = InstallStateUpdatedListener { state ->
        if (active) when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> updateReady(true)
            InstallStatus.INSTALLED, InstallStatus.FAILED, InstallStatus.CANCELED -> updateReady(false)
        }
    }

    init { runCatching { manager.registerListener(listener) } }

    fun resume() {
        if (!active) return
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { info ->
                if (!active) return@addOnSuccessListener
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    updateReady(true)
                } else if (!offered && allowOffer() && info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                    offered = true
                    onOffered()
                    runCatching {
                        manager.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build())
                    }
                }
            }.addOnFailureListener { /* Sideloads, no Play or no connection never block the ledger. */ }
        }
    }

    fun complete() {
        if (!active || !ready || !allowOffer()) return
        runCatching {
            manager.completeUpdate().addOnSuccessListener { if (active) updateReady(false) }
                .addOnFailureListener { /* Keep the notice so a later explicit tap can retry. */ }
        }
    }

    fun stop() {
        active = false
        runCatching { manager.unregisterListener(listener) }
    }

    private fun updateReady(value: Boolean) {
        ready = value
        onReady(value)
    }
}
