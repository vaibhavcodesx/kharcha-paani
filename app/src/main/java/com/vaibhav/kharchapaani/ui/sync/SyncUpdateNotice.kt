package com.vaibhav.kharchapaani.ui.sync

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.sync.AppVersionGate
import com.vaibhav.kharchapaani.ui.theme.Spacing

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncUpdateNotice(modifier: Modifier = Modifier, onLinkUnavailable: (String) -> Unit) {
    val context = LocalContext.current
    val notices = remember(context) { AppVersionGate.observeNotice(context) }
    val notice by notices.collectAsStateWithLifecycle(initialValue = AppVersionGate.notice(context))
    if (notice == AppVersionGate.Notice.NONE) return

    val required = notice == AppVersionGate.Notice.REQUIRED
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(if (required) R.string.sync_update_required else R.string.sync_update_recommended),
                style = if (required) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodySmall,
            )
            if (required) Text(stringResource(R.string.sync_update_data_safe), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                val openUpdate = {
                    if (!openUpdateLink(context)) {
                        onLinkUnavailable(context.getString(R.string.sync_update_no_app))
                    }
                }
                if (required) {
                    Button(onClick = openUpdate) { Text(stringResource(R.string.sync_update_action)) }
                } else {
                    TextButton(onClick = openUpdate) { Text(stringResource(R.string.sync_update_action)) }
                    TextButton(onClick = { AppVersionGate.dismissRecommendation(context) }) {
                        Text(stringResource(R.string.sync_update_dismiss))
                    }
                }
            }
        }
    }
}

private const val PLAY_INSTALLER = "com.android.vending"
private const val RELEASES = "https://github.com/vaibhavcodesx/kharcha-paani/releases"

@Suppress("DEPRECATION") // Android 7–10 have no InstallSourceInfo.
private fun openUpdateLink(context: Context): Boolean {
    val packageId = context.packageName
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.packageManager.getInstallSourceInfo(packageId).installingPackageName
    } else {
        context.packageManager.getInstallerPackageName(packageId)
    }
    fun open(uri: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    return if (installer == PLAY_INSTALLER) {
        open("market://details?id=$packageId") || open("https://play.google.com/store/apps/details?id=$packageId")
    } else {
        open(RELEASES)
    }
}
