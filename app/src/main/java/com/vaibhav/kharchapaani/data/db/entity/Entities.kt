package com.vaibhav.kharchapaani.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "people",
    indices = [Index(value = ["profileId"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarColor: String, // hex or preset identifier
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * The Supabase profile this person is, for members of shared groups. Null for local-only
     * friends, who can't take part in anything that syncs (decision 1: every participant in a
     * shared split is a registered user).
     */
    val profileId: String? = null,
    val avatarUrl: String? = null,
    /** A groupmate's chosen emoji, shown instead of initials (backlog item 6). */
    val avatarEmoji: String? = null,
    /**
     * A groupmate's UPI handle, so settling up with them can show where to pay.
     *
     * Never trusted as stored: it is interpolated into a upi://pay deep link, so every read goes
     * back through [com.vaibhav.kharchapaani.domain.UpiLink.cleanId] first. The server has a
     * matching check constraint; neither layer is relied on alone.
     */
    val upiId: String? = null,
    /**
     * A groupmate's own colour, as the OKLCH hue they chose on their profile (v1.4, design item
     * 11): the same on every phone. When set, [avatarColor] is derived from it and never moved by
     * PersonColours.respace. Null for friends not on the app and for anyone who hasn't chosen.
     */
    val hue: Int? = null,
)

@Entity(
    tableName = "groups",
    indices = [
        Index(value = ["name"]),
        Index(value = ["remoteId"], unique = true),
        Index(value = ["syncState"]),
    ]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, // e.g. "Lonavala 2026", "Flatmates", "Goa Trip"
    val description: String = "",
    val emoji: String = "🏖️",
    val colorHex: String = "#FF7A3D",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val remoteId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** LOCAL_ONLY groups are private to this phone (decision 7); any other state is a shared group. */
    @ColumnInfo(defaultValue = "LOCAL_ONLY") val syncState: SyncState = SyncState.LOCAL_ONLY,
    val archivedAt: Long? = null,
    val createdByProfileId: String? = null,
) {
    val isShared: Boolean get() = syncState != SyncState.LOCAL_ONLY
    val isArchived: Boolean get() = archivedAt != null
}

@Entity(
    tableName = "group_members",
    foreignKeys = [
        ForeignKey(
            entity = GroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["groupId"]), Index(value = ["personId"])]
)
data class GroupMemberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val personId: Long // -1 means "Me", otherwise PersonEntity.id
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["date"]),
        Index(value = ["groupId"]),
        Index(value = ["remoteId"], unique = true),
        Index(value = ["syncState"]),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "expense" or "income"
    val amount: Double,
    val category: String, // Food, Transport, Shopping, Bills, Entertainment, Health, Rent, Team Payment, Income (Client), Income (Other), Other
    val merchant: String,
    val paymentMethod: String, // PopUPI, GPay, PhonePe, Paytm, Debit Card, Credit Card, Cash, Bank Transfer, Other
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val isSplit: Boolean = false,
    val groupId: Long? = null, // Optional link to GroupEntity
    val createdAt: Long = System.currentTimeMillis(),
    /** Generated on-device; doubles as the Supabase row id, so retried pushes are idempotent. */
    @ColumnInfo(defaultValue = "") val remoteId: String = UUID.randomUUID().toString(),
    /** Local write time. Pushes only mark a row synced if this hasn't changed since it was read. */
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "LOCAL_ONLY") val syncState: SyncState = SyncState.LOCAL_ONLY,
    /** Who added it, when that isn't this account (a groupmate's expense): read-only here (decision 2). */
    val ownerProfileId: String? = null,
) {
    val isOwnedByMe: Boolean get() = ownerProfileId == null
}

enum class SyncState {
    /** Matches the server copy as of the last push or pull. */
    SYNCED,

    /** Changed locally; the next push uploads it. */
    PENDING,

    /**
     * Never uploaded: pre-sync history (handled by step 5), local-group rows, and splits and IOUs
     * that can't sync, including those with friends not on the app from before 1.3 (forward-only,
     * docs/local-friend-sync-v2.md).
     */
    LOCAL_ONLY,

    /** The server rejected this row; it stays local and retries after the next edit. */
    FAILED,
}

