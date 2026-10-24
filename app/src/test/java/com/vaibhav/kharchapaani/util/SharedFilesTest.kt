package com.vaibhav.kharchapaani.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Picked and shared files are read within a size the phone can hold (security audit, 2026-10-24). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SharedFilesTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun file(size: Int): Uri =
        Uri.fromFile(File(context.cacheDir, "shared-$size.bin").apply { writeBytes(ByteArray(size) { (it % 251).toByte() }) })

    @Test
    fun `a 108 MP photo is decoded at most 2048 on its long side, keeping its shape`() {
        assertEquals(2048 to 1536, SharedFiles.targetSize(12000, 9000, SharedFiles.MAX_IMAGE_SIDE))
        assertEquals(1536 to 2048, SharedFiles.targetSize(9000, 12000, SharedFiles.MAX_IMAGE_SIDE))
        assertEquals("a picture that fits is left alone", 1080 to 2400, SharedFiles.targetSize(1080, 2400, 2400))
    }

    @Test
    fun `the older decoder samples down to just above the cap`() {
        // 12000 / 4 = 3000, / 8 = 1500: 4 keeps it at or above 2048, as close as a power of two gets.
        assertEquals(4, SharedFiles.sampleSize(12000, 9000, 2048))
        assertEquals(1, SharedFiles.sampleSize(1080, 2400, 2048))
    }

    @Test
    fun `a file over the limit is refused without reading it all`() = runBlocking {
        assertEquals(SharedFiles.Read.TooLarge, SharedFiles.readBytes(context, file(300_000), limit = 200_000))
    }

    @Test
    fun `a file within the limit comes back whole`() = runBlocking {
        val read = SharedFiles.readBytes(context, file(150_000), limit = 200_000)

        assertTrue(read is SharedFiles.Read.Bytes)
        assertArrayEquals(ByteArray(150_000) { (it % 251).toByte() }, (read as SharedFiles.Read.Bytes).bytes)
    }

    @Test
    fun `an empty or missing file is unreadable`() = runBlocking {
        assertEquals(SharedFiles.Read.Unreadable, SharedFiles.readBytes(context, file(0), limit = 10))
        assertEquals(SharedFiles.Read.Unreadable, SharedFiles.readBytes(context, Uri.fromFile(File(context.cacheDir, "missing.bin")), limit = 10))
    }
}
