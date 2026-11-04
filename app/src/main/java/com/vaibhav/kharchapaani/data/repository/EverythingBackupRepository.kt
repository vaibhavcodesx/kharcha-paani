package com.vaibhav.kharchapaani.data.repository

import android.util.Base64
import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.domain.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream

/** Full archive, independent of cloud availability. No authentication or scheduling side effects. */
class EverythingBackupRepository(private val db: AppDatabase, private val receiptStore: ReceiptStore) {
    suspend fun collect(appVersion: String, settings: EverythingSettings = EverythingSettings()): EverythingBackup =
        withContext(Dispatchers.IO) {
            db.withTransaction {
                val transactions = db.transactionDao().getAllTransactionsDirect()
                val receipts = transactions.flatMap { db.receiptDao().forTransaction(it.id) }
                var photoBytes = 0L
                EverythingBackup(
                    exportedAt = System.currentTimeMillis(), appVersion = appVersion,
                    people = db.personDao().getAllPeopleDirect().map { it.archive() },
                    groups = db.groupDao().getAllGroupsDirect().map { it.archive() },
                    members = db.groupDao().getAllGroupMembersDirect().map { it.archive() },
                    transactions = transactions.map { it.archive() },
                    splits = db.splitDao().getAllSplitsDirect().map { it.archive() },
                    shares = db.splitDao().getAllSplitSharesDirect().map { it.archive() },
                    settlements = db.settlementDao().getAllSettlementsDirect().map { it.archive() },
                    subscriptions = db.subscriptionDao().getAllDirect().map { it.archive() },
                    paymentAsks = readAsks().map { it.archive() },
                    receipts = receipts.map { row ->
                        val file = safeReceipt(row.fileName)
                        require(row.bytes in 1..EverythingBackup.MAX_PHOTO_BYTES.toLong() && file.length() == row.bytes) { "Receipt is missing or changed" }
                        photoBytes += row.bytes
                        require(photoBytes <= EverythingBackup.MAX_TOTAL_PHOTO_BYTES) { "Receipt collection is too large" }
                        val content = readReceiptBytes(file, row.bytes)
                        row.archive(Base64.encodeToString(content, Base64.NO_WRAP))
                    }, settings = settings,
                ).let { archive ->
                    val present = archive.people.map { it.id }.toSet()
                    archive.copy(missingPersonIds = archive.referencedPersonIds()
                        .filter { it != -1L && it !in present }.distinct()).also { it.validate() }
                }
            }
        }

    /**
     * Only an EMPTY, local-mode ledger is a valid target. The caller owns that session guarantee.
     * Settings are returned for explicit UI application; this class never accesses DataStore.
     * Receipt files precede metadata; failures remove only files created here. A process crash may
     * leave harmless orphan files, never committed metadata pointing at a file not yet written.
     */
    suspend fun restoreIntoEmptyLocalLedger(backup: EverythingBackup): EverythingSettings = withContext(Dispatchers.IO) {
        backup.validate()
        val created = mutableListOf<File>()
        var committed = false
        try {
            db.withTransaction {
                requireEmpty()
                backup.receipts.forEach { receipt ->
                    val file = safeReceipt(receipt.fileName)
                    require(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory) { "Receipt directory is unavailable" }
                    require(file.createNewFile()) { "Receipt target already exists" }
                    created += file
                    file.outputStream().use { it.write(Base64.decode(receipt.contentBase64, Base64.NO_WRAP)) }
                }
                backup.people.forEach { db.personDao().insertPerson(it.entity()) }
                backup.groups.forEach { db.groupSyncDao().insertGroup(it.entity()) }
                db.groupSyncDao().insertMembers(backup.members.map { it.entity() })
                backup.transactions.forEach { db.transactionDao().insertTransaction(it.entity()) }
                backup.splits.forEach { db.groupSyncDao().insertSplit(it.entity()) }
                db.groupSyncDao().insertShares(backup.shares.map { it.entity() })
                backup.settlements.forEach { db.groupSyncDao().insertSettlement(it.entity()) }
                backup.subscriptions.forEach { db.subscriptionDao().insert(it.entity()) }
                backup.paymentAsks.forEach { db.paymentAskDao().insert(it.entity()) }
                backup.receipts.forEach { db.receiptDao().insert(it.entity()) }
            }
            committed = true
            backup.settings
        } finally {
            if (!committed) withContext(NonCancellable) {
                // Cancellation can be delivered after Room commits, before withTransaction returns.
                // Never delete a file whose metadata committed. An unavailable DB leaves orphans.
                runCatching { db.receiptDao().allFileNames().toSet() }.getOrNull()?.let { referenced ->
                    created.filter { it.name !in referenced }.forEach { it.delete() }
                }
            }
        }
    }

