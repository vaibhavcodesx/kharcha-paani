package com.vaibhav.kharchapaani.ui.home

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalElevated
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import java.util.Locale

/**
 * The one place to log something: type or say it, and the AI fills in the rest. The other ways in
 * (a bill photo, a bank statement, an IOU) sit quietly underneath instead of competing with it.
 */
@Composable
fun CaptureCard(
    isParsing: Boolean,
    onParseText: (String) -> Unit,
    onParseImage: (Bitmap) -> Unit,
    onParsePdf: (ByteArray, String) -> Unit,
    onOpenIou: () -> Unit,
    modifier: Modifier = Modifier,
    // The home-screen widget's Speak button: start listening straight away.
    startVoiceCapture: Boolean = false,
    onVoiceCaptureStarted: () -> Unit = {},
) {
    var input by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (input.isBlank()) return
        focusManager.clearFocus()
        onParseText(input)
        input = ""
    }

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) onParseText(spoken)
        }
    }
    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say what you spent, like “coffee 150 card”")
        // No speech recogniser installed: there's nothing to launch.
        runCatching { speechLauncher.launch(intent) }
    }
    LaunchedEffect(startVoiceCapture) {
        if (startVoiceCapture) {
            onVoiceCaptureStarted()
            listen()
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { onParsePdf(it, "statement.pdf") }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            }.getOrNull()?.let(onParseImage)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(KpShapes.large)
            .background(CharcoalCard),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s),
        ) {
            Box(Modifier.weight(1f).padding(vertical = Spacing.m)) {
                if (input.isEmpty()) {
                    Text(
                        text = if (isParsing) "Reading it…" else "What did you spend on?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SubMutedWarmGray,
                    )
                }
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    enabled = !isParsing,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OffWhiteText),
                    cursorBrush = SolidColor(WarmAmberPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_capture_input"),
                )
            }
            when {
                isParsing -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                input.isNotBlank() -> CaptureAction(
                    icon = KpIcons.Send,
                    label = "Log it",
                    filled = true,
                    onClick = ::submit,
                    modifier = Modifier.testTag("quick_parse_button"),
                )
                else -> CaptureAction(
                    icon = KpIcons.Mic,
                    label = "Say it",
                    filled = false,
                    onClick = ::listen,
                    modifier = Modifier.testTag("voice_mic_button"),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.l).height(1.dp).background(CharcoalBorder))
        Row(Modifier.padding(Spacing.xs)) {
            CaptureMode(KpIcons.Scan, "Scan bill", Modifier.testTag("scan_receipt_button")) { imagePicker.launch("image/*") }
            CaptureMode(KpIcons.Statement, "Statement", Modifier.testTag("upload_pdf_statement_button")) { pdfPicker.launch("application/pdf") }
            CaptureMode(KpIcons.Lend, "IOU", Modifier.testTag("home_iou_button"), onOpenIou)
        }
    }
}

@Composable
private fun CaptureAction(icon: ImageVector, label: String, filled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .clip(KpShapes.small)
            .background(if (filled) WarmAmberPrimary else CharcoalElevated)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) CharcoalBg else WarmAmberPrimary, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RowScope.CaptureMode(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clip(KpShapes.small)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Icon(icon, contentDescription = null, tint = MutedWarmGray, modifier = Modifier.size(20.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = MutedWarmGray, modifier = Modifier.padding(start = Spacing.s))
    }
}
