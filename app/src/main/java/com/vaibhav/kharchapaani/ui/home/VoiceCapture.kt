package com.vaibhav.kharchapaani.ui.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.ui.icons.KpIcons
import com.vaibhav.kharchapaani.ui.theme.OffWhiteText
import com.vaibhav.kharchapaani.ui.theme.Spacing
import com.vaibhav.kharchapaani.ui.theme.SubMutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.WarmAmberPrimary
import com.vaibhav.kharchapaani.voice.LiveSpeech
import com.vaibhav.kharchapaani.voice.VoiceMode
import com.vaibhav.kharchapaani.voice.VoiceRecorder
import com.vaibhav.kharchapaani.voice.isOnline
import kotlinx.coroutines.launch

/** What the listening bar is showing, if anything. */
enum class VoicePhase { Idle, Recording, LiveWords }

/**
 * Saying an entry out loud. One tap on the mic and, depending on what's possible (see [VoiceMode]),
 * either the app records and sends the audio for Gemini to read, or Android's recogniser shows the
 * words as they're said; with no permission or no recogniser, the system dialog as before.
 * [state] drives the listening bar; everything stops when the card leaves the screen or the app
 * goes to the background, so the microphone is never left open.
 */
@Stable
class VoiceCapture internal constructor(
    private val start: () -> Unit,
    private val finish: () -> Unit,
    private val cancel: () -> Unit,
    val phase: () -> VoicePhase,
    val words: () -> String,
    val level: () -> Float,
) {
    fun listen() = start()
    fun done() = finish()
    fun stop() = cancel()
}

@Composable
fun rememberVoiceCapture(
    friendNames: () -> List<String>,
    voiceToAiAllowed: () -> Boolean,
    onAudio: (ByteArray) -> Unit,
    onText: (String) -> Unit,
    onMessage: (String) -> Unit,
): VoiceCapture {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val names by rememberUpdatedState(friendNames)
    val aiAllowed by rememberUpdatedState(voiceToAiAllowed)
    val sendAudio by rememberUpdatedState(onAudio)
    val sendText by rememberUpdatedState(onText)
    val tell by rememberUpdatedState(onMessage)

    var phase by remember { mutableStateOf(VoicePhase.Idle) }
    var words by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    val recorder = remember { VoiceRecorder() }
    val live = remember { LiveSpeech(context) }

    fun reset() {
        phase = VoicePhase.Idle
        words = ""
        level = 0f
    }

    fun cancel() {
        recorder.cancel()
        live.stop()
        reset()
    }

    // The last resort, and what a phone with no microphone permission still gets.
    val dialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(sendText)
        }
    }
    fun openDialog() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.home_capture_voice_prompt))
        runCatching { dialog.launch(intent) }.onFailure { tell(context.getString(R.string.home_capture_voice_unavailable)) }
    }

    fun beginLiveWords() {
        phase = VoicePhase.LiveWords
        words = ""
        live.start(
            friendNames = names(),
            onWords = { words = it },
            onResult = { text -> reset(); sendText(text) },
            onFailed = { code ->
                reset()
                when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tell(context.getString(R.string.voice_no_match))
                    SpeechRecognizer.ERROR_CLIENT -> Unit // we cancelled it ourselves
                    else -> tell(context.getString(R.string.voice_no_mic))
                }
            },
            onLevel = { level = it },
        )
    }

    fun begin() {
        when (VoiceMode.choose(
            micAllowed = true,
            aiAvailable = aiAllowed(),
            aiNoticeAccepted = aiAllowed(),
            online = isOnline(context),
            liveWordsAvailable = live.available(),
        )) {
            VoiceMode.RecordForAi -> {
                val opened = recorder.start(scope, onLevel = { level = it }, onLimit = {
                    scope.launch {
                        val wav = recorder.finish()
                        reset()
                        if (wav != null) sendAudio(wav)
                    }
                })
                if (opened) phase = VoicePhase.Recording
                else if (live.available()) beginLiveWords() else openDialog()
            }
            VoiceMode.LiveWords -> beginLiveWords()
            VoiceMode.SystemDialog -> openDialog()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else openDialog()
    }
    fun listen() {
        if (phase != VoicePhase.Idle) return
        val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (allowed) begin() else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun finish() {
        when (phase) {
            VoicePhase.Recording -> scope.launch {
                val wav = recorder.finish()
                reset()
                if (wav == null) tell(context.getString(R.string.voice_too_short)) else sendAudio(wav)
            }
            VoicePhase.LiveWords -> live.finish()
            VoicePhase.Idle -> Unit
        }
    }

    // The microphone is only ever open while this card is on screen and the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) cancel() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            cancel()
        }
    }

    return remember { VoiceCapture(::listen, ::finish, ::cancel, { phase }, { words }, { level }) }
}

/**
 * The bar that takes the capture line's place while listening: a dot that swells with the voice,
 * what's being heard (or a prompt to say it), and a cancel and a done button.
 */
@Composable
fun VoiceListeningBar(voice: VoiceCapture, modifier: Modifier = Modifier) {
    val level by animateFloatAsState(voice.level(), label = "voice level")
    val heard = voice.words()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = Spacing.l, end = Spacing.s, top = Spacing.s, bottom = Spacing.s)
            .testTag("voice_listening_bar"),
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size((10 + 22 * level).dp)
                    .clip(CircleShape)
                    .background(WarmAmberPrimary.copy(alpha = 0.35f + 0.65f * level)),
            )
        }
        Column(
            Modifier.weight(1f).padding(horizontal = Spacing.m).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = heard.ifBlank { stringResource(R.string.voice_listening) },
                style = MaterialTheme.typography.bodyLarge,
                color = OffWhiteText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("voice_heard"),
            )
            if (heard.isBlank()) {
                Text(stringResource(R.string.voice_say_it), style = MaterialTheme.typography.bodySmall, color = SubMutedWarmGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        CaptureAction(
            icon = KpIcons.Close,
            label = stringResource(R.string.voice_cancel),
            filled = false,
            onClick = voice::stop,
            modifier = Modifier.testTag("voice_cancel_button"),
        )
        CaptureAction(
            icon = KpIcons.Check,
            label = stringResource(R.string.voice_done),
            filled = true,
            onClick = voice::done,
            modifier = Modifier.padding(start = Spacing.xs).testTag("voice_done_button"),
        )
    }
}
