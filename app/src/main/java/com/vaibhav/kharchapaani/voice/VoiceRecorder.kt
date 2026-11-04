package com.vaibhav.kharchapaani.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * Records the microphone as 16 kHz mono PCM for a few seconds and hands it back as a WAV. Nothing is
 * written to disk and nothing is kept: the bytes live in memory until they're sent or dropped.
 * The caller must hold RECORD_AUDIO.
 */
class VoiceRecorder {
    private var record: AudioRecord? = null
    private var job: Job? = null
    private val pcm = ByteArrayOutputStream()

    /**
     * Starts listening. [onLevel] gets 0..1 about ten times a second for the bar; [onLimit] is called
     * once if [MAX_SECONDS] pass, after which it stops by itself. Returns false when the microphone
     * can't be opened (another app has it).
     */
    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope, onLevel: (Float) -> Unit, onLimit: () -> Unit): Boolean {
        val minBuffer = AudioRecord.getMinBufferSize(WavEncoder.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return false
        val audio = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, WavEncoder.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2,
            )
        } catch (e: IllegalArgumentException) {
            return false
        }
        if (audio.state != AudioRecord.STATE_INITIALIZED) {
            audio.release()
            return false
        }
        record = audio
        pcm.reset()
        audio.startRecording()
        job = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(WavEncoder.SAMPLE_RATE / 10) // 100 ms
            var heard = 0
            while (isActive) {
                val n = audio.read(buffer, 0, buffer.size)
                if (n <= 0) continue
                val bytes = ByteArray(n * 2)
                var sum = 0.0
                for (i in 0 until n) {
                    val s = buffer[i].toInt()
                    bytes[i * 2] = (s and 0xFF).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
                    sum += s.toDouble() * s
                }
                pcm.write(bytes)
                heard += bytes.size
                onLevel(level(sqrt(sum / n)))
                if (WavEncoder.seconds(heard) >= MAX_SECONDS) {
                    onLimit()
                    break
                }
            }
        }
        return true
    }

    /** Stops and returns the recording as a WAV, or null when it's too short to be a sentence. */
    suspend fun finish(): ByteArray? {
        stopCapture()
        val bytes = pcm.toByteArray()
        pcm.reset()
        return if (WavEncoder.seconds(bytes.size) < MIN_SECONDS) null else WavEncoder.wrap(bytes)
    }

    /** Stops and throws the recording away. */
    fun cancel() {
        stopCapture()
        pcm.reset()
    }

    private fun stopCapture() {
        job?.cancel()
        job = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
    }

    companion object {
        const val MAX_SECONDS = 30.0
        const val MIN_SECONDS = 0.5

        /** A loudness for the bar: speech sits around 500–5000 on the 16-bit scale. */
        internal fun level(rms: Double): Float = (rms / 4000.0).coerceIn(0.0, 1.0).toFloat()
    }
}
