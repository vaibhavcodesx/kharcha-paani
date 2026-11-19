package com.vaibhav.kharchapaani.ui.components

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.theme.Spacing

/** Ask only from a feature action. A granted or permanently blocked permission needs no sheet. */
@Composable
internal fun rememberExplainedPermission(
    permission: String,
    reason: String,
    onGranted: () -> Unit,
    onDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val attempts = remember(context) { context.getSharedPreferences("permission_requests", Context.MODE_PRIVATE) }
    var explaining by rememberSaveable(permission) { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else onDenied()
    }

    if (explaining) {
        PermissionExplanation(
            reason = reason,
            onContinue = {
                explaining = false
                attempts.edit().putBoolean(permission, true).apply()
                launcher.launch(permission)
            },
            onSkip = {
                explaining = false
                onDenied()
            },
        )
    }

    return {
        when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> onGranted()
            attempts.getBoolean(permission, false) &&
                (activity == null || !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) -> onDenied()
            else -> explaining = true
        }
    }
}

@Composable
private fun PermissionExplanation(reason: String, onContinue: () -> Unit, onSkip: () -> Unit) {
    KpModalSheet(onDismiss = onSkip) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text(reason, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("permission_reason"))
            KpButton(
                label = stringResource(R.string.permission_continue),
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
                tier = KpButtonTier.Primary,
                testTag = "permission_continue",
            )
            KpButton(
                label = stringResource(R.string.permission_not_now),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                tier = KpButtonTier.Link,
                testTag = "permission_not_now",
            )
        }
    }
}
