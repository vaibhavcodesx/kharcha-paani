package com.vaibhav.kharchapaani.domain

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A portable offline copy. Record ids belong to this archive, never to an authenticated session. */
@Serializable
data class EverythingBackup(
    val format: String = "kharcha-paani-everything",
    val version: Int = 1,
    val exportedAt: Long,
    val appVersion: String,
    val people: List<EverythingPerson> = emptyList(),
    val groups: List<EverythingGroup> = emptyList(),
    val members: List<EverythingGroupMember> = emptyList(),
    val transactions: List<EverythingTransaction> = emptyList(),
    val splits: List<EverythingSplit> = emptyList(),
    val shares: List<EverythingSplitShare> = emptyList(),
    val settlements: List<EverythingSettlement> = emptyList(),
    val subscriptions: List<EverythingSubscription> = emptyList(),
    val paymentAsks: List<EverythingPaymentAsk> = emptyList(),
    val receipts: List<EverythingReceipt> = emptyList(),
    /** Historical person references left by deleting a friend; no invented replacement row. */
    val missingPersonIds: List<Long> = emptyList(),
    val settings: EverythingSettings = EverythingSettings(),
) {
    companion object {
        const val MAX_JSON_BYTES = 64 * 1024 * 1024
        const val MAX_PHOTO_BYTES = 8 * 1024 * 1024
        const val MAX_TOTAL_PHOTO_BYTES = 32 * 1024 * 1024
        const val MAX_ROWS = 100_000
        private val json = Json { encodeDefaults = true }

        fun encode(backup: EverythingBackup): String {
            backup.validate()
            return json.encodeToString(backup).also {
                require(it.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "Backup is too large" }
            }
        }

        /** Caller maps IllegalArgumentException to its import error; no writes occur during decode. */
        fun decode(text: String): EverythingBackup {
            require(text.length <= MAX_JSON_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_JSON_BYTES) { "Backup is too large" }
            return json.decodeFromString<EverythingBackup>(text).also { it.validate() }
        }
    }
}

/** User choices only: no login, AI disclosure acceptance, OS permissions or crash-report consent. */
@Serializable
data class EverythingSettings(
    val customExpenseCategories: List<String> = emptyList(),
    val customIncomeCategories: List<String> = emptyList(),
    val hiddenCategories: List<String> = emptyList(),
    val customPaymentMethods: List<String> = emptyList(),
    val hiddenPaymentMethods: List<String> = emptyList(),
    val defaultCategory: String? = null,
    val defaultPaymentMethod: String? = null,
    val askBeforeDiscarding: Boolean = true,
    val reminderEnabled: Boolean = true,
    val reminderMinute: Int = 21 * 60,
    val paymentsToConfirm: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStart: Int = 22 * 60 + 30,
    val quietHoursEnd: Int = 8 * 60,
    val groupSort: String? = null,
    val upiId: String? = null,
    val upiName: String? = null,
    val aiEnabled: Boolean = true,
)

@Serializable
data class EverythingPerson(
    val id: Long,
    val name: String,
    val avatarColor: String,
    val createdAt: Long,
    val profileId: String?,
    val avatarUrl: String?,
    val avatarEmoji: String?,
    val upiId: String?,
    val hue: Int?,
)

@Serializable
data class EverythingGroup(
    val id: Long,
    val name: String,
    val description: String,
    val emoji: String,
    val colorHex: String,
    val createdAt: Long,
    val remoteId: String,
    val updatedAt: Long,
    val syncState: String,
    val archivedAt: Long?,
    val createdByProfileId: String?,
)

@Serializable
data class EverythingGroupMember(
    val id: Long,
    val groupId: Long,
    val personId: Long,
)

@Serializable
data class EverythingTransaction(
    val id: Long,
    val type: String,
    val amount: Double,
    val category: String,
    val merchant: String,
    val paymentMethod: String,
    val date: Long,
    val note: String,
    val isSplit: Boolean,
    val groupId: Long?,
    val createdAt: Long,
    val remoteId: String,
    val updatedAt: Long,
    val syncState: String,
    val ownerProfileId: String?,
)

