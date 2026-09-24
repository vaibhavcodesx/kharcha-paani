package com.vaibhav.kharchapaani.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Whether splits and IOUs with friends who have no account sync to the owner's private tables.
 *
 * **On since 1.3.0** (owner decision, after `20260923000000_owner_private_local_ledger` was applied
 * and verified on 2026-09-24). Only records made from then on sync: anything already on a phone as
 * LOCAL_ONLY stays there, even when edited (see LedgerRepository.syncStateFor). Turning this off
 * again stops new records syncing and leaves the tables untouched. docs/local-friend-sync-v2.md.
 */
object LocalFriendSync {
    const val ENABLED = true
}

private const val LOCAL_SPLIT_TOMBSTONE = "local_split:"

/**
 * The tombstone key for retracting a personal expense's private split. Prefixed because tombstones
 * are keyed by remoteId alone, and the expense's own key is cleared whenever it's queued again.
 */
fun localSplitTombstone(transactionRemoteId: String): String = LOCAL_SPLIT_TOMBSTONE + transactionRemoteId

internal fun transactionIdOfSplitTombstone(key: String): String = key.removePrefix(LOCAL_SPLIT_TOMBSTONE)

/** The personal expense `save_local_split` writes alongside its split; never has a group. */
@Serializable
data class LocalTransactionPayload(
    val id: String,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    @SerialName("payment_method") val paymentMethod: String,
    val date: String,
    val note: String,
    @SerialName("created_at") val createdAt: String,
)

/** One share of a personal split: [localName] null is the owner. */
@Serializable
data class LocalSharePayload(
    @SerialName("local_name") val localName: String?,
    @SerialName("share_amount") val shareAmount: Double,
    val percentage: Double? = null,
)

/** What `save_local_split` takes as its split, and what a pulled `local_splits` row carries. */
@Serializable
data class LocalSplitPayload(
    val id: String,
    @SerialName("split_type") val splitType: String,
    @SerialName("total_amount") val totalAmount: Double,
    /** Null means the owner paid. */
    @SerialName("paid_by_local_name") val paidByLocalName: String?,
    val shares: List<LocalSharePayload>,
)

@Serializable
data class RemoteLocalSplit(
    val id: String,
    @SerialName("transaction_id") val transactionId: String,
    @SerialName("split_type") val splitType: String = "equal",
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("paid_by_local_name") val paidByLocalName: String? = null,
    val shares: List<LocalSharePayload>,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class LocalSettlementPayload(
    val id: String,
    /** Null is the owner, on either side. */
    @SerialName("from_local_name") val fromLocalName: String?,
    @SerialName("to_local_name") val toLocalName: String?,
    val amount: Double,
    @SerialName("is_iou") val isIou: Boolean,
    val date: String,
    val note: String,
    @SerialName("created_at") val createdAt: String,
    /** Always sent, as null on a save, so re-saving revives a retracted row (last write wins). */
    @SerialName("deleted_at") val deletedAt: String?,
)

@Serializable
data class RemoteLocalSettlement(
    val id: String,
    @SerialName("from_local_name") val fromLocalName: String? = null,
    @SerialName("to_local_name") val toLocalName: String? = null,
    val amount: Double,
    @SerialName("is_iou") val isIou: Boolean = false,
    val date: String,
    val note: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

interface LocalLedgerRemote {
    /** The personal expense and its split in one statement. @throws RowsRejectedException */
    suspend fun saveLocalSplit(transaction: LocalTransactionPayload, split: LocalSplitPayload)

    /** Soft-deletes the split hanging off each of these personal expenses. */
    suspend fun retractLocalSplits(transactionIds: List<String>)

    suspend fun splitsChangedSince(since: String?, limit: Int): List<RemoteLocalSplit>

    /** @throws RowsRejectedException */
    suspend fun upsertSettlements(rows: List<LocalSettlementPayload>)

    suspend fun retractSettlements(ids: List<String>)

    suspend fun settlementsChangedSince(since: String?, limit: Int): List<RemoteLocalSettlement>
}

class SupabaseLocalLedgerRemote(
    private val supabase: SupabaseClient,
    private val ownerId: String,
) : LocalLedgerRemote {

    private val json = Json { encodeDefaults = true; explicitNulls = true }

    override suspend fun saveLocalSplit(transaction: LocalTransactionPayload, split: LocalSplitPayload) = rejecting {
        supabase.postgrest.rpc(
            "save_local_split",
            buildJsonObject {
                put("p_transaction", json.encodeToJsonElement(transaction))
                put("p_split", json.encodeToJsonElement(split))
            },
        )
        Unit
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun retractLocalSplits(transactionIds: List<String>) = rejecting {
        supabase.from(SPLITS).update(buildJsonObject { put("deleted_at", Clock.System.now().toString()) }) {
            filter { isIn("transaction_id", transactionIds) }
        }
        Unit
    }

    override suspend fun splitsChangedSince(since: String?, limit: Int): List<RemoteLocalSplit> =
        supabase.from(SPLITS).select {
            filter {
                eq("owner_id", ownerId)
                if (since != null) gte("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList()

    override suspend fun upsertSettlements(rows: List<LocalSettlementPayload>) = rejecting {
        supabase.from(SETTLEMENTS).upsert(rows) { onConflict = "id" }
        Unit
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun retractSettlements(ids: List<String>) = rejecting {
        supabase.from(SETTLEMENTS).update(buildJsonObject { put("deleted_at", Clock.System.now().toString()) }) {
            filter { isIn("id", ids) }
        }
        Unit
    }

    override suspend fun settlementsChangedSince(since: String?, limit: Int): List<RemoteLocalSettlement> =
        supabase.from(SETTLEMENTS).select {
            filter {
                eq("owner_id", ownerId)
                if (since != null) gte("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList()

    /** Same split as the other remotes: auth and server errors retry, anything else is final. */
    private suspend fun <T> rejecting(block: suspend () -> T): T = try {
        block()
    } catch (e: RestException) {
        if (e.statusCode == 401 || e.statusCode >= 500) throw e
        throw RowsRejectedException(e.statusCode, e.error)
    }

    private companion object {
        const val SPLITS = "local_splits"
        const val SETTLEMENTS = "local_settlements"
    }
}
