package com.example.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val avatarColor: String, // hex or preset identifier
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "groups",
    indices = [Index(value = ["name"])]
)
data class GroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String, // e.g. "Lonavala 2026", "Flatmates", "Goa Trip"
    val description: String = "",
    val emoji: String = "🏖️",
    val colorHex: String = "#FF7A3D",
    val createdAt: Long = System.currentTimeMillis()
)

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
    indices = [Index(value = ["date"]), Index(value = ["groupId"])]
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
    val createdAt: Long = System.currentTimeMillis()
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
    indices = [Index(value = ["transactionId"])]
)
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val totalAmount: Double,
    val paidByPersonId: Long = -1L, // -1 means "Me", otherwise PersonEntity.id
    val splitType: String = "equal" // "equal", "exact", "percentage"
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
    val percentage: Double? = null
)

@Entity(
    tableName = "settlements",
    indices = [Index(value = ["fromPersonId"]), Index(value = ["toPersonId"]), Index(value = ["date"]), Index(value = ["groupId"])]
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
    val createdAt: Long = System.currentTimeMillis()
)
