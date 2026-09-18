package com.vaibhav.kharchapaani.data.sync

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.math.BigDecimal
import java.math.RoundingMode

@Serializable
data class RemoteProfile(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("avatar_emoji") val avatarEmoji: String? = null,
    /** Where to pay them, for settle-up. Format-checked by the server and re-checked before use. */
    @SerialName("upi_id") val upiId: String? = null,
)

@Serializable
data class RemoteGroupMember(
    @SerialName("profile_id") val profileId: String,
    /** Null when RLS hides the profile; a groupmate's profile is always visible to members. */
    val profiles: RemoteProfile? = null,
)

/** A `public.groups` row with its members embedded. */
@Serializable
data class RemoteGroup(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val description: String = "",
    @SerialName("color_hex") val colorHex: String = "#FF7A3D",
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("archived_at") val archivedAt: String? = null,
    @SerialName("group_members") val members: List<RemoteGroupMember> = emptyList(),
)

@Serializable
data class RemoteShare(
    val id: String,
    @SerialName("profile_id") val profileId: String,
    @SerialName("share_amount") val shareAmount: Double,
    val percentage: Double? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class RemoteSplit(
    val id: String,
    @SerialName("paid_by") val paidBy: String,
    @SerialName("split_type") val splitType: String = "equal",
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("split_shares") val shares: List<RemoteShare> = emptyList(),
)

/** A group expense with its split embedded, pulled as one unit keyed on the parent's updated_at. */
@Serializable
data class RemoteGroupTransaction(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    @SerialName("payment_method") val paymentMethod: String,
    val date: String,
    val note: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    /**
     * PostgREST embeds a one-to-one relation (splits has a unique transaction_id) as an object or
     * null, and older servers as an array, so this stays raw until [split] reads it.
     */
    @SerialName("splits") val splitJson: JsonElement? = null,
) {
    val split: RemoteSplit?
        get() = when (val json = splitJson) {
            null, JsonNull -> null
            is JsonArray -> json.firstOrNull()?.let { lenientJson.decodeFromJsonElement(RemoteSplit.serializer(), it) }
            is JsonObject -> lenientJson.decodeFromJsonElement(RemoteSplit.serializer(), json)
            else -> null
        }

    /** Removed shares stay on the server as history; only active ones count. */
    val activeShares: List<RemoteShare> get() = split?.shares.orEmpty().filter { it.deletedAt == null }
}

@Serializable
data class RemoteSettlement(
    val id: String,
    @SerialName("from_profile_id") val fromProfileId: String,
    @SerialName("to_profile_id") val toProfileId: String,
    val amount: Double,
    val date: String,
    val note: String = "",
    @SerialName("is_iou") val isIou: Boolean = false,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/** The three arguments of `save_group_expense`. Amounts are sent as exact 2-decimal numbers. */
data class GroupExpenseUpload(
    val transaction: GroupTransactionPayload,
    val split: SplitPayload,
    val shares: List<SharePayload>,
)

data class GroupTransactionPayload(
    val id: String,
    val type: String,
    val amount: BigDecimal,
    val category: String,
    val merchant: String,
    val paymentMethod: String,
    val date: String,
    val note: String,
    val groupId: String,
    val createdAt: String,
)

data class SplitPayload(val id: String, val paidBy: String, val splitType: String, val totalAmount: BigDecimal)

data class SharePayload(val profileId: String, val shareAmount: BigDecimal, val percentage: Double?)

/**
 * What a settlement push sends. No updated_at (the trigger owns it); [deletedAt] is always an
 * explicit null so a queued edit revives a settlement another device retracted, as for transactions.
 */
@Serializable
data class SettlementUpload(
    val id: String,
    @SerialName("from_profile_id") val fromProfileId: String,
    @SerialName("to_profile_id") val toProfileId: String,
    val amount: Double,
    val date: String,
    val note: String,
    @SerialName("is_iou") val isIou: Boolean,
    @SerialName("group_id") val groupId: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

internal fun money(amount: Double): BigDecimal = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)

private val lenientJson = Json { ignoreUnknownKeys = true }