/** A delete the server hasn't heard about yet. The local row is already gone. */
@Entity(tableName = "pending_remote_deletes")
data class PendingRemoteDeleteEntity(
    @PrimaryKey val remoteId: String,
    val deletedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = TYPE_TRANSACTION) val entityType: String = TYPE_TRANSACTION,
) {
    companion object {
        const val TYPE_TRANSACTION = "transaction"
        const val TYPE_SETTLEMENT = "settlement"

        /** Keyed by the personal expense's remoteId: its private split, not the expense. */
        const val TYPE_LOCAL_SPLIT = "local_split"

        /** A settlement or IOU with a friend who has no account (`local_settlements`). */
        const val TYPE_LOCAL_SETTLEMENT = "local_settlement"
    }
}

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(
    tableName = "splits",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["transactionId"]), Index(value = ["remoteId"], unique = true)]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val totalAmount: Double,
    val paidByPersonId: Long = -1L, // -1 means "Me", otherwise PersonEntity.id
    val splitType: String = "equal", // "equal", "exact", "percentage"
    /** The server split id for group expenses; sent with every save so edits keep the same split. */
    @ColumnInfo(defaultValue = "") val remoteId: String = UUID.randomUUID().toString(),
)

@Entity(
    tableName = "split_shares",
    foreignKeys = [
        ForeignKey(
            entity = SplitEntity::class,
            parentColumns = ["id"],
            childColumns = ["splitId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["splitId"]), Index(value = ["personId"])]
)
data class SplitShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val personId: Long, // -1 means "Me", otherwise PersonEntity.id
    val shareAmount: Double,
    val percentage: Double? = null,
    /**
     * Set when this share is a guest's: a friend with no account in a shared-group expense, covered
     * by the member who added it (design item 9). [personId] is then the host, so every group
     * balance already treats the share as theirs; this is the guest's name, shown on the expense.
     */
    val guestName: String? = null,
    /**
     * On the host's own phone only: which of their friends the guest is, so the friend owes the
     * host their share. Null on every other phone (they don't know the host's friends).
     */
    val guestPersonId: Long? = null,
) {
    val isGuest: Boolean get() = guestName != null
}

@Entity(
    tableName = "settlements",
    indices = [
        Index(value = ["fromPersonId"]),
        Index(value = ["toPersonId"]),
        Index(value = ["date"]),
        Index(value = ["groupId"]),
        Index(value = ["remoteId"], unique = true),
        Index(value = ["syncState"]),
    ]
)
data class SettlementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromPersonId: Long, // -1 means "Me", otherwise PersonEntity.id
    val toPersonId: Long,   // -1 means "Me", otherwise PersonEntity.id
    val amount: Double,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val isIou: Boolean = false, // true = informal IOU / direct debt, false = debt settlement
    val groupId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "") val remoteId: String = UUID.randomUUID().toString(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    /** PENDING/SYNCED only inside shared groups; everything else stays LOCAL_ONLY (decision 1). */
    @ColumnInfo(defaultValue = "LOCAL_ONLY") val syncState: SyncState = SyncState.LOCAL_ONLY,
    /**
     * v1.4 settlement confirmation (design item 10). Only a shared-group settlement can be anything
     * but CONFIRMED: it is PENDING until the other party answers, and only CONFIRMED moves balances.
     */
    @ColumnInfo(defaultValue = "CONFIRMED") val status: SettlementStatus = SettlementStatus.CONFIRMED,
    /** Who recorded it (-1 = me). Null for anything from before confirmation, and local ones. */
    val recordedByPersonId: Long? = null,
    val respondedAt: Long? = null,
    val responseNote: String? = null,
    /** My answer, saved here at once and sent by the next sync (respond_to_settlement). */
    val pendingResponse: SettlementStatus? = null,
    /** The last reminder stage already shown for this one (0 = when it arrived, 1 = day 3, …). */
    @ColumnInfo(defaultValue = "-1") val remindedStage: Int = -1,
) {
    val isConfirmed: Boolean get() = status == SettlementStatus.CONFIRMED
}

enum class SettlementStatus { PENDING, CONFIRMED, REJECTED }

/**
 * A fixed charge on a fixed rhythm (backlog item 8). It's a rule, not money: the ledger only gets a
 * real transaction once a due date has arrived, created by LedgerRepository.runDueSubscriptions.
 */
@Entity(tableName = "subscriptions", indices = [Index(value = ["cancelledAt"])])
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Double,
    val category: String,
    val paymentMethod: String,
    /** WEEKLY, MONTHLY or YEARLY (domain.SubscriptionCycle). */
    val cycle: String,
    /** The first charge, as a local date at midnight. */
    val startDate: Long,
    /** The last due date already in the ledger, so a charge is never created twice. */
    val chargedThrough: Long? = null,
    val cancelledAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
