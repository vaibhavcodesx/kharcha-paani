package com.vaibhav.kharchapaani.updates

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.play.core.appupdate.AppUpdateManagerFactory

/** Hidden while a money draft or other blocking sheet is open. The offer survives rotation. */
@Composable
internal fun FlexibleUpdates(enabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember(context) { runCatching { AppUpdateManagerFactory.create(context.applicationContext) }.getOrNull() } ?: return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val canOffer by rememberUpdatedState(enabled)
    var offered by rememberSaveable { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    val controller = remember(manager, launcher) {
        FlexibleUpdateController(manager, launcher, { ready = it }, offered, { offered = true }, { canOffer })
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) controller.resume() }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.resume()
        onDispose { lifecycle.removeObserver(observer); controller.stop() }
    }
    LaunchedEffect(enabled, controller) {
        if (enabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller.resume()
    }
    if (ready && enabled) {
        Snackbar(
            modifier = modifier.testTag("play_update_ready"),
            action = {
                TextButton(onClick = controller::complete, modifier = Modifier.testTag("play_update_restart")) { Text("Restart") }
            },
        ) { Text("Update ready. Save any changes, then restart.") }
    }
}
