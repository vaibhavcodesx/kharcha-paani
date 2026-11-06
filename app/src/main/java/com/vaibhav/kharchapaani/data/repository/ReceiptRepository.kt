package com.vaibhav.kharchapaani.data.repository

import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.ReceiptEntity
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bill photos against the ledger: a row per photo, the file in [store]. Rows go with their expense
 * (foreign key, cascade); a file is deleted only after the row that named it is gone for good, so a
 * failed transaction never leaves a row pointing at nothing.
 */
class ReceiptRepository(private val db: AppDatabase, val store: ReceiptStore) {

    private val dao = db.receiptDao()

    /** Ids of the expenses with a photo. */
    val transactionIds: Flow<Set<Long>> = dao.observeTransactionIds().map { it.toSet() }

    /** The photo of [transactionId], and its file, or null when it has none. */
    suspend fun photoOf(transactionId: Long): Pair<ReceiptEntity, File>? = withContext(Dispatchers.IO) {
        dao.forTransaction(transactionId).firstOrNull()?.let { it to store.file(it.fileName) }
    }

    /** Makes [photo] the expense's one photo, replacing any it had. False if it couldn't be kept. */
    suspend fun attach(transactionId: Long, photo: ReceiptStore.Staged): Boolean = withContext(Dispatchers.IO) {
        val fileName = store.commit(photo) ?: return@withContext false.also { store.discard(photo) }
        val replaced = try {
            db.withTransaction {
                val old = dao.forTransaction(transactionId)
                com.vaibhav.kharchapaani.data.sync.ReceiptDeleteQueue.add(db, old.mapNotNull { it.remotePath })
                val before = old.map { it.fileName }
                dao.deleteForTransaction(transactionId)
                dao.insert(
                    ReceiptEntity(
                        transactionId = transactionId,
                        fileName = fileName,
                        width = photo.width,
                        height = photo.height,
                        bytes = store.file(fileName).length(),
                    )
                )
                before
            }
        } catch (e: Exception) {
            // The expense went (a delete raced the save) or the database refused: the file is no one's.
            store.delete(listOf(fileName))
            return@withContext false
        }
        store.delete(replaced)
        true
    }

    /** Takes the expense's photo off it. */
    suspend fun remove(transactionId: Long) = withContext(Dispatchers.IO) {
        val names = db.withTransaction {
            val rows = dao.forTransaction(transactionId)
            // The cloud copy goes on the next sync; queued in the same transaction, so it can't be lost.
            com.vaibhav.kharchapaani.data.sync.ReceiptDeleteQueue.add(db, rows.mapNotNull { it.remotePath })
            dao.deleteForTransaction(transactionId)
            rows.map { it.fileName }
        }
        store.delete(names)
    }

    /** The files of [transactionId]'s photos, read before its delete so they can be removed after it. */
    suspend fun fileNamesOf(transactionId: Long): List<String> = withContext(Dispatchers.IO) {
        dao.forTransaction(transactionId).map { it.fileName }
    }

    /** The cloud copies of [transactionId]'s photos, read before its delete like [fileNamesOf]. */
    suspend fun remotePathsOf(transactionId: Long): List<String> = withContext(Dispatchers.IO) {
        dao.forTransaction(transactionId).mapNotNull { it.remotePath }
    }

    /** After the expense's delete commits, its cloud copies go on the next sync. */
    suspend fun forgetRemote(paths: List<String>) = withContext(Dispatchers.IO) {
        com.vaibhav.kharchapaani.data.sync.ReceiptDeleteQueue.add(db, paths)
    }

    /**
     * Brings a photo that's only in the cloud onto this phone, as an ordinary bill photo. Null when it
     * can't (offline, or no longer shared); the row stays, so it can be tried again.
     */
    suspend fun download(receipt: ReceiptEntity, remote: com.vaibhav.kharchapaani.data.receipts.ReceiptRemote): File? =
        withContext(Dispatchers.IO) {
            val path = receipt.remotePath ?: return@withContext null
            val target = store.file(receipt.fileName)
            if (target.exists()) return@withContext target
            val bytes = runCatching { remote.download(path) }.getOrNull() ?: return@withContext null
            store.dir.mkdirs()
            val part = File(store.dir, receipt.fileName + ".part")
            part.writeBytes(bytes)
            if (!part.renameTo(target)) return@withContext null.also { part.delete() }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(target.path, bounds)
            dao.setDownloaded(receipt.id, bounds.outWidth.coerceAtLeast(0), bounds.outHeight.coerceAtLeast(0), target.length())
            target
        }

    /** After the expense's delete commits: its rows went by cascade, these are their files. */
    suspend fun deleteFiles(fileNames: List<String>) = withContext(Dispatchers.IO) {
        store.delete(fileNames)
    }

    /** See [ReceiptStore.sweep]. Run when the account's ledger opens. */
    suspend fun sweepOrphans(now: Long = System.currentTimeMillis()): Int =
        store.sweep(withContext(Dispatchers.IO) { dao.allFileNames().toSet() }, now)
}
