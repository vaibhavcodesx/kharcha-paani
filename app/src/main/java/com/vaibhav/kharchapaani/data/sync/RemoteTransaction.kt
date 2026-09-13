package com.vaibhav.kharchapaani.data.sync

import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A `public.transactions` row as pulled from Supabase. */
@Serializable
data class RemoteTransaction(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    @SerialName("payment_method") val paymentMethod: String,
    val date: String,
    val note: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

/**
 * What a push sends. No updated_at: the server trigger owns it. [deletedAt] has no default so it
 * is always encoded as an explicit null, which revives a row another device deleted when this
 * device's pending edit is pushed later (the approved last-write-wins rule).
 */
@Serializable
data class TransactionUpload(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    @SerialName("payment_method") val paymentMethod: String,
    val date: String,
    val note: String,
    @SerialName("group_id") val groupId: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String?,
)

@OptIn(ExperimentalTime::class)
fun TransactionEntity.toUpload(ownerId: String) = TransactionUpload(
    id = remoteId,
    ownerId = ownerId,
    type = type,
    // Postgres stores numeric(12,2); round here so the server never rounds differently.
    amount = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).toDouble(),
    category = category,
    merchant = merchant,
    paymentMethod = paymentMethod,
    date = Instant.fromEpochMilliseconds(date).toString(),
    note = note,
    groupId = null,
    createdAt = Instant.fromEpochMilliseconds(createdAt).toString(),
    deletedAt = null,
)

@OptIn(ExperimentalTime::class)
fun RemoteTransaction.toSyncedEntity() = TransactionEntity(
    type = type,
    amount = amount,
    category = category,
    merchant = merchant,
    paymentMethod = paymentMethod,
    date = parseTimestamp(date),
    note = note,
    isSplit = false,
    groupId = null,
    createdAt = parseTimestamp(createdAt),
    remoteId = id,
    updatedAt = parseTimestamp(updatedAt),
    syncState = SyncState.SYNCED,
)

@OptIn(ExperimentalTime::class)
internal fun parseTimestamp(value: String): Long = Instant.parse(value).toEpochMilliseconds()
