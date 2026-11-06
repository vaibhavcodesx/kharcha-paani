package com.vaibhav.kharchapaani.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Wraps raw 16-bit little-endian PCM in a WAV header: the one audio format every Gemini model reads. */
object WavEncoder {
    const val SAMPLE_RATE = 16_000
    private const val HEADER_BYTES = 44

    fun wrap(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE, channels: Int = 1): ByteArray {
        val byteRate = sampleRate * channels * 2
        val out = ByteBuffer.allocate(HEADER_BYTES + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII))
        out.putInt(36 + pcm.size)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII))
        out.putInt(16)                       // PCM header size
        out.putShort(1)                      // PCM
        out.putShort(channels.toShort())
        out.putInt(sampleRate)
        out.putInt(byteRate)
        out.putShort((channels * 2).toShort()) // block align
        out.putShort(16)                     // bits per sample
        out.put("data".toByteArray(Charsets.US_ASCII))
        out.putInt(pcm.size)
        out.put(pcm)
        return out.array()
    }

    /** Seconds of audio in [pcmBytes] of 16-bit mono PCM at [sampleRate]. */
    fun seconds(pcmBytes: Int, sampleRate: Int = SAMPLE_RATE): Double = pcmBytes / (sampleRate * 2.0)
}
