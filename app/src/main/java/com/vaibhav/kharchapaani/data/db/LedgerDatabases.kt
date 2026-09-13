package com.vaibhav.kharchapaani.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One Room database file per signed-in account (`ledger-<userId>.db`), so a second person
 * signing in on the same phone never sees the first person's ledger. Each file is that
 * account's local cache: DAO queries need no account filter because the file itself is scoped.
 *
 * Only the signed-in account's database is open at a time; opening another account's closes it.
 */
object LedgerDatabases {

    /** The pre-accounts, unscoped database. Claimed by the first account to sign in. */
    const val LEGACY_DATABASE_NAME = "splitflow.db"

    private const val TAG = "LedgerDatabases"
    private val USER_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private val lock = Mutex()
    private var open: OpenDatabase? = null

    private class OpenDatabase(val userId: String, val database: AppDatabase)

    suspend fun open(context: Context, userId: String): AppDatabase = lock.withLock {
        open?.takeIf { it.userId == userId }?.let { return it.database }

        val appContext = context.applicationContext
        val name = databaseName(userId)
        open?.database?.close()
        open = null

        val database = withContext(Dispatchers.IO) {
            if (claimLegacyDatabase(appContext, userId)) {
                Log.i(TAG, "Moved the pre-accounts ledger into this account's database")
            }
            AppDatabase.builder(appContext, name).build()
        }
        open = OpenDatabase(userId, database)
        database
    }

    /** Closes the open database, if any. The file stays on disk (signing out keeps local data). */
    suspend fun close() = lock.withLock {
        open?.database?.close()
        open = null
    }

    internal fun databaseName(userId: String): String {
        // The id lands in a file path; only accept the Supabase auth UUID shape.
        require(USER_ID.matches(userId)) { "Not a Supabase user id: $userId" }
        return "ledger-${userId.lowercase()}.db"
    }

    /**
     * Renames the legacy database to [userId]'s file if it exists and that account has no file
     * yet. After the rename the legacy file is gone, so no other account can claim it.
     *
     * Must run before any connection to either file is open.
     */
    internal fun claimLegacyDatabase(context: Context, userId: String): Boolean {
        val legacy = context.getDatabasePath(LEGACY_DATABASE_NAME)
        val target = context.getDatabasePath(databaseName(userId))
        if (!legacy.exists() || target.exists()) return false

        // Room uses WAL, and committed rows may still sit in splitflow.db-wal. Fold them into the
        // main file first so renaming that single file carries every row.
        SQLiteDatabase.openDatabase(legacy.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
        }
        check(legacy.renameTo(target)) { "Couldn't move ${legacy.name} to ${target.name}" }
        SIDECAR_SUFFIXES.forEach { File(legacy.path + it).delete() }
        return true
    }

    private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")
}
