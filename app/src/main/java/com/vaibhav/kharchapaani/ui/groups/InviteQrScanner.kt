package com.vaibhav.kharchapaani.ui.groups

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.domain.decodeQr
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.Spacing
import java.util.concurrent.Executors

@Composable
internal fun InviteQrScanner(onResult: (String) -> Unit, onDismiss: () -> Unit, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val result = rememberUpdatedState(onResult)
    val unavailable = rememberUpdatedState(onUnavailable)
    val view = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    DisposableEffect(owner, view) {
        val main = ContextCompat.getMainExecutor(context)
        val decoder = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply { setSurfaceProvider(view.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var active = true
        var decodedOne = false // Accessed only on the analyzer's thread.
        analysis.setAnalyzer(decoder) { frame ->
            try {
                if (!decodedOne) {
                    val text = decodeQr(frame.luminance(), frame.width, frame.height)
                    if (text != null) {
                        decodedOne = true
                        main.execute { if (active) result.value(text) }
                    }
                }
            } finally {
                frame.close()
            }
        }
        future.addListener({
            if (active) {
                runCatching {
                    val camera = future.get()
                    provider = camera
                    val selector = if (camera.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    camera.bindToLifecycle(owner, selector, preview, analysis)
                }.onFailure { unavailable.value() }
            }
        }, main)
        onDispose {
            active = false
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            decoder.shutdown()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = KpShapes.large) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.m),
            ) {
                Text(stringResource(R.string.shared_group_qr_scan), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.shared_group_qr_point), style = MaterialTheme.typography.bodyMedium)
                AndroidView(
                    factory = { view },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(KpShapes.small),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.shared_group_qr_close))
                }
            }
        }
    }
}

/** CameraX's YUV image has one byte per luminance pixel and may pad each row. */
private fun ImageProxy.luminance(): ByteArray {
    val plane = planes[0]
    val buffer = plane.buffer.duplicate()
    val start = buffer.position()
    val pixels = ByteArray(width * height)
    for (row in 0 until height) {
        buffer.position(start + row * plane.rowStride)
        buffer.get(pixels, row * width, width)
    }
    return pixels
}
