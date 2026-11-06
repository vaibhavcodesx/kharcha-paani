package com.vaibhav.kharchapaani.data.repository

import android.util.AtomicFile
import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.domain.EverythingBackup
import com.vaibhav.kharchapaani.domain.EverythingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.security.MessageDigest

/** Local recovery receipt, not a sync cursor or a remote-write queue. Never exported. */
internal const val EVERYTHING_RESTORE_MARKER = "local_everything_restore_sha256"

/**
 * Owns one PRIVATE local-session journal. Never use an account's DB or preferences here.
 * The caller must keep the local session pinned for the whole operation and run resume before
 * enabling normal ledger/settings actions. applySettings must be an idempotent, single-store edit.
 * A failed settings edit leaves committed rows + journal for a later resume, never a second import.
 */
class EverythingRestoreCoordinator(
    private val db: AppDatabase,
    private val repository: EverythingBackupRepository,
    private val privateJournalFile: File,
    private val applySettings: suspend (EverythingSettings) -> Unit,
) {
    private val journal = AtomicFile(privateJournalFile)

    suspend fun restore(backup: EverythingBackup, isLocal: Boolean) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(isLocal) { "Complete restore requires a local session" }
            val text = EverythingBackup.encode(backup)
            val bytes = text.toByteArray(Charsets.UTF_8)
            val hash = sha256(bytes)
            if (hasJournal()) {
                val pending = readJournal()
                require(pending.hash == hash) { "Another restore needs recovery first" }
                finish(pending, reuseMatchingReceipts = true)
            } else {
                // Core checks again at commit time. Refuse occupied targets before writing a journal.
                db.withTransaction { requireEmptyTarget() }
                writeJournal(bytes)
                finish(Pending(backup, hash), reuseMatchingReceipts = false)
            }
        }
    }

    /** True only when a pending restore was finished. An absent journal never imports anything. */
    suspend fun resume(isLocal: Boolean): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(isLocal) { "Complete restore requires a local session" }
            if (!hasJournal()) false else {
                finish(readJournal(), reuseMatchingReceipts = true)
                true
            }
        }
    }

    private suspend fun finish(pending: Pending, reuseMatchingReceipts: Boolean) {
        val marker = db.syncDao().meta(EVERYTHING_RESTORE_MARKER)
        require(marker == null || marker == pending.hash) { "Restore journal does not match this ledger" }
        if (marker == null) {
            repository.restoreIntoEmptyLocalLedger(
                pending.backup,
                restoreMarker = pending.hash,
                reuseMatchingReceipts = reuseMatchingReceipts,
            )
        }
        // Matching marker proves the exact archive's rows committed in the same Room transaction.
        // Keep the journal until the settings edit returns successfully; retries only repeat this edit.
        applySettings(pending.backup.settings)
        journal.delete()
        check(!hasJournal()) { "Could not finish restore journal cleanup" }
    }

    private fun hasJournal(): Boolean =
        privateJournalFile.exists() || File(privateJournalFile.path + ".bak").exists()

    private fun writeJournal(bytes: ByteArray) {
        require(bytes.size <= EverythingBackup.MAX_JSON_BYTES) { "Restore journal is too large" }
        val parent = requireNotNull(privateJournalFile.absoluteFile.parentFile)
        require(parent.mkdirs() || parent.isDirectory) { "Restore journal directory is unavailable" }
        val output = journal.startWrite()
        try {
            output.write(bytes)
            output.fd.sync()
            // AtomicFile syncs the file and renames it before any Room rows can be restored.
            journal.finishWrite(output)
            check(hasJournal() && readJournal().hash == sha256(bytes)) { "Restore journal was not saved" }
        } catch (failure: Throwable) {
            journal.failWrite(output)
            throw failure
        }
    }

    private fun readJournal(): Pending {
        val bytes = journal.openRead().use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                total += count
                require(total <= EverythingBackup.MAX_JSON_BYTES) { "Restore journal is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val text = try {
            Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
        } catch (failure: CharacterCodingException) {
            throw IllegalArgumentException("Invalid restore journal encoding", failure)
        }
        return Pending(EverythingBackup.decode(text), sha256(bytes))
    }

    private fun requireEmptyTarget() {
        val tables = listOf("people", "groups", "group_members", "transactions", "splits", "split_shares",
            "settlements", "subscriptions", "payment_asks", "receipts", "sync_meta", "pending_remote_deletes")
        tables.forEach { table ->
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
                check(it.moveToFirst())
                require(it.getLong(0) == 0L) { "Restore requires an empty local ledger" }
            }
        }
    }

    private data class Pending(val backup: EverythingBackup, val hash: String)

    companion object {
        // Prevent a recreated UI/coordinator racing another instance in this app process.
        private val mutex = Mutex()
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}
