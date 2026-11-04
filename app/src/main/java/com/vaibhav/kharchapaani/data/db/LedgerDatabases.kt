package com.vaibhav.kharchapaani.data.db

import android.content.Context
import com.vaibhav.kharchapaani.data.settings.GuestSession
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

    /** A hook must use captured DAOs/files only; it must never call a LedgerDatabases opener or close(). */
    fun interface CloseOwner {
        suspend fun beforeClose()
    }

    /** The pre-accounts, unscoped database. Claimed by the first account to sign in. */
    const val LEGACY_DATABASE_NAME = "splitflow.db"

    private const val TAG = "LedgerDatabases"
    private val USER_ID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

    private val lock = Mutex()
    private var open: OpenDatabase? = null

    private class OpenDatabase(val name: String, val database: AppDatabase) {
        val owners = mutableListOf<CloseOwner>()
        suspend fun drainOwners() {
            owners.forEach { it.beforeClose() }
            // Only a fully successful drain releases owners. Cancellation/failure retains
            // every callback for a safe, idempotent retry of unfinished closing work.
            owners.clear()
        }
    }

    suspend fun open(context: Context, userId: String, isCurrent: () -> Boolean = { true }): AppDatabase =
        openNamed(context, databaseName(userId), legacyOwner = userId, isCurrent = isCurrent)

    /** Local ledgers never claim the pre-accounts database, even on the very first launch. */
    suspend fun openLocal(context: Context, guest: GuestSession, isCurrent: () -> Boolean = { true }): AppDatabase =
        openNamed(context, localDatabaseName(guest), legacyOwner = null, isCurrent = isCurrent)

    /** Session ownership is established before any competing opener can close this handle. */
    suspend fun openOwned(
        context: Context,
        userId: String,
        isLocal: Boolean,
        isCurrent: () -> Boolean = { true },
        ownerFactory: (AppDatabase) -> CloseOwner,
    ): AppDatabase = openNamed(
        context,
        if (isLocal) localDatabaseName(GuestSession(userId)) else databaseName(userId),
        legacyOwner = if (isLocal) null else userId,
        isCurrent = isCurrent,
        ownerFactory = ownerFactory,
    )

    internal fun localDatabaseName(guest: GuestSession): String = "ledger-local-${guest.id}.db"

    private suspend fun openNamed(
        context: Context,
        name: String,
        legacyOwner: String?,
        isCurrent: () -> Boolean,
        ownerFactory: ((AppDatabase) -> CloseOwner)? = null,
    ): AppDatabase = lock.withLock {
        // The caller may have waited behind a mode/account switch. Reject it before even
        // returning a cached connection, and especially before closing the current ledger.
        check(isCurrent()) { "Ledger selection changed before opening" }
        open?.takeIf { it.name == name }?.let { current ->
            if (ownerFactory != null) {
                // A new session must not initialize over a destroyed owner's canceled restore.
                // Plain cached background reads retain their fast path and never clear the UI.
                current.drainOwners()
                check(isCurrent()) { "Ledger selection changed while replacing its owner" }
            }
            attachOwner(current, ownerFactory)
            check(isCurrent()) { "Ledger selection changed while creating its owner" }
            return current.database
        }

        val appContext = context.applicationContext
        open?.drainOwners()
        // Owner cleanup suspends; an obsolete opener must not close the now-selected ledger.
        check(isCurrent()) { "Ledger selection changed while closing its owner" }
        open?.database?.close()
        open = null

        val database = withContext(Dispatchers.IO) {
            if (legacyOwner != null && claimLegacyDatabase(appContext, legacyOwner)) {
                Log.i(TAG, "Moved the pre-accounts ledger into this account's database")
            }
            AppDatabase.builder(appContext, name).build()
        }
        val current = OpenDatabase(name, database)
        open = current
        attachOwner(current, ownerFactory)
        check(isCurrent()) { "Ledger selection changed while creating its owner" }
        database
    }

    // Called only while lock is held. Factories create ownership, never ViewModels, DAO work
    // or registry calls. Main construction and hook publication finish before the handle escapes.
    private suspend fun attachOwner(current: OpenDatabase, factory: ((AppDatabase) -> CloseOwner)?) {
        if (factory != null) current.owners += withContext(Dispatchers.Main.immediate) { factory(current.database) }
    }

    /** Closes the open database, if any. The file stays on disk (signing out keeps local data). */
    suspend fun close() = lock.withLock {
        open?.drainOwners()
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
