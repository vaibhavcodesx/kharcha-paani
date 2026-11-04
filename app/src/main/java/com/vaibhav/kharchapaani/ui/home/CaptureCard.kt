package com.vaibhav.kharchapaani.ui.home

import com.vaibhav.kharchapaani.R
import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.remember
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vaibhav.kharchapaani.domain.StatementPdf
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
import com.vaibhav.kharchapaani.util.SharedFiles
import kotlinx.coroutines.launch
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
    aiEnabled: Boolean = true,
    startVoiceCapture: Boolean = false,
    onVoiceCaptureStarted: () -> Unit = {},
    // The widget's quick-add bar: put the cursor in the line (keyboard up), then report it.
    focusCapture: Boolean = false,
    onCaptureFocused: () -> Unit = {},
    onCaptureFailed: (String) -> Unit = {},
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
        if (!aiEnabled) {
            onCaptureFailed(context.getString(R.string.ai_choice_voice_off))
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.home_capture_voice_prompt))
        runCatching { speechLauncher.launch(intent) }
            .onFailure { onCaptureFailed(context.getString(R.string.home_capture_voice_unavailable)) }
    }
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusCapture) {
        if (focusCapture) {
            // On a short phone the line can start below the fold: scroll it in, then focus.
            bringIntoView.bringIntoView()
            focusRequester.requestFocus()
            keyboard?.show()
            onCaptureFocused()
        }
    }
    LaunchedEffect(startVoiceCapture) {
        if (startVoiceCapture) {
            onVoiceCaptureStarted()
            listen()
        }
    }

    // Read off the main thread and capped (SharedFiles): a full-size camera photo or a huge file
    // used to be decoded right here, which froze or crashed the app.
    val scope = rememberCoroutineScope()
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            when (val read = SharedFiles.readBytes(context, uri, StatementPdf.MAX_BYTES)) {
                is SharedFiles.Read.Bytes -> onParsePdf(read.bytes, "statement.pdf")
                SharedFiles.Read.TooLarge -> onCaptureFailed(context.getString(R.string.home_capture_pdf_too_large))
                SharedFiles.Read.Unreadable -> onCaptureFailed(context.getString(R.string.home_capture_pdf_unreadable))
            }
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) scope.launch {
            SharedFiles.decodeImage(context, uri)?.let(onParseImage) ?: onCaptureFailed(context.getString(R.string.home_capture_image_unreadable))
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
                    if (isParsing) {
                        Text(stringResource(R.string.home_capture_reading), style = MaterialTheme.typography.bodyLarge, color = SubMutedWarmGray)
                    } else {
                        RotatingHint()
                    }
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
                        .bringIntoViewRequester(bringIntoView)
                        .focusRequester(focusRequester)
                        .testTag("quick_capture_input"),
                )
            }
            when {
                isParsing -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WarmAmberPrimary, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                }
                input.isNotBlank() -> CaptureAction(
                    icon = KpIcons.Send,
                    label = stringResource(R.string.home_capture_log),
                    filled = true,
                    onClick = ::submit,
                    modifier = Modifier.testTag("quick_parse_button"),
                )
                else -> CaptureAction(
                    icon = KpIcons.Mic,
                    label = stringResource(R.string.home_capture_say),
                    filled = false,
                    onClick = ::listen,
                    modifier = Modifier.testTag("voice_mic_button"),
                )
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.l).height(1.dp).background(CharcoalBorder))
        Text(
            text = stringResource(if (aiEnabled) R.string.ai_choice_capture_on else R.string.ai_choice_capture_off),
            style = MaterialTheme.typography.bodySmall,
            color = SubMutedWarmGray,
            modifier = Modifier.padding(horizontal = Spacing.l, vertical = Spacing.s).testTag("capture_ai_status"),
        )
        Row(Modifier.padding(Spacing.xs)) {
            CaptureMode(KpIcons.Scan, stringResource(R.string.home_capture_bill_photo), Modifier.testTag("scan_receipt_button")) {
                runCatching { imagePicker.launch("image/*") }
                    .onFailure { onCaptureFailed(context.getString(R.string.home_capture_image_unreadable)) }
            }
            CaptureMode(KpIcons.Statement, stringResource(R.string.home_capture_statement), Modifier.testTag("upload_pdf_statement_button")) {
                runCatching { pdfPicker.launch("application/pdf") }
                    .onFailure { onCaptureFailed(context.getString(R.string.home_capture_pdf_unreadable)) }
            }
            CaptureMode(KpIcons.Lend, stringResource(R.string.home_capture_iou), Modifier.testTag("home_iou_button"), onOpenIou)
        }
    }
}

/**
 * The empty line asks the question, then every few seconds shows a way to answer it ("Try: chai 40
 * cash"): the examples are the teaching, so they are kept to lines quick add reads (CaptureHintsTest).
 * A screen reader gets the question only; the rotation is for eyes.
 */
@Composable
private fun RotatingHint() {
    val prompt = stringResource(R.string.home_capture_prompt)
    val examples = stringArrayResource(R.array.home_capture_examples).toList()
    val tryLine = stringResource(R.string.home_capture_try, "%s")
    val hints = remember(prompt, examples, tryLine) {
        CaptureHints.sequence(prompt, examples) { tryLine.replace("%s", it) }
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(hints) {
        while (true) {
            // Real time, off the main clock: this loop never ends, and a delay on the composition's
            // clock would keep a test (or the idle check) advancing forever.
            withContext(Dispatchers.Default) { delay(CaptureHints.INTERVAL_MILLIS) }
            index = CaptureHints.next(index, hints.size)
        }
    }
    Crossfade(
        targetState = hints[index.coerceIn(hints.indices)],
        animationSpec = tween(400),
        label = "capture hint",
        modifier = Modifier.semantics { contentDescription = prompt },
    ) { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyLarge,
            color = SubMutedWarmGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The empty capture line's cycle: the question first, then the examples, then round again. */
object CaptureHints {
    const val INTERVAL_MILLIS = 3_500L

    fun sequence(prompt: String, examples: List<String>, format: (String) -> String): List<String> =
        listOf(prompt) + examples.map(format)

    fun next(index: Int, size: Int): Int = if (size <= 0) 0 else (index + 1) % size
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
