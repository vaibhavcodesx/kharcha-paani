package com.vaibhav.kharchapaani.data.receipts

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * The bill photos of one account, as files (docs/designs/bill-photos.md).
 *
 * `filesDir/receipts/<account>/`, not the cache, which the system clears when space runs low. A
 * photo chosen in the add sheet is written to `staged/` first and only moved in when the expense
 * saves, so closing the sheet leaves nothing behind.
 */
class ReceiptStore(val dir: File) {

    private val staged = File(dir, STAGED)

    /** A picture written to staged/, not yet anyone's bill. */
    data class Staged(val file: File, val width: Int, val height: Int) {
        val bytes: Long get() = file.length()
    }

    /**
     * [bitmap] as a bill photo: at most [MAX_SIDE] on its long side, JPEG at [QUALITY]. Written from
     * the pixels, so nothing of the original file's metadata comes along: no EXIF, so no GPS
     * location, phone model or time. Null when it can't be written (out of storage, say).
     */
    suspend fun stage(bitmap: Bitmap): Staged? = withContext(Dispatchers.IO) {
        try {
            staged.mkdirs()
            val scaled = scaledDown(bitmap)
            val file = File(staged, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)) {
                    file.delete()
                    return@withContext null
                }
            }
            Staged(file, scaled.width, scaled.height).also { if (scaled !== bitmap) scaled.recycle() }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep that bill photo", e)
            null
        }
    }

    /** Moves a staged photo in; returns the name to store, or null if the move failed. */
    fun commit(photo: Staged): String? {
        val target = File(dir, photo.file.name)
        // Recovery can find a committed file whose Receipt row was not written before process death.
        return if (photo.file.canonicalFile == target.canonicalFile && target.isFile || photo.file.renameTo(target)) target.name else null
    }

    /** Only this ledger's strict JPEG leaf, checked against the durable size/hash before use. */
    suspend fun recoverStaged(fileName: String, width: Int, height: Int, bytes: Long, sha256: String): Staged? =
        withContext(Dispatchers.IO) {
            if (!SAFE_JPEG.matches(fileName) || width !in 1..MAX_SIDE || height !in 1..MAX_SIDE || bytes !in 1..16_777_216L ||
                !Regex("[0-9a-f]{64}").matches(sha256)) return@withContext null
            listOf(File(staged, fileName), File(dir, fileName)).firstOrNull { file ->
                file.isFile && file.canonicalFile.parentFile in setOf(staged.canonicalFile, dir.canonicalFile) &&
                    file.length() == bytes && hash(file) == sha256
            }?.let { Staged(it, width, height) }
        }

    suspend fun hashOf(photo: Staged): String = withContext(Dispatchers.IO) { hash(photo.file) }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun discard(photo: Staged) {
        photo.file.delete()
    }

    fun file(fileName: String): File = File(dir, fileName)

    fun delete(fileNames: Collection<String>) {
        fileNames.forEach { File(dir, it).delete() }
    }

    /**
     * Deletes photos no row points at (their expense went by a route that doesn't know about files: a
     * sync pull, a groupmate's delete, a friend's removal), and staged ones older than [stagedMaxAge].
     * Returns how many went.
     */
    suspend fun sweep(referenced: Set<String>, now: Long = System.currentTimeMillis(), stagedMaxAge: Long = DAY_MILLIS, protectedNames: Set<String> = emptySet()): Int =
        withContext(Dispatchers.IO) {
            var deleted = 0
            dir.listFiles()?.forEach { file ->
                // An hour's grace: a photo is moved in just before its row is written, and a sweep
                // that ran in between would delete a bill that's about to be someone's.
                val orphan = file.isFile && file.name !in referenced && file.name !in protectedNames && now - file.lastModified() > ORPHAN_GRACE
                if (orphan && file.delete()) deleted++
            }
            staged.listFiles()?.forEach { file ->
                if (file.name !in protectedNames && now - file.lastModified() > stagedMaxAge && file.delete()) deleted++
            }
            deleted
        }

    companion object {
        private const val TAG = "ReceiptStore"
        private const val STAGED = "staged"
        private val SAFE_JPEG = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.jpg")
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        private const val ORPHAN_GRACE = 60 * 60 * 1000L

        /** Long side of a kept bill. A receipt's small print still reads; a photo is about 200–400 KB. */
        const val MAX_SIDE = 2000
        const val QUALITY = 80

        /** Per account, like the ledger file, so one account never sees another's bills. */
        fun forAccount(context: Context, userId: String): ReceiptStore =
            ReceiptStore(File(File(context.filesDir, "receipts"), LedgerDatabases.databaseName(userId).removeSuffix(".db")))

        internal fun scaledDown(bitmap: Bitmap, maxSide: Int = MAX_SIDE): Bitmap {
            val longest = maxOf(bitmap.width, bitmap.height)
            if (longest <= maxSide) return bitmap
            val scale = maxSide.toDouble() / longest
            return Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        }
    }
}
