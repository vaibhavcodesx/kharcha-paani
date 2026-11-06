package com.vaibhav.kharchapaani.data.receipts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/**
 * The cloud copy of a bill photo (docs/designs/bill-photo-sync.md): smaller than the one on the
 * phone, because it's for seeing the bill and getting it back on a new phone, not for reading it
 * again. Encoded from pixels, so no EXIF (and no location) leaves the phone.
 */
object CloudCopy {
    /** Long side of the cloud copy. */
    const val MAX_SIDE = 1280

    /** The bucket refuses anything bigger (1 MB); the encoder stays far under it. */
    const val MAX_BYTES = 1_048_576

    class Encoded(val bytes: ByteArray, val extension: String, val contentType: String)

    /** Null when [file] can't be read as a picture or still comes out too big. */
    fun encode(file: File, sdk: Int = Build.VERSION.SDK_INT): Encoded? {
        val bitmap = decodeScaled(file) ?: return null
        try {
            val scaled = ReceiptStore.scaledDown(bitmap, MAX_SIDE)
            val out = ByteArrayOutputStream()
            // Lossy WebP needs API 30; below that, JPEG at about the same visual quality. [sdk] lets
            // a test ask for the JPEG path; lint needs to see the real SDK_INT check as well.
            val encoded = if (sdk >= Build.VERSION_CODES.R && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, WEBP_QUALITY, out)
                Encoded(out.toByteArray(), "webp", "image/webp")
            } else {
                scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                Encoded(out.toByteArray(), "jpg", "image/jpeg")
            }
            if (scaled !== bitmap) scaled.recycle()
            return encoded.takeIf { it.bytes.isNotEmpty() && it.bytes.size <= MAX_BYTES }
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodes at a power-of-two reduction that still leaves at least [MAX_SIDE], to save memory. */
    private fun decodeScaled(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /** `<uploader>/<transaction>/<photo>.<ext>`: the server only accepts this shape in your own folder. */
    fun path(ownerId: String, transactionRemoteId: String, extension: String, photoId: String = UUID.randomUUID().toString()) =
        "$ownerId/$transactionRemoteId/$photoId.$extension"

    private const val WEBP_QUALITY = 70
    private const val JPEG_QUALITY = 72
}

/** Storage plus the one column that shares a photo. An interface so the engine is testable. */
interface ReceiptRemote {
    suspend fun upload(path: String, bytes: ByteArray, contentType: String)
    suspend fun download(path: String): ByteArray
    suspend fun delete(paths: List<String>)

    /** Everything in [ownerId]'s folder, from every phone they used: run before the account goes. */
    suspend fun deleteFolder(ownerId: String)

    /** Sets or clears `transactions.receipt_storage_path`; RLS limits it to rows this account may change. */
    suspend fun setSharedPath(transactionRemoteId: String, path: String?)

    /** `receipt_storage_path` of the given transactions that this account can see and that have one. */
    suspend fun sharedPaths(transactionRemoteIds: List<String>): Map<String, String>
}

class SupabaseReceiptRemote(private val supabase: SupabaseClient) : ReceiptRemote {
    private val bucket get() = supabase.storage.from(BUCKET)

    override suspend fun upload(path: String, bytes: ByteArray, contentType: String) {
        bucket.upload(path, bytes) {
            // A path is never reused: a retry after an upload that did land is a 409, handled by the engine.
            upsert = false
            this.contentType = ContentType.parse(contentType)
        }
    }

    override suspend fun download(path: String): ByteArray = bucket.downloadAuthenticated(path)

    override suspend fun delete(paths: List<String>) {
        if (paths.isNotEmpty()) bucket.delete(paths)
    }

    override suspend fun deleteFolder(ownerId: String) {
        // <owner>/<transaction>/<photo>: one listing for the transactions, one per transaction.
        val transactions = bucket.list(ownerId) { limit = LIST_LIMIT }.map { it.name }
        val files = transactions.flatMap { tx ->
            bucket.list("$ownerId/$tx") { limit = LIST_LIMIT }.map { "$ownerId/$tx/${it.name}" }
        }
        files.chunked(CHUNK).forEach { bucket.delete(it) }
    }

    override suspend fun setSharedPath(transactionRemoteId: String, path: String?) {
        supabase.from(TABLE).update(buildJsonObject {
            if (path == null) put(COLUMN, JsonNull) else put(COLUMN, path)
        }) {
            filter { eq("id", transactionRemoteId) }
        }
    }

    override suspend fun sharedPaths(transactionRemoteIds: List<String>): Map<String, String> =
        transactionRemoteIds.chunked(CHUNK).flatMap { ids ->
            supabase.from(TABLE).select(io.github.jan.supabase.postgrest.query.Columns.list("id", COLUMN)) {
                // Null paths are dropped below: they're expenses with nothing shared.
                filter { isIn("id", ids) }
            }.decodeList<SharedPathRow>()
        }.mapNotNull { row -> row.path?.let { row.id to it } }.toMap()

    @Serializable
    private data class SharedPathRow(val id: String, @SerialName(COLUMN) val path: String? = null)

    private companion object {
        const val BUCKET = "receipts"
        const val TABLE = "transactions"
        const val COLUMN = "receipt_storage_path"
        const val CHUNK = 100
        const val LIST_LIMIT = 1000
    }
}
