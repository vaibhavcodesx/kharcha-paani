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
import com.vaibhav.kharchapaani.domain.BackupSettings
import com.vaibhav.kharchapaani.domain.BackupSettlement
import com.vaibhav.kharchapaani.domain.BackupShare
import com.vaibhav.kharchapaani.domain.BackupSplit
import com.vaibhav.kharchapaani.domain.BackupSubscription
import com.vaibhav.kharchapaani.domain.BackupTransaction
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.LocalBackup
import com.vaibhav.kharchapaani.domain.RestoreReport
import com.vaibhav.kharchapaani.domain.roundPaisa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reading and writing everything the server will never hold.
 *
 * See [LocalBackup] for what that includes and why the rule is "would the sync engine upload it"
 * rather than "does a friend without an account appear in it" — the second is what the first
 * version of this class asked, and it left real financial history out of the file.
 */
class LocalBackupRepository(private val db: AppDatabase) {

    /** What the ledger calls "you". Never a row in `people`. */
    private val me = -1L

    suspend fun collect(appVersion: String, settings: BackupSettings = BackupSettings()): LocalBackup =
        withContext(Dispatchers.IO) {
            val people = db.personDao().getAllPeopleDirect()
            val localPeople = people.filter { it.profileId == null }
            // Every person, not just local ones: a split with a registered groupmate outside a
            // shared group is just as unsyncable, and its shares still have to name someone.
            val nameOf = people.associate { it.id to it.name }
            // Null in the file means "you", so only -1 may be written as null. Someone deleted from
            // this phone has no name to write, and writing null for them turned a split they paid
            // into one I paid (correctness audit, 2026-10-24).
            fun known(personId: Long) = personId == me || personId in nameOf
            fun nameFor(personId: Long): String? = if (personId == me) null else nameOf.getValue(personId)

            val allGroups = db.groupDao().getAllGroupsDirect()
            val groupById = allGroups.associateBy { it.id }
            val localGroups = allGroups.filter { !it.isShared }
            val members = db.groupDao().getAllGroupMembersDirect().groupBy { it.groupId }

            // Everything outside a shared group, including the ones with friends who have no account
            // that sync privately since 1.3.0: kept anyway, and recognised on restore (see LocalBackup).
            // One with someone no longer here moves no balance the app shows, and has no one to name.
            val settlements = db.settlementDao().getAllSettlementsDirect()
                .filter { settlement -> settlement.groupId?.let { groupById[it]?.isShared } != true }
                .filter { known(it.fromPersonId) && known(it.toPersonId) }

            val splitByTransaction = db.splitDao().getAllSplitsDirect().associateBy { it.transactionId }
            val transactions = db.transactionDao().getAllTransactionsDirect().filter { transaction ->
                LocalBackup.isUnsyncable(
                    isSplit = transaction.isSplit || splitByTransaction.containsKey(transaction.id),
                    inSharedGroup = transaction.groupId?.let { groupById[it]?.isShared ?: false },
                    localOnly = transaction.syncState == SyncState.LOCAL_ONLY,
                )
            }

            LocalBackup(
                exportedAt = System.currentTimeMillis(),
                appVersion = appVersion,
                people = localPeople.map {
                    BackupPerson(
                        name = it.name,
                        avatarColor = it.avatarColor,
                        avatarEmoji = it.avatarEmoji,
                        createdAt = it.createdAt,
                    )
                },
                groups = localGroups.map { group ->
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
                        fromName = nameFor(it.fromPersonId),
                        toName = nameFor(it.toPersonId),
                        amount = it.amount,
                        date = it.date,
                        note = it.note,
                        isIou = it.isIou,
                        groupName = it.groupId?.let { id -> groupById[id]?.name },
                        createdAt = it.createdAt,
                    )
                },
                transactions = transactions.mapNotNull { transaction ->
                    val split = splitByTransaction[transaction.id]
                    val shares = split?.let { db.splitDao().getSharesForSplitDirect(it.id) }.orEmpty()
                    // Paid by a friend since deleted: what's left of it for me is my share, which is
                    // what the app counts as my spending. Nobody here owes anybody here for it.
                    val payerGone = split != null && !known(split.paidByPersonId)
                    val amount = if (payerGone) roundPaisa(shares.filter { it.personId == me }.sumOf { it.shareAmount }) else transaction.amount
                    if (payerGone && amount <= 0) return@mapNotNull null
                    BackupTransaction(
                        remoteId = transaction.remoteId.takeIf { it.isNotBlank() },
                        type = transaction.type,
                        amount = amount,
                        category = transaction.category,
                        merchant = transaction.merchant,
                        paymentMethod = transaction.paymentMethod,
                        date = transaction.date,
                        note = transaction.note,
                        groupName = transaction.groupId?.let { groupById[it]?.name },
                        createdAt = transaction.createdAt,
                        split = split?.takeUnless { payerGone }?.let {
                            BackupSplit(
                                totalAmount = it.totalAmount,
                                splitType = it.splitType,
                                paidByName = nameFor(it.paidByPersonId),
                                // A share of someone no longer here isn't anyone's to restore.
                                shares = shares.filter { share -> known(share.personId) }.map { share ->
                                    BackupShare(
                                        name = nameFor(share.personId),
                                        amount = share.shareAmount,
                                        percentage = share.percentage,
                                    )
                                },
                            )
                        },
                    )
                },
                settings = settings,
            )
        }

    /**
     * Puts back whatever is missing and touches nothing already there.
     *
     * Additive on purpose. The common case is restoring onto a phone that has already synced, so a
     * restore that replaced what it found could wipe a ledger in use since the backup was taken.
     * Running it twice is a no-op, and [RestoreReport] says what it skipped rather than reporting a
     * success that leaves you unsure whether it duplicated everything.
     */
    suspend fun restore(backup: LocalBackup): RestoreReport = withContext(Dispatchers.IO) {
        var report = RestoreReport()

        db.withTransaction {
            // People first: everything else refers to them by name.
            val idOf = db.personDao().getAllPeopleDirect()
                .associate { it.name.trim().lowercase() to it.id }.toMutableMap()

            for (person in backup.people) {
                val key = person.name.trim().lowercase()
                if (key in idOf) {
                    report = report.copy(peopleSkipped = report.peopleSkipped + 1)
                    continue
                }
                idOf[key] = db.personDao().insertPerson(
                    PersonEntity(
                        name = person.name,
                        avatarColor = person.avatarColor,
                        avatarEmoji = person.avatarEmoji,
                        createdAt = person.createdAt,
                    )
                )
                report = report.copy(peopleAdded = report.peopleAdded + 1)
            }

            /** Null name is you; an unknown name means the row cannot be placed. */
            fun personId(name: String?): Long? = if (name == null) me else idOf[name.trim().lowercase()]

            val groupsHere = db.groupDao().getAllGroupsDirect()
            val groupIdOf = groupsHere.associate { it.name.trim().lowercase() to it.id }.toMutableMap()
            // Backups made before 2026-10-24 carry shared groups' expenses; the group's own copy on the
            // server is the one that counts (see LocalBackup.isUnsyncable).
            val sharedGroupNames = groupsHere.filter { it.isShared }.map { it.name.trim().lowercase() }.toSet()
            for (group in backup.groups) {
                val key = group.name.trim().lowercase()
                if (key in groupIdOf) {
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
                db.groupSyncDao().insertMembers(
                    memberIds.distinct().map { GroupMemberEntity(groupId = groupId, personId = it) }
                )
                groupIdOf[key] = groupId
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

            // Transactions before settlements: neither depends on the other, but doing the bigger
            // one first means a failure part-way leaves the smaller set obviously absent.
            val existing = db.transactionDao().getAllTransactionsDirect()
            val byRemoteId = existing.filter { it.remoteId.isNotBlank() }.associateBy { it.remoteId }
            val byShape = existing.map { Triple(it.date, it.merchant, it.amount) }.toMutableSet()

            for (inFile in backup.transactions) {
                val alreadyHere = inFile.remoteId?.let { byRemoteId[it] }
                val shape = Triple(inFile.date, inFile.merchant, inFile.amount)
                val sharedGroups = inFile.groupName?.trim()?.lowercase() in sharedGroupNames
                // A payer named but not known here (a groupmate this phone hasn't synced yet) is not
                // "you": falling back to me flipped who owes whom. Keep my share as my own spending,
                // the way deleting a friend leaves it (LedgerRepository.deletePerson).
                val payerUnknown = inFile.split?.paidByName?.let { personId(it) == null } == true
                val backedUp = if (payerUnknown) {
                    inFile.copy(amount = roundPaisa(inFile.split!!.shares.filter { it.name == null }.sumOf { it.amount }), split = null)
                } else inFile
                if (alreadyHere != null || shape in byShape || sharedGroups || backedUp.amount <= 0) {
                    report = report.copy(transactionsSkipped = report.transactionsSkipped + 1)
                    continue
                }
                val transactionId = db.transactionDao().insertTransaction(
                    TransactionEntity(
                        type = backedUp.type,
                        amount = backedUp.amount,
                        category = backedUp.category,
                        merchant = backedUp.merchant,
                        paymentMethod = backedUp.paymentMethod,
                        date = backedUp.date,
                        note = backedUp.note,
                        isSplit = backedUp.split != null,
                        groupId = backedUp.groupName?.let { groupIdOf[it.trim().lowercase()] },
                        createdAt = backedUp.createdAt,
                        remoteId = backedUp.remoteId ?: java.util.UUID.randomUUID().toString(),
                        syncState = SyncState.LOCAL_ONLY,
                    )
                )
                byShape += shape
                report = report.copy(transactionsAdded = report.transactionsAdded + 1)

                val split = backedUp.split ?: continue
                val splitId = db.splitDao().insertSplit(
                    SplitEntity(
                        transactionId = transactionId,
                        totalAmount = split.totalAmount,
                        // The field whose loss would flip who owes whom, silently. Never null here:
                        // an unknown payer was turned into my own expense above.
                        paidByPersonId = personId(split.paidByName)!!,
                        splitType = split.splitType,
                    )
                )
                db.groupSyncDao().insertShares(
                    split.shares.mapNotNull { share ->
                        personId(share.name)?.let {
                            SplitShareEntity(
                                splitId = splitId,
                                personId = it,
                                shareAmount = share.amount,
                                percentage = share.percentage,
                            )
                        }
                    }
                )
            }

            // Amounts compared to the paisa: one pulled from the cloud was rounded on the way up (the
            // server's numeric(12, 2)), while a backup taken before it synced keeps whatever was
            // typed, e.g. 100.555.
            val settlementKeys = db.settlementDao().getAllSettlementsDirect()
                .map { listOf(it.fromPersonId, it.toPersonId, roundPaisa(it.amount), it.date) }.toMutableSet()
            for (settlement in backup.settlements) {
                val from = personId(settlement.fromName)
                val to = personId(settlement.toName)
                if (from == null || to == null) {
                    report = report.copy(settlementsSkipped = report.settlementsSkipped + 1)
                    continue
                }
                val key = listOf(from, to, roundPaisa(settlement.amount), settlement.date)
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
                        groupId = settlement.groupName?.let { groupIdOf[it.trim().lowercase()] },
                        createdAt = settlement.createdAt,
                        syncState = SyncState.LOCAL_ONLY,
                    )
                )
                settlementKeys += key
                report = report.copy(settlementsAdded = report.settlementsAdded + 1)
            }
        }
        report
    }
}

/** The DataStore half, which the repository is handed rather than reaching for. */
fun CategoryPrefs.toBackup(askBeforeDiscarding: Boolean): BackupSettings = BackupSettings(
    customCategories = customExpense,
    hiddenCategories = hiddenCategories.toList(),
    customIncomeCategories = customIncome,
    customPaymentMethods = customPaymentMethods,
    hiddenPaymentMethods = hiddenPaymentMethods.toList(),
    askBeforeDiscarding = askBeforeDiscarding,
)
