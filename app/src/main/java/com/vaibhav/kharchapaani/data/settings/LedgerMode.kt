package com.vaibhav.kharchapaani.data.settings

import android.content.Context
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.remote.BackgroundAuth
import java.util.UUID

/** A device ledger identity, never a Supabase account or an identity to upload. */
data class GuestSession(val id: String) {
    /** Settings stay isolated even if a cloud account happens to have this UUID. */
    val settingsId: String get() = "local-$id"

    init {
        require(UUID_PATTERN.matches(id)) { "Invalid local ledger id" }
    }

    private companion object {
        val UUID_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
    }
}

/** Read before constructing any cloud client, including in a cold background process. */
class LedgerMode(context: Context) {
    enum class Choice { LOCAL, CLOUD }
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Existing installs retain their account flow until the person explicitly chooses local mode.
    val choice: Choice get() = if (prefs.getString(MODE, null) == Choice.LOCAL.name) Choice.LOCAL else Choice.CLOUD

    fun guestOrNull(): GuestSession? = synchronized(lock) {
        if (choice != Choice.LOCAL) null else guestIdentity()
    }

    /** Identity and mode commit together, before any caller is allowed to open the ledger. */
    fun useLocal(): GuestSession = synchronized(lock) {
        val guest = guestIdentity()
        check(prefs.edit().putString(GUEST_ID, guest.id).putString(MODE, Choice.LOCAL.name).commit()) {
            "Couldn't save local ledger choice"
        }
        guest
    }

    fun useCloud() = synchronized(lock) {
        check(prefs.edit().putString(MODE, Choice.CLOUD.name).commit()) { "Couldn't save account ledger choice" }
    }

    private fun guestIdentity(): GuestSession {
        val id = prefs.getString(GUEST_ID, null)
        // A damaged stored identity must not silently select a fresh empty ledger.
        if (id != null) return GuestSession(id)
        check(choice != Choice.LOCAL) { "Local ledger identity is missing" }
        return GuestSession(UUID.randomUUID().toString())
    }

    companion object {
        internal const val PREFS = "ledger_mode"
        private const val MODE = "mode"
        private const val GUEST_ID = "guest_id"
        private val lock = Any()
    }
}

/** Shared by widgets, reminders and share callbacks; local resolution never touches auth. */
data class BackgroundLedger(val userId: String, val isLocal: Boolean) {
    // Resolver belongs to the descriptor, not its equality/copy fields. Keep application context only.
    private var cloudIdentity: (suspend () -> String?)? = null

    suspend fun <T> withDatabase(context: Context, block: suspend (AppDatabase) -> T): T {
        val app = context.applicationContext
        val mode = LedgerMode(app)
        return LedgerDatabases.withDatabase(app, userId, isLocal,
            isCurrent = {
                if (isLocal) mode.guestOrNull()?.id == userId else mode.choice == LedgerMode.Choice.CLOUD
            },
            verifyCurrent = if (isLocal) null else {
                suspend {
                    val resolver = cloudIdentity
                    mode.choice == LedgerMode.Choice.CLOUD &&
                        (if (resolver == null) BackgroundAuth.signedInUserId(app) else resolver()) == userId &&
                        mode.choice == LedgerMode.Choice.CLOUD
                }
            },
            block = block,
        )
    }

    suspend fun open(context: Context): AppDatabase {
        val mode = LedgerMode(context)
        return if (isLocal) {
            LedgerDatabases.openLocal(context, GuestSession(userId), isCurrent = {
                mode.guestOrNull()?.id == userId
            })
        } else {
            // Re-check mode without constructing an SDK; callers already resolved the cloud identity.
            LedgerDatabases.open(context, userId, isCurrent = {
                mode.choice == LedgerMode.Choice.CLOUD
            })
        }
    }

    companion object {
        suspend fun current(
            context: Context,
            cloudUserId: (suspend () -> String?)? = null,
        ): BackgroundLedger? {
            val mode = LedgerMode(context)
            mode.guestOrNull()?.let { return BackgroundLedger(it.id, true) }
            val userId = (if (cloudUserId == null) BackgroundAuth.signedInUserId(context) else cloudUserId()) ?: return null
            // A mode switch while cloud identity was loading must not open the previous ledger.
            if (mode.choice == LedgerMode.Choice.LOCAL) return null
            val app = context.applicationContext
            return BackgroundLedger(userId, false).also { ledger ->
                ledger.cloudIdentity = cloudUserId ?: suspend { BackgroundAuth.signedInUserId(app) }
            }
        }
    }
}
