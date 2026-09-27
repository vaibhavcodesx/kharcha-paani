package com.vaibhav.kharchapaani.data.sync

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Group, group-expense and settlement sync against Supabase. Membership changes live in PR 4's UI remote. */
interface GroupRemote {
    /** Every group this account can see, with members. Includes archived groups. */
    suspend fun visibleGroups(): List<RemoteGroup>

    /** Idempotent for the creator: the id is generated on the device. */
    suspend fun createGroup(id: String, name: String, emoji: String?)

    /** Creator-only by RLS; a non-creator's update matches no rows. */
    suspend fun updateGroup(id: String, name: String, emoji: String?, description: String, colorHex: String, archivedAt: String?)

    suspend fun saveGroupExpense(expense: GroupExpenseUpload)

    /** Group expenses (any owner, any visible group) with updated_at >= [since], oldest first. */
    suspend fun groupExpensesChangedSince(since: String?, limit: Int): List<RemoteGroupTransaction>

    suspend fun upsertSettlements(rows: List<SettlementUpload>)

    suspend fun softDeleteSettlements(remoteIds: List<String>)

    suspend fun settlementsChangedSince(since: String?, limit: Int): List<RemoteSettlement>

    /** Confirms or rejects a settlement the other party recorded (`respond_to_settlement`). */
    suspend fun respondToSettlement(remoteId: String, confirm: Boolean, note: String?)
}

class SupabaseGroupRemote(private val supabase: SupabaseClient) : GroupRemote {

    override suspend fun visibleGroups(): List<RemoteGroup> =
        supabase.from("groups").select(Columns.raw(VISIBLE_GROUPS_COLUMNS)).decodeList()

    override suspend fun createGroup(id: String, name: String, emoji: String?) = rejectingContent {
        supabase.postgrest.rpc(
            "create_group",
            buildJsonObject {
                put("p_id", id)
                put("p_name", name)
                put("p_emoji", emoji)
            },
        )
        Unit
    }

    override suspend fun updateGroup(
        id: String,
        name: String,
        emoji: String?,
        description: String,
        colorHex: String,
        archivedAt: String?,
    ) = rejectingContent {
        supabase.from("groups").update(
            buildJsonObject {
                put("name", name)
                put("emoji", emoji)
                put("description", description)
                put("color_hex", colorHex)
                put("archived_at", archivedAt)
            }
        ) {
            filter { eq("id", id) }
        }
        Unit
    }

    override suspend fun saveGroupExpense(expense: GroupExpenseUpload) = rejectingContent {
        val tx = expense.transaction
        supabase.postgrest.rpc(
            "save_group_expense",
            buildJsonObject {
                put("p_transaction", buildJsonObject {
                    put("id", tx.id)
                    put("type", tx.type)
                    put("amount", JsonPrimitive(tx.amount))
                    put("category", tx.category)
                    put("merchant", tx.merchant)
                    put("payment_method", tx.paymentMethod)
                    put("date", tx.date)
                    put("note", tx.note)
                    put("group_id", tx.groupId)
                    put("created_at", tx.createdAt)
                })
                put("p_split", buildJsonObject {
                    put("id", expense.split.id)
                    put("paid_by", expense.split.paidBy)
                    put("split_type", expense.split.splitType)
                    put("total_amount", JsonPrimitive(expense.split.totalAmount))
                })
                put("p_shares", buildJsonArray {
                    expense.shares.forEach { share ->
                        add(buildJsonObject {
                            if (share.localName != null) put("local_name", share.localName) else put("profile_id", share.profileId)
                            put("share_amount", JsonPrimitive(share.shareAmount))
                            put("percentage", share.percentage?.let(::JsonPrimitive) ?: JsonNull)
                        })
                    }
                })
            },
        )
        Unit
    }

    override suspend fun groupExpensesChangedSince(since: String?, limit: Int): List<RemoteGroupTransaction> =
        supabase.from("transactions").select(
            Columns.raw("*, splits(id, paid_by, split_type, total_amount, split_shares(id, profile_id, local_name, share_amount, percentage, deleted_at))")
        ) {
            filter {
                filterNot("group_id", FilterOperator.IS, null)
                if (since != null) gte("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList()

    override suspend fun respondToSettlement(remoteId: String, confirm: Boolean, note: String?) = rejectingContent {
        supabase.postgrest.rpc(
            "respond_to_settlement",
            buildJsonObject {
                put("p_settlement_id", remoteId)
                put("p_confirm", confirm)
                put("p_note", note?.let(::JsonPrimitive) ?: JsonNull)
            },
        )
        Unit
    }

    override suspend fun upsertSettlements(rows: List<SettlementUpload>) = rejectingContent {
        supabase.from("settlements").upsert(rows) { onConflict = "id" }
        Unit
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun softDeleteSettlements(remoteIds: List<String>) = rejectingContent {
        supabase.from("settlements").update(buildJsonObject { put("deleted_at", Clock.System.now().toString()) }) {
            filter { isIn("id", remoteIds) }
        }
        Unit
    }

    override suspend fun settlementsChangedSince(since: String?, limit: Int): List<RemoteSettlement> =
        supabase.from("settlements").select {
            filter {
                filterNot("group_id", FilterOperator.IS, null)
                if (since != null) gte("updated_at", since)
            }
            order("updated_at", Order.ASCENDING)
            limit(limit.toLong())
        }.decodeList()

    /**
     * Unlike personal rows, a 403 here is final: RLS refuses group writes once the group is archived
     * or the writer has left it, and retrying would loop forever. Expired sessions come back as 401.
     */
    private suspend fun rejectingContent(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: RestException) {
            val retryable = e.statusCode == 401 || e.statusCode >= 500
            if (retryable) throw e
            throw RowsRejectedException(e.statusCode, e.error)
        }
    }

    companion object {
        /**
         * PostgREST returns the columns you name and nothing else, so a field missing from this
         * string arrives as its default rather than as an error — which is how every groupmate's
         * UPI ID silently stayed null on the device while the server was serving it correctly.
         * `GroupSelectColumnsTest` walks [RemoteProfile] and [RemoteGroupMember] and fails if a
         * field is ever added to either without being added here.
         */
        internal const val VISIBLE_GROUPS_COLUMNS =
            "*, group_members(profile_id, profiles(id, display_name, avatar_url, avatar_emoji, upi_id, hue))"
    }
}
