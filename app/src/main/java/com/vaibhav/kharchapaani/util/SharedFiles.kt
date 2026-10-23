package com.vaibhav.kharchapaani.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

/**
 * Reading a picture or a file someone picked or shared into the app, off the main thread and within
 * a size the phone can hold.
 *
 * The first versions decoded the whole picture on the main thread: a 108 MP camera photo is
 * 400 MB and more as a bitmap, and a several-hundred-MB "PDF" was read into memory before its size
 * was checked. Either froze the app or crashed it (security audit, 2026-10-24).
 */
object SharedFiles {

    private const val TAG = "SharedFiles"

    /**
     * The long side a picture is decoded at. Gemini gets it scaled to 1600 px anyway
     * (GeminiRepository.bitmapToBase64); a little more keeps that step a downscale.
     */
    const val MAX_IMAGE_SIDE = 2048

    /** The picture at [uri], no longer than [maxSide] on its long side; null when it can't be read. */
    suspend fun decodeImage(context: Context, uri: Uri, maxSide: Int = MAX_IMAGE_SIDE): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    val (width, height) = targetSize(info.size.width, info.size.height, maxSide)
                    if (width != info.size.width) decoder.setTargetSize(width, height)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = true
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                    inMutable = true
                }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read that picture", e)
            null
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "That picture was too big to open", e)
            null
        }
    }

    /** What reading a file came to. */
    sealed interface Read {
        class Bytes(val bytes: ByteArray) : Read
        /** Longer than the limit; nothing past it was read. */
        data object TooLarge : Read
        data object Unreadable : Read
    }

    /** The file at [uri], reading no more than one byte past [limit]. */
    suspend fun readBytes(context: Context, uri: Uri, limit: Int): Read = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri) ?: return@withContext Read.Unreadable
            stream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > limit) return@withContext Read.TooLarge
                }
                if (out.size() == 0) Read.Unreadable else Read.Bytes(out.toByteArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read that file", e)
            Read.Unreadable
        }
    }

    /** [width] × [height] scaled so the long side is at most [maxSide]; unchanged when it already fits. */
    internal fun targetSize(width: Int, height: Int, maxSide: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxSide) return width to height
        val scale = maxSide.toDouble() / longest
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /** The power-of-two sample that brings the long side to [maxSide] or just above, for BitmapFactory. */
    internal fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}
