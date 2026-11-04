package com.vaibhav.kharchapaani.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {

    @Test
    fun `the header describes 16 kHz mono 16-bit PCM and the data follows it untouched`() {
        val pcm = ByteArray(32_000) { (it % 251).toByte() } // one second
        val wav = WavEncoder.wrap(pcm)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + pcm.size, header.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(16, header.getInt(16))
        assertEquals(1, header.getShort(20).toInt())      // PCM
        assertEquals(1, header.getShort(22).toInt())      // mono
        assertEquals(16_000, header.getInt(24))
        assertEquals(32_000, header.getInt(28))           // bytes per second
        assertEquals(2, header.getShort(32).toInt())      // block align
        assertEquals(16, header.getShort(34).toInt())
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(pcm.size, header.getInt(40))
        assertEquals(44 + pcm.size, wav.size)
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    @Test
    fun `seconds counts 16-bit mono samples`() {
        assertEquals(1.0, WavEncoder.seconds(32_000), 0.0001)
        assertEquals(0.5, WavEncoder.seconds(16_000), 0.0001)
    }
}
