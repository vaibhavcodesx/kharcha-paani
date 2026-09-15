package com.vaibhav.kharchapaani.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The server refused the request's content (4xx other than auth), so retrying the same rows can't
 * succeed. Network, auth and 5xx failures are not mapped to this: they're worth retrying.
 */
class RowsRejectedException(val statusCode: Int, message: String) : Exception(message)

interface TransactionRemote {
    /**
     * Insert-or-update by id. Safe to repeat: the id is the device-generated remoteId.
     * @throws RowsRejectedException if the server rejects the rows themselves.
     */
    suspend fun upsert(rows: List<TransactionUpload>)

    /**
     * Marks rows deleted; ids the server never received are a no-op.
     * @throws RowsRejectedException if RLS refuses the update (a group expense in an archived group).
     */
    suspend fun softDelete(remoteIds: List<String>)

    /** Own personal rows with updated_at >= [since] (all rows if null), oldest first. */
    suspend fun changedSince(since: String?, limit: Int): List<RemoteTransaction>
}

class SupabaseTransactionRemote(
    private val supabase: SupabaseClient,
    private val ownerId: String,
) : TransactionRemote {

    override suspend fun upsert(rows: List<TransactionUpload>) {
        try {
            supabase.from(TABLE).upsert(rows) { onConflict = "id" }
        } catch (e: RestException) {
            val retryable = e.statusCode == 401 || e.statusCode == 403 || e.statusCode >= 500
            if (retryable) throw e
            throw RowsRejectedException(e.statusCode, e.error)
        }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun softDelete(remoteIds: List<String>) {
        // deleted_at is informational; the trigger's updated_at is what other devices pull on.
        try {
            supabase.from(TABLE).update(buildJsonObject { put("deleted_at", Clock.System.now().toString()) }) {
                // No owner filter: the payer of a groupmate's expense deletes it too (item 11), and RLS
                // already limits the update to rows this account may change. With the filter, a
                // payer's delete matched nothing and silently stayed on the server.
                filter {
                    isIn("id", remoteIds)
                }
            }
        } catch (e: RestException) {
            // A soft delete of a row we may change can only be refused by an RLS check (the row's group
            // is archived or we left it), which retrying never fixes. Expired sessions are 401.
            if (e.statusCode == 401 || e.statusCode >= 500) throw e
            throw RowsRejectedException(e.statusCode, e.error)
        }
    }

    override suspend fun changedSince(since: String?, limit: Int): List<RemoteTransaction> =
        supabase.from(TABLE).select {
            filter {
                eq("owner_id", ownerId)
                // Step 3 is personal transactions only; group rows arrive with step 4.
                exact("group_id", null)
                if (since != null) gte("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList()

    private companion object {
        const val TABLE = "transactions"
    }
}