@Serializable
data class EverythingSplit(
    val id: Long,
    val transactionId: Long,
    val totalAmount: Double,
    val paidByPersonId: Long,
    val splitType: String,
    val remoteId: String,
)

@Serializable
data class EverythingSplitShare(
    val id: Long,
    val splitId: Long,
    val personId: Long,
    val shareAmount: Double,
    val percentage: Double?,
    val guestName: String?,
    val guestPersonId: Long?,
)

@Serializable
data class EverythingSettlement(
    val id: Long,
    val fromPersonId: Long,
    val toPersonId: Long,
    val amount: Double,
    val date: Long,
    val note: String,
    val isIou: Boolean,
    val groupId: Long?,
    val createdAt: Long,
    val remoteId: String,
    val updatedAt: Long,
    val syncState: String,
    val status: String,
    val recordedByPersonId: Long?,
    val respondedAt: Long?,
    val responseNote: String?,
    val pendingResponse: String?,
    val remindedStage: Int,
)

@Serializable
data class EverythingSubscription(
    val id: Long,
    val name: String,
    val amount: Double,
    val category: String,
    val paymentMethod: String,
    val cycle: String,
    val startDate: Long,
    val chargedThrough: Long?,
    val cancelledAt: Long?,
    val createdAt: Long,
)

@Serializable
data class EverythingPaymentAsk(
    val id: Long,
    val personId: Long,
    val amount: Double,
    val sentAt: Long,
)

@Serializable
data class EverythingReceipt(
    val id: Long,
    val transactionId: Long,
    val fileName: String,
    val width: Int,
    val height: Int,
    val bytes: Long,
    val createdAt: Long,
    val remotePath: String?,
    val contentBase64: String,
)

