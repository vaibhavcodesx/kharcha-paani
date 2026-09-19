package com.vaibhav.kharchapaani.data.repository

import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.domain.BackupGroup
import com.vaibhav.kharchapaani.domain.BackupPerson
import com.vaibhav.kharchapaani.domain.BackupSettlement
import com.vaibhav.kharchapaani.domain.BackupShare
import com.vaibhav.kharchapaani.domain.BackupSplit
import com.vaibhav.kharchapaani.domain.BackupSubscription
import com.vaibhav.kharchapaani.domain.LocalBackup
import com.vaibhav.kharchapaani.domain.RestoreReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reading and writing the local-only half of the ledger as a file.
 *
 * "Local-only" means precisely: rows the server has no way to hold, because every remote row that
 * names a person names them by profile id and a friend without an account has none. See
 * [LocalBackup] for the list and for why this is insurance rather than routine.
 */
class LocalBackupRepository(private val db: AppDatabase) {

    /** What the ledger calls "you". Never a row in `people`. */
    private val me = -1L

    suspend fun collect(appVersion: String): LocalBackup = withContext(Dispatchers.IO) {
        val people = db.personDao().getAllPeopleDirect()
        val local = people.filter { it.profileId == null }
        val localIds = local.map { it.id }.toSet()
        val nameOf = local.associate { it.id to it.name }

        val groups = db.groupDao().getAllGroupsDirect().filter { it.syncState == SyncState.LOCAL_ONLY }
        val members = db.groupDao().getAllGroupMembersDirect().groupBy { it.groupId }

        // Only the settlements the server cannot hold: one side has to be a local friend. A
        // settlement between two accounts comes back on its own.
        val settlements = db.settlementDao().getAllSettlementsDirect()
            .filter { it.fromPersonId in localIds || it.toPersonId in localIds }

        // Likewise splits. The *expense* may well sync — personal transactions do — but its shares
        // are stored remotely against profile ids, so a share belonging to a local friend is only
        // ever here. The expense rides along so a restore can rebuild the split either way.
        val splits = db.splitDao().getAllSplitsDirect().mapNotNull { split ->
            val shares = db.splitDao().getSharesForSplitDirect(split.id)
            if (shares.none { it.personId in localIds }) return@mapNotNull null
            val transaction = db.transactionDao().getTransactionByIdDirect(split.transactionId)
                ?: return@mapNotNull null
            BackupSplit(
                transactionRemoteId = transaction.remoteId.takeIf { it.isNotBlank() },
                merchant = transaction.merchant,
                category = transaction.category,
                paymentMethod = transaction.paymentMethod,
                date = transaction.date,
                note = transaction.note,
                totalAmount = split.totalAmount,
                splitType = split.splitType,
                shares = shares.map {
                    BackupShare(
                        name = nameOf[it.personId],
                        amount = it.shareAmount,
                        percentage = it.percentage,
                    )
                },
            )
        }

        LocalBackup(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            people = local.map {
                BackupPerson(
                    name = it.name,
                    avatarColor = it.avatarColor,
                    avatarEmoji = it.avatarEmoji,
                    createdAt = it.createdAt,
                )
            },
            groups = groups.map { group ->
                BackupGroup(
                    name = group.name,
                    emoji = group.emoji,
                    description = group.description,
                    colorHex = group.colorHex,
                    createdAt = group.createdAt,
                    archivedAt = group.archivedAt,
                    memberNames = members[group.id].orEmpty().mapNotNull { nameOf[it.personId] },
                )
            },
            subscriptions = db.subscriptionDao().getAllDirect().map {
                BackupSubscription(
                    name = it.name,
                    amount = it.amount,
                    category = it.category,
                    paymentMethod = it.paymentMethod,
                    cycle = it.cycle,
                    startDate = it.startDate,
                    chargedThrough = it.chargedThrough,
                    cancelledAt = it.cancelledAt,
                    createdAt = it.createdAt,
                )
            },
            settlements = settlements.map {
                BackupSettlement(
                    fromName = nameOf[it.fromPersonId],
                    toName = nameOf[it.toPersonId],
                    amount = it.amount,
                    date = it.date,
                    note = it.note,
                    isIou = it.isIou,
                    createdAt = it.createdAt,
                )
            },
        )
    }