    private fun readReceiptBytes(file: File, expected: Long): ByteArray = file.inputStream().use { input ->
        val output = ByteArrayOutputStream(expected.toInt())
        val buffer = ByteArray(8192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count == -1) break
            total += count
            require(total <= expected) { "Receipt changed while exporting" }
            output.write(buffer, 0, count)
        }
        require(total == expected) { "Receipt changed while exporting" }
        output.toByteArray()
    }

    private fun safeReceipt(name: String): File {
        require(safeReceiptName(name)) { "Unsafe receipt filename" }
        val root = receiptStore.dir.canonicalFile
        val file = File(root, name)
        require(file.canonicalFile.parentFile == root) { "Receipt path leaves its directory" }
        return file
    }

    private fun requireEmpty() {
        val tables = listOf("people", "groups", "group_members", "transactions", "splits", "split_shares",
            "settlements", "subscriptions", "payment_asks", "receipts", "sync_meta", "pending_remote_deletes")
        tables.forEach { table ->
            db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table`").use {
                check(it.moveToFirst())
                require(it.getLong(0) == 0L) { "Restore requires an empty local ledger" }
            }
        }
    }

    // Existing DAO exposes only a Flow. A direct query keeps this gather inside the read transaction.
    private fun readAsks(): List<PaymentAskEntity> =
        db.openHelper.writableDatabase.query("SELECT id, personId, amount, sentAt FROM payment_asks ORDER BY id").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(PaymentAskEntity(cursor.getLong(0), cursor.getLong(1), cursor.getDouble(2), cursor.getLong(3)))
            }
        }
}

private fun PersonEntity.archive() = EverythingPerson(
    id = id,
    name = name,
    avatarColor = avatarColor,
    createdAt = createdAt,
    profileId = profileId,
    avatarUrl = avatarUrl,
    avatarEmoji = avatarEmoji,
    upiId = upiId,
    hue = hue,
)

private fun EverythingPerson.entity() = PersonEntity(
    id = id,
    name = name,
    avatarColor = avatarColor,
    createdAt = createdAt,
    profileId = profileId,
    avatarUrl = avatarUrl,
    avatarEmoji = avatarEmoji,
    upiId = upiId,
    hue = hue,
)

private fun GroupEntity.archive() = EverythingGroup(
    id = id,
    name = name,
    description = description,
    emoji = emoji,
    colorHex = colorHex,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    syncState = syncState.name,
    archivedAt = archivedAt,
    createdByProfileId = createdByProfileId,
)

private fun EverythingGroup.entity() = GroupEntity(
    id = id,
    name = name,
    description = description,
    emoji = emoji,
    colorHex = colorHex,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    // Shared history must keep shared-group balance semantics (partial historical shares differ
    // from a local group's spending). SYNCED queues nothing; the local session denies all cloud
    // membership/writes and never becomes an account session.
    syncState = if (syncState == SyncState.LOCAL_ONLY.name) SyncState.LOCAL_ONLY else SyncState.SYNCED,
    archivedAt = archivedAt,
    createdByProfileId = createdByProfileId,
)

private fun GroupMemberEntity.archive() = EverythingGroupMember(
    id = id,
    groupId = groupId,
    personId = personId,
)

private fun EverythingGroupMember.entity() = GroupMemberEntity(
    id = id,
    groupId = groupId,
    personId = personId,
)

private fun TransactionEntity.archive() = EverythingTransaction(
    id = id,
    type = type,
    amount = amount,
    category = category,
    merchant = merchant,
    paymentMethod = paymentMethod,
    date = date,
    note = note,
    isSplit = isSplit,
    groupId = groupId,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    syncState = syncState.name,
    ownerProfileId = ownerProfileId,
)

private fun EverythingTransaction.entity() = TransactionEntity(
    id = id,
    type = type,
    amount = amount,
    category = category,
    merchant = merchant,
    paymentMethod = paymentMethod,
    date = date,
    note = note,
    isSplit = isSplit,
    groupId = groupId,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    syncState = SyncState.LOCAL_ONLY,
    ownerProfileId = ownerProfileId,
)

private fun SplitEntity.archive() = EverythingSplit(
    id = id,
    transactionId = transactionId,
    totalAmount = totalAmount,
    paidByPersonId = paidByPersonId,
    splitType = splitType,
    remoteId = remoteId,
)

private fun EverythingSplit.entity() = SplitEntity(
    id = id,
    transactionId = transactionId,
    totalAmount = totalAmount,
    paidByPersonId = paidByPersonId,
    splitType = splitType,
    remoteId = remoteId,
)

private fun SplitShareEntity.archive() = EverythingSplitShare(
    id = id,
    splitId = splitId,
    personId = personId,
    shareAmount = shareAmount,
    percentage = percentage,
    guestName = guestName,
    guestPersonId = guestPersonId,
)

private fun EverythingSplitShare.entity() = SplitShareEntity(
    id = id,
    splitId = splitId,
    personId = personId,
    shareAmount = shareAmount,
    percentage = percentage,
    guestName = guestName,
    guestPersonId = guestPersonId,
)

private fun SettlementEntity.archive() = EverythingSettlement(
    id = id,
    fromPersonId = fromPersonId,
    toPersonId = toPersonId,
    amount = amount,
    date = date,
    note = note,
    isIou = isIou,
    groupId = groupId,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    syncState = syncState.name,
    status = status.name,
    recordedByPersonId = recordedByPersonId,
    respondedAt = respondedAt,
    responseNote = responseNote,
    pendingResponse = pendingResponse?.name,
    remindedStage = remindedStage,
)

private fun EverythingSettlement.entity() = SettlementEntity(
    id = id,
    fromPersonId = fromPersonId,
    toPersonId = toPersonId,
    amount = amount,
    date = date,
    note = note,
    isIou = isIou,
    groupId = groupId,
    createdAt = createdAt,
    remoteId = remoteId,
    updatedAt = updatedAt,
    syncState = SyncState.LOCAL_ONLY,
    status = SettlementStatus.valueOf(status),
    recordedByPersonId = recordedByPersonId,
    respondedAt = respondedAt,
    responseNote = responseNote,
    pendingResponse = null, // Never restore an unsent network action.
    remindedStage = remindedStage,
)

private fun SubscriptionEntity.archive() = EverythingSubscription(
    id = id,
    name = name,
    amount = amount,
    category = category,
    paymentMethod = paymentMethod,
    cycle = cycle,
    startDate = startDate,
    chargedThrough = chargedThrough,
    cancelledAt = cancelledAt,
    createdAt = createdAt,
)

private fun EverythingSubscription.entity() = SubscriptionEntity(
    id = id,
    name = name,
    amount = amount,
    category = category,
    paymentMethod = paymentMethod,
    cycle = cycle,
    startDate = startDate,
    chargedThrough = chargedThrough,
    cancelledAt = cancelledAt,
    createdAt = createdAt,
)

private fun PaymentAskEntity.archive() = EverythingPaymentAsk(
    id = id,
    personId = personId,
    amount = amount,
    sentAt = sentAt,
)

private fun EverythingPaymentAsk.entity() = PaymentAskEntity(
    id = id,
    personId = personId,
    amount = amount,
    sentAt = sentAt,
)

private fun ReceiptEntity.archive(contentBase64: String) = EverythingReceipt(
    id = id,
    transactionId = transactionId,
    fileName = fileName,
    width = width,
    height = height,
    bytes = bytes,
    createdAt = createdAt,
    remotePath = remotePath,
    contentBase64 = contentBase64,
)

private fun EverythingReceipt.entity() = ReceiptEntity(
    id = id,
    transactionId = transactionId,
    fileName = fileName,
    width = width,
    height = height,
    bytes = bytes,
    createdAt = createdAt,
    remotePath = remotePath,
)
