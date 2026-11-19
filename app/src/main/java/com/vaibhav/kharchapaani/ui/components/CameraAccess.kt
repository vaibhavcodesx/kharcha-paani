package com.vaibhav.kharchapaani.ui.components

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vaibhav.kharchapaani.R

/** Declare camera access once, then request it only from an explicit camera action. */
@Composable
internal fun rememberCameraAccess(
    onGranted: () -> Unit,
    onDenied: () -> Unit,
    reason: String = stringResource(R.string.bill_photo_camera_permission_reason),
): () -> Unit = rememberExplainedPermission(Manifest.permission.CAMERA, reason, onGranted, onDenied)