    /**
     * Puts back whatever is missing, and touches nothing that is already there.
     *
     * Additive on purpose. A restore that replaced what it found could wipe a ledger someone has
     * been using since the backup was taken, and the common case — restoring onto a phone that has
     * already synced — is exactly that. Running it twice is a no-op, and [RestoreReport] says so
     * rather than reporting a silent success.
     */
    suspend fun restore(backup: LocalBackup): RestoreReport = withContext(Dispatchers.IO) {
        var report = RestoreReport()

        db.withTransaction {
            // People first: everything else refers to them by name.
            val existing = db.personDao().getAllPeopleDirect().associateBy { it.name.trim().lowercase() }
            val idOf = existing.mapValues { it.value.id }.toMutableMap()

            for (person in backup.people) {
                val key = person.name.trim().lowercase()
                if (key in idOf) {
                    report = report.copy(peopleSkipped = report.peopleSkipped + 1)
                    continue
                }
                val id = db.personDao().insertPerson(
                    PersonEntity(
                        name = person.name,
                        avatarColor = person.avatarColor,
                        avatarEmoji = person.avatarEmoji,
                        createdAt = person.createdAt,
                    )
                )
                idOf[key] = id
                report = report.copy(peopleAdded = report.peopleAdded + 1)
            }

            fun personId(name: String?): Long? =
                if (name == null) me else idOf[name.trim().lowercase()]

            val groupKeys = db.groupDao().getAllGroupsDirect()
                .map { it.name.trim().lowercase() to it.createdAt }.toMutableSet()
            for (group in backup.groups) {
                val key = group.name.trim().lowercase() to group.createdAt
                if (key in groupKeys) {
                    report = report.copy(groupsSkipped = report.groupsSkipped + 1)
                    continue
                }
                val groupId = db.groupDao().insertGroup(
                    GroupEntity(
                        name = group.name,
                        description = group.description,
                        emoji = group.emoji,
                        colorHex = group.colorHex,
                        createdAt = group.createdAt,
                        syncState = SyncState.LOCAL_ONLY,
                        archivedAt = group.archivedAt,
                    )
                )
                val memberIds = listOf(me) + group.memberNames.mapNotNull { personId(it) }
                db.groupSyncDao().insertMembers(memberIds.distinct().map { GroupMemberEntity(groupId = groupId, personId = it) })
                groupKeys += key
                report = report.copy(groupsAdded = report.groupsAdded + 1)
            }

            val subKeys = db.subscriptionDao().getAllDirect()
                .map { Triple(it.name.trim().lowercase(), it.amount, it.startDate) }.toMutableSet()
            for (sub in backup.subscriptions) {
                val key = Triple(sub.name.trim().lowercase(), sub.amount, sub.startDate)
                if (key in subKeys) {
                    report = report.copy(subscriptionsSkipped = report.subscriptionsSkipped + 1)
                    continue
                }
                db.subscriptionDao().insert(
                    SubscriptionEntity(
                        name = sub.name,
                        amount = sub.amount,
                        category = sub.category,
                        paymentMethod = sub.paymentMethod,
                        cycle = sub.cycle,
                        startDate = sub.startDate,
                        chargedThrough = sub.chargedThrough,
                        cancelledAt = sub.cancelledAt,
                        createdAt = sub.createdAt,
                    )
                )
                subKeys += key
                report = report.copy(subscriptionsAdded = report.subscriptionsAdded + 1)
            }

            val settlementKeys = db.settlementDao().getAllSettlementsDirect()
                .map { Triple(it.fromPersonId, it.toPersonId, it.amount to it.date) }.toMutableSet()
            for (settlement in backup.settlements) {
                val from = personId(settlement.fromName)
                val to = personId(settlement.toName)
                if (from == null || to == null) {
                    report = report.copy(settlementsSkipped = report.settlementsSkipped + 1)
                    continue
                }
                val key = Triple(from, to, settlement.amount to settlement.date)
                if (key in settlementKeys) {
                    report = report.copy(settlementsSkipped = report.settlementsSkipped + 1)
                    continue
                }
                db.groupSyncDao().insertSettlement(
                    SettlementEntity(
                        fromPersonId = from,
                        toPersonId = to,
                        amount = settlement.amount,
                        date = settlement.date,
                        note = settlement.note,
                        isIou = settlement.isIou,
                        createdAt = settlement.createdAt,
                    )
                )
                settlementKeys += key
                report = report.copy(settlementsAdded = report.settlementsAdded + 1)
            }

            report = report.copy(
                splitsAdded = 0,
                splitsSkipped = 0,
            )
            val transactions = db.transactionDao().getAllTransactionsDirect()
            val byRemoteId = transactions.filter { it.remoteId.isNotBlank() }.associateBy { it.remoteId }
            for (split in backup.splits) {
                // The expense may already be back from the server. Prefer that copy — adding a
                // second one would double the month's spending, which is worse than losing a split.
                val existingTransaction = split.transactionRemoteId?.let { byRemoteId[it] }
                    ?: transactions.firstOrNull {
                        it.date == split.date && it.merchant == split.merchant && it.amount == split.totalAmount
                    }
                val transactionId = when {
                    existingTransaction != null -> {
                        if (db.splitDao().getSplitByTransactionIdDirect(existingTransaction.id) != null) {
                            report = report.copy(splitsSkipped = report.splitsSkipped + 1)
                            continue
                        }
                        existingTransaction.id
                    }
                    else -> db.transactionDao().insertTransaction(
                        TransactionEntity(
                            type = "expense",
                            amount = split.totalAmount,
                            category = split.category,
                            merchant = split.merchant,
                            paymentMethod = split.paymentMethod,
                            date = split.date,
                            note = split.note,
                        )
                    )
                }
                val splitId = db.splitDao().insertSplit(
                    SplitEntity(
                        transactionId = transactionId,
                        totalAmount = split.totalAmount,
                        paidByPersonId = me,
                        splitType = split.splitType,
                    )
                )
                val shares = split.shares.mapNotNull { share ->
                    personId(share.name)?.let {
                        SplitShareEntity(splitId = splitId, personId = it, shareAmount = share.amount, percentage = share.percentage)
                    }
                }
                db.groupSyncDao().insertShares(shares)
                report = report.copy(splitsAdded = report.splitsAdded + 1)
            }
        }
        report
    }
}
