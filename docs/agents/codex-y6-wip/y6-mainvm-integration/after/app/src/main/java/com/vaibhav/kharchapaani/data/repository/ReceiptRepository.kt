package com.vaibhav.kharchapaani.data.repository

import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.ReceiptEntity
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import kotlinx.coroutines.CancellationException
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
    suspend fun attach(transactionId: Long, photo: ReceiptStore.Staged, preserveForDraftRecovery: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val fileName = store.commit(photo) ?: return@withContext false.also { if (!preserveForDraftRecovery) store.discard(photo) }
        val replaced = try {
            db.withTransaction {
                val before = dao.forTransaction(transactionId).map { it.fileName }
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
            if (preserveForDraftRecovery) {
                // Only an already-durable draft pins this strict leaf against orphan maintenance.
                // Keep the journaled file for retry in either its staged or committed location.
                if (e is CancellationException) throw e
            } else {
                // Preserve the established cleanup contract for callers without a recovery journal.
                store.delete(listOf(fileName))
            }
            return@withContext false
        }
        store.delete(replaced.filterNot { it == fileName })
        true
    }

    /** Takes the expense's photo off it. */
    suspend fun remove(transactionId: Long) = withContext(Dispatchers.IO) {
        val names = db.withTransaction {
            dao.forTransaction(transactionId).map { it.fileName }.also { dao.deleteForTransaction(transactionId) }
        }
        store.delete(names)
    }

    /** The files of [transactionId]'s photos, read before its delete so they can be removed after it. */
    suspend fun fileNamesOf(transactionId: Long): List<String> = withContext(Dispatchers.IO) {
        dao.forTransaction(transactionId).map { it.fileName }
    }

    /** After the expense's delete commits: its rows went by cascade, these are their files. */
    suspend fun deleteFiles(fileNames: List<String>) = withContext(Dispatchers.IO) {
        store.delete(fileNames)
    }

    /** See [ReceiptStore.sweep]. Run when the account's ledger opens. */
    suspend fun sweepOrphans(now: Long = System.currentTimeMillis(), protectedNames: Set<String> = emptySet()): Int =
        store.sweep(withContext(Dispatchers.IO) { dao.allFileNames().toSet() }, now, protectedNames = protectedNames)
}
