package com.vaibhav.kharchapaani.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Android's own speech recogniser with the words showing as they're said, in Indian English, biased
 * toward the names of this person's friends so "Rohan" isn't heard as "Rowan". The fallback when the
 * AI can't take the audio (offline, not yet accepted, switched off). Main thread only.
 */
class LiveSpeech(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    fun available(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(
        friendNames: List<String>,
        onWords: (String) -> Unit,
        onResult: (String) -> Unit,
        onFailed: (code: Int) -> Unit,
        onLevel: (Float) -> Unit = {},
    ) {
        stop()
        val speech = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = speech
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(onWords)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (text.isNullOrBlank()) onFailed(SpeechRecognizer.ERROR_NO_MATCH) else onResult(text)
            }
            override fun onError(error: Int) = onFailed(error)
            override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speech.startListening(intent(friendNames))
    }

    /** Ends listening and delivers what was heard so far. */
    fun finish() { recognizer?.stopListening() }

    fun stop() {
        recognizer?.let {
            runCatching { it.cancel() }
            it.destroy()
        }
        recognizer = null
    }

    internal fun intent(friendNames: List<String>): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            .also { intent ->
                if (Build.VERSION.SDK_INT >= 33 && friendNames.isNotEmpty()) {
                    intent.putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(friendNames.take(BIAS_LIMIT)))
                }
            }

    private companion object {
        const val BIAS_LIMIT = 50
    }
}