/** Validate every reference before any target database or filesystem is touched. */
fun EverythingBackup.validate() {
    require(format == "kharcha-paani-everything" && version == 1) { "Unsupported backup format" }
    val tables = listOf(people.map { it.id }, groups.map { it.id }, members.map { it.id },
        transactions.map { it.id }, splits.map { it.id }, shares.map { it.id }, settlements.map { it.id },
        subscriptions.map { it.id }, paymentAsks.map { it.id }, receipts.map { it.id })
    require(tables.sumOf { it.size.toLong() } <= EverythingBackup.MAX_ROWS) { "Too many backup rows" }
    tables.forEach { ids -> require(ids.all { it > 0 } && ids.toSet().size == ids.size) { "Duplicate or invalid record id" } }
    val personIds = people.map { it.id }.toSet()
    val groupIds = groups.map { it.id }.toSet()
    val transactionIds = transactions.map { it.id }.toSet()
    val splitIds = splits.map { it.id }.toSet()
    val missing = missingPersonIds.toSet()
    require(missing.size == missingPersonIds.size && missing.all { it > 0 && it !in personIds }) { "Invalid missing-friend declaration" }
    require(missing == referencedPersonIds().filter { it != -1L && it !in personIds }.toSet()) { "Undeclared missing friend reference" }
    fun person(id: Long) = require(id == -1L || id in personIds || id in missing) { "Unknown friend reference" }
    fun group(id: Long?) = require(id == null || id in groupIds) { "Unknown group reference" }
    fun money(value: Double, zero: Boolean = false) = require(value.isFinite() && value >= 0 && (zero || value > 0) && value <= MAX_AMOUNT_RUPEES) { "Invalid money value" }
    fun sync(value: String) = require(value in setOf("LOCAL_ONLY", "PENDING", "SYNCED", "FAILED")) { "Invalid sync state" }
    fun status(value: String?) = require(value == null || value in setOf("PENDING", "CONFIRMED", "REJECTED")) { "Invalid settlement status" }
    fun unique(values: List<String>) = require(values.toSet().size == values.size) { "Duplicate remote identity" }
    unique(people.mapNotNull { it.profileId })
    unique(groups.map { it.remoteId }); unique(transactions.map { it.remoteId })
    unique(splits.map { it.remoteId }); unique(settlements.map { it.remoteId })
    groups.forEach { sync(it.syncState) }
    members.forEach { require(it.groupId in groupIds) { "Unknown member group" }; person(it.personId) }
    transactions.forEach { money(it.amount); group(it.groupId); sync(it.syncState); require(it.type in setOf("expense", "income")) { "Invalid transaction type" } }
    require(splits.map { it.transactionId }.toSet().size == splits.size) { "Multiple splits for one expense" }
    splits.forEach {
        require(it.transactionId in transactionIds) { "Unknown split expense" }
        person(it.paidByPersonId); money(it.totalAmount)
        require(it.splitType.lowercase() in setOf("equal", "exact", "percentage")) { "Invalid split type" }
    }
    shares.forEach {
        require(it.splitId in splitIds) { "Unknown share split" }; person(it.personId)
        it.guestPersonId?.let(::person); money(it.shareAmount, zero = true)
        require(it.percentage == null || (it.percentage.isFinite() && it.percentage in 0.0..100.0)) { "Invalid percentage" }
    }
    val sharesBySplit = shares.groupBy { it.splitId }
    splits.forEach { split ->
        val rows = sharesBySplit[split.id].orEmpty()
        val total = rows.sumOf { it.shareAmount }
        // Old ledgers may retain only surviving shares; never invent money for a missing share.
        require(total.isFinite() && total <= split.totalAmount + 0.01 * rows.size + 0.001) { "Shares exceed split total" }
    }
    settlements.forEach {
        person(it.fromPersonId); person(it.toPersonId); it.recordedByPersonId?.let(::person)
        money(it.amount); group(it.groupId); sync(it.syncState); status(it.status); status(it.pendingResponse)
    }
    subscriptions.forEach { money(it.amount); require(it.cycle in setOf("WEEKLY", "MONTHLY", "YEARLY")) { "Invalid subscription cycle" } }
    paymentAsks.forEach { require(it.personId in personIds) { "An ask must reference an existing friend" }; money(it.amount) }
    require(receipts.map { it.fileName }.toSet().size == receipts.size) { "Duplicate receipt filename" }
    var totalPhotoBytes = 0L
    receipts.forEach {
        require(it.transactionId in transactionIds) { "Unknown receipt expense" }
        require(safeReceiptName(it.fileName)) { "Unsafe receipt filename" }
        require(it.width > 0 && it.height > 0 && it.bytes in 1..EverythingBackup.MAX_PHOTO_BYTES.toLong()) { "Invalid receipt metadata" }
        require(it.contentBase64.length <= ((EverythingBackup.MAX_PHOTO_BYTES + 2L) / 3 * 4)) { "Receipt is too large" }
        val bytes = try { Base64.decode(it.contentBase64, Base64.NO_WRAP) } catch (e: IllegalArgumentException) { throw IllegalArgumentException("Invalid receipt content", e) }
        require(Base64.encodeToString(bytes, Base64.NO_WRAP) == it.contentBase64) { "Invalid receipt encoding" }
        require(bytes.size.toLong() == it.bytes) { "Receipt length does not match metadata" }
        totalPhotoBytes += bytes.size
        require(totalPhotoBytes <= EverythingBackup.MAX_TOTAL_PHOTO_BYTES) { "Receipt collection is too large" }
    }
    require(settings.reminderMinute in 0..1439 && settings.quietHoursStart in 0..1439 && settings.quietHoursEnd in 0..1439) { "Invalid reminder time" }
}

internal fun safeReceiptName(name: String): Boolean =
    name.isNotBlank() && name != "." && name != ".." && name.length <= 128 &&
        name.all { it.isLetterOrDigit() && it.code < 128 || it in "._-" }

/** Person columns deliberately have no Room FK; friend deletion can leave historical references. */
internal fun EverythingBackup.referencedPersonIds(): List<Long> = buildList {
    addAll(members.map { it.personId })
    addAll(splits.map { it.paidByPersonId })
    shares.forEach { add(it.personId); it.guestPersonId?.let(::add) }
    settlements.forEach { add(it.fromPersonId); add(it.toPersonId); it.recordedByPersonId?.let(::add) }
}
