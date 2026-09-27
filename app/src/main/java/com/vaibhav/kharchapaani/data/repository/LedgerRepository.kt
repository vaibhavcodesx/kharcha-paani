package com.vaibhav.kharchapaani.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PendingRemoteDeleteEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.data.sync.LocalFriendSync
import com.vaibhav.kharchapaani.data.sync.localSplitTombstone
import com.vaibhav.kharchapaani.domain.CsvExport
import com.vaibhav.kharchapaani.domain.CsvFile
import com.vaibhav.kharchapaani.domain.ExportRange
import com.vaibhav.kharchapaani.domain.GroupBalances
import com.vaibhav.kharchapaani.domain.coveredBy
import com.vaibhav.kharchapaani.domain.ownShareOf
import com.vaibhav.kharchapaani.domain.owedAsGuestBy
import com.vaibhav.kharchapaani.domain.MonthAnalytics
import com.vaibhav.kharchapaani.domain.PersonColour
import com.vaibhav.kharchapaani.domain.PersonColours
import com.vaibhav.kharchapaani.domain.SplitRounding
import com.vaibhav.kharchapaani.domain.SubscriptionSchedule
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import com.vaibhav.kharchapaani.domain.roundPaisa
import java.util.Locale
import java.util.UUID
import java.time.LocalDate
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * @param onLocalTransactionChange called after a syncable write commits, so the account's sync
 * can be scheduled. Runs after the Room transaction, never inside it.
 * @param selfProfileId the signed-in account's profile id; needed to create or edit shared groups.
 */
class LedgerRepository(
    private val db: AppDatabase,
    private val onLocalTransactionChange: () -> Unit = {},
    private val selfProfileId: String? = null,
    /** See [LocalFriendSync]: off until the private-ledger migration is applied. */
    private val localFriendSync: Boolean = LocalFriendSync.ENABLED,
) {

    val allPeople: Flow<List<PersonEntity>> = db.personDao().getAllPeople()
    val allTransactions: Flow<List<TransactionEntity>> = db.transactionDao().getAllTransactions()
    val allSplits: Flow<List<SplitEntity>> = db.splitDao().getAllSplits()
    val allSplitShares: Flow<List<SplitShareEntity>> = db.splitDao().getAllSplitShares()
    val allSettlements: Flow<List<SettlementEntity>> = db.settlementDao().getAllSettlements()
    val allGroups: Flow<List<GroupEntity>> = db.groupDao().getAllGroups()
    val allGroupMembers: Flow<List<GroupMemberEntity>> = db.groupDao().getAllGroupMembers()

    val fullTransactions: Flow<List<FullTransaction>> = combine(
        allTransactions,
        allSplits,
        allSplitShares
    ) { transactions, splits, shares ->
        val splitMap = splits.associateBy { it.transactionId }
        val shareMap = shares.groupBy { it.splitId }

        transactions.map { tx ->
            val split = splitMap[tx.id]
            val splitShares = split?.let { shareMap[it.id] } ?: emptyList()
            FullTransaction(tx, split, splitShares)
        }
    }.flowOn(Dispatchers.IO)

    val personBalances: Flow<List<PersonBalance>> = combine(
        allPeople,
        allSplits,
        allSplitShares,
        allSettlements
    ) { people, splits, shares, settlements ->
        val splitMap = splits.associateBy { it.id }
        val sharesBySplit = shares.groupBy { it.splitId }

        people.map { person ->
            var theyOweMe = 0.0
            var iOweThem = 0.0

            // 1. Calculate from splits
            for (split in splits) {
                val splitShares = sharesBySplit[split.id] ?: emptyList()
                if (split.paidByPersonId == -1L) {
                    // I paid for the split -> everyone else owes me their share (and their guests')
                    theyOweMe += splitShares.coveredBy(person.id) ?: 0.0
                } else if (split.paidByPersonId == person.id) {
                    // Person paid for the split -> I owe them my share, and my guests'
                    iOweThem += splitShares.coveredBy(-1L) ?: 0.0
                }
                // A friend of mine who came along as my guest owes me their share, whoever paid.
                theyOweMe += splitShares.owedAsGuestBy(person.id)
            }

            // 2. Calculate from settlements / IOUs. Only confirmed ones: a pending one is a claim
            // the other person hasn't agreed to yet (design item 10).
            for (settlement in settlements.filter { it.isConfirmed }) {
                if (settlement.fromPersonId == -1L && settlement.toPersonId == person.id) {
                    // I paid money to person
                    theyOweMe += settlement.amount
                } else if (settlement.fromPersonId == person.id && settlement.toPersonId == -1L) {
                    // Person paid money to me
                    iOweThem += settlement.amount
                }
            }

            val net = theyOweMe - iOweThem
            PersonBalance(
                person = person,
                netBalance = roundPaisa(net),
                totalTheyOweMe = roundPaisa(theyOweMe),
                totalIOweThem = roundPaisa(iOweThem)
            )
        }
    }.flowOn(Dispatchers.IO)

    val groupsWithDetails: Flow<List<GroupWithDetails>> = combine(
        allGroups,
        allGroupMembers,
        allPeople,
        fullTransactions,
        allSettlements
    ) { groups, groupMembers, people, transactions, settlements ->
        val membersByGroup = groupMembers.groupBy { it.groupId }
        val peopleMap = people.associateBy { it.id }

        groups.map { group ->
            val memberEntities = membersByGroup[group.id]?.mapNotNull { gm ->
                if (gm.personId == -1L) null else peopleMap[gm.personId]
            } ?: emptyList()

            val groupTxs = transactions.filter { it.transaction.groupId == group.id }

            var totalExpenses = 0.0
            var myPaidAmount = 0.0
            var myShare = 0.0
            var othersOweMeInGroup = 0.0
            var iOweOthersInGroup = 0.0

            for (tx in groupTxs) {
                totalExpenses += tx.transaction.amount
                val split = tx.split
                val shares = tx.shares

                if (split != null) {
                    if (split.paidByPersonId == -1L) {
                        // I paid
                        myPaidAmount += tx.transaction.amount
                        // "Your share" is what I consumed; guests I cover are mine to collect, not
                        // the group's to owe me (design item 9).
                        myShare += shares.ownShareOf(-1L) ?: 0.0
                        othersOweMeInGroup += (tx.transaction.amount - (shares.coveredBy(-1L) ?: 0.0))
                    } else {
                        // Someone else paid: I owe them my share and my guests'
                        myShare += shares.ownShareOf(-1L) ?: 0.0
                        iOweOthersInGroup += shares.coveredBy(-1L) ?: 0.0
                    }
                } else {
                    myPaidAmount += tx.transaction.amount
                    myShare += tx.transaction.amount
                }
            }

            val netBalance = if (group.isShared) {
                // Groupmates' expenses and settlements move my position too, so use the group-wide view.
                GroupBalances.netPositions(groupTxs, settlements.filter { it.groupId == group.id })[-1L] ?: 0.0
            } else {
                othersOweMeInGroup - iOweOthersInGroup
            }

            GroupWithDetails(
                group = group,
                members = memberEntities,
                totalExpenses = roundPaisa(totalExpenses),
                myPaidAmount = roundPaisa(myPaidAmount),
                myShare = roundPaisa(myShare),
                netBalanceForMe = roundPaisa(netBalance),
                transactionsCount = groupTxs.size,
                transactions = groupTxs,
                settlements = settlements.filter { it.groupId == group.id },
                unsyncedChanges = if (!group.isShared) 0 else {
                    val unsynced = setOf(SyncState.PENDING, SyncState.FAILED)
                    groupTxs.count { it.transaction.syncState in unsynced } +
                        settlements.count { it.groupId == group.id && it.syncState in unsynced } +
                        (if (group.syncState in unsynced) 1 else 0)
                },
            )
        }
    }.flowOn(Dispatchers.IO)

    fun getLedgerForPerson(personId: Long): Flow<List<PersonLedgerItem>> = combine(
        allTransactions,
        allSplits,
        allSplitShares,
        allSettlements
    ) { transactions, splits, shares, settlements ->
        val items = mutableListOf<PersonLedgerItem>()
        val txMap = transactions.associateBy { it.id }
        val splitMap = splits.associateBy { it.id }
        val sharesBySplit = shares.groupBy { it.splitId }

        // Splits involving this person
        for (split in splits) {
            val tx = txMap[split.transactionId] ?: continue
            val splitShares = sharesBySplit[split.id] ?: emptyList()

            if (split.paidByPersonId == -1L) {
                // I paid
                val personShare = splitShares.coveredBy(personId)
                if (personShare != null) {
                    items.add(
                        PersonLedgerItem(
                            id = "split_${split.id}",
                            date = tx.date,
                            title = tx.merchant.ifBlank { tx.category },
                            // "You paid ₹150 (Their share)" read as their share being ₹150, and %.0f turned ₹150.50
                            // into ₹151. Both figures, said separately, to the paisa.
                            subtitle = "You paid ${Money.format(split.totalAmount)} · their share ${Money.format(personShare)}",
                            amount = personShare,
                            netChange = personShare, // they owe me more (+)
                            isSettlement = false
                        )
                    )
                }
            } else if (split.paidByPersonId == personId) {
                // Person paid
                val myShare = splitShares.coveredBy(-1L)
                if (myShare != null) {
                    items.add(
                        PersonLedgerItem(
                            id = "split_${split.id}",
                            date = tx.date,
                            title = tx.merchant.ifBlank { tx.category },
                            subtitle = "They paid ${Money.format(split.totalAmount)} · your share ${Money.format(myShare)}",
                            amount = myShare,
                            netChange = -myShare, // I owe them more (-)
                            isSettlement = false
                        )
                    )
                }
            }
            // This friend came along as my guest on a group expense: they owe me their share.
            val asGuest = splitShares.owedAsGuestBy(personId)
            if (asGuest > 0.0) {
                items.add(
                    PersonLedgerItem(
                        id = "guest_${split.id}",
                        date = tx.date,
                        title = tx.merchant.ifBlank { tx.category },
                        subtitle = "Your guest in a group · their share ${Money.format(asGuest)}",
                        amount = asGuest,
                        netChange = asGuest,
                        isSettlement = false
                    )
                )
            }
        }

        // Settlements / IOUs involving this person
        for (settlement in settlements) {
            if (settlement.fromPersonId == -1L && settlement.toPersonId == personId) {
                // I paid person
                items.add(
                    PersonLedgerItem(
                        id = "settlement_${settlement.id}",
                        date = settlement.date,
                        // Worded the way the group feed words it (GroupFeed.describe), from your side.
                        title = if (settlement.isIou) "You lent them" else "You paid them",
                        subtitle = settlementSubtitle(settlement),
                        amount = settlement.amount,
                        netChange = if (settlement.isConfirmed) settlement.amount else 0.0,
                        isSettlement = true
                    )
                )
            } else if (settlement.fromPersonId == personId && settlement.toPersonId == -1L) {
                // Person paid me
                items.add(
                    PersonLedgerItem(
                        id = "settlement_${settlement.id}",
                        date = settlement.date,
                        title = if (settlement.isIou) "They lent you" else "They paid you",
                        subtitle = settlementSubtitle(settlement),
                        amount = settlement.amount,
                        netChange = if (settlement.isConfirmed) -settlement.amount else 0.0,
                        isSettlement = true
                    )
                )
            }
        }

        items.sortedByDescending { it.date }
    }.flowOn(Dispatchers.IO)

    /** A settlement's line in a friend's history, saying when it hasn't counted yet. */
    private fun settlementSubtitle(settlement: SettlementEntity): String {
        val base = settlement.note.ifBlank { if (settlement.isIou) "IOU" else "Settled up" }
        return when (settlement.status) {
            SettlementStatus.PENDING -> "$base · waiting to be confirmed"
            SettlementStatus.REJECTED -> "$base · marked not right"
            SettlementStatus.CONFIRMED -> base
        }
    }

    /** This month, for Home and the widget. */
    val monthlyAnalytics: Flow<MonthlyAnalytics> = analyticsFor { YearMonth.now(it) }

    /** Any month, for Insights' month picker. */
    fun monthlyAnalyticsFor(month: YearMonth): Flow<MonthlyAnalytics> = analyticsFor { month }

    private fun analyticsFor(month: (ZoneId) -> YearMonth): Flow<MonthlyAnalytics> =
        combine(allTransactions, allSplits, allSplitShares) { transactions, splits, shares ->
            val zone = ZoneId.systemDefault()
            MonthAnalytics.of(transactions, splits, shares, month(zone), LocalDate.now(zone), zone)
        }.flowOn(Dispatchers.IO)

    suspend fun saveTransactionWithSplits(
        tx: TransactionEntity,
        paidByPersonId: Long = -1L,
        shares: List<SplitShareEntity> = emptyList(),
        splitType: String = "equal"
    ): Long = withContext(Dispatchers.IO) {
        val isSplit = shares.isNotEmpty()
        val txId = db.withTransaction {
            requireWritableGroup(tx.groupId)
            val syncState = syncStateFor(groupId = tx.groupId, isSplit = isSplit, participants = shares.map { it.personId } + paidByPersonId)
            val txId = db.transactionDao().insertTransaction(
                tx.copy(
                    isSplit = isSplit,
                    updatedAt = System.currentTimeMillis(),
                    syncState = syncState,
                    ownerProfileId = null,
                )
            )
            if (isSplit) insertSplit(txId, tx.amount, paidByPersonId, splitType, sharesFor(syncState, tx.groupId, tx.amount, paidByPersonId, shares))
            txId
        }
        onLocalTransactionChange()
        txId
    }

    suspend fun batchInsertTransactions(
        transactions: List<TransactionEntity>
    ): List<Long> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) return@withContext emptyList()
        val now = System.currentTimeMillis()
        val ids = db.withTransaction {
            db.transactionDao().insertTransactions(
                transactions.map {
                    it.copy(updatedAt = now, syncState = syncStateFor(it.groupId, it.isSplit), ownerProfileId = null)
                }
            )
        }
        onLocalTransactionChange()
        ids
    }

    suspend fun updateTransactionWithSplits(
        tx: TransactionEntity,
        paidByPersonId: Long = -1L,
        shares: List<SplitShareEntity> = emptyList(),
        splitType: String = "equal"
    ) = withContext(Dispatchers.IO) {
        val isSplit = shares.isNotEmpty()
        db.withTransaction {
            val existing = db.transactionDao().getTransactionByIdDirect(tx.id) ?: return@withTransaction
            val existingSplit = db.groupSyncDao().splitForTransaction(tx.id)
            // The adder or, for a group expense, the payer (item 11). The payer can't hand the payment
            // to someone else, unsplit it or move it out of its group; the server refuses those too.
            // The UI hides what isn't allowed; this keeps a stray call from queueing a refused edit.
            if (!FullTransaction(existing, existingSplit).canEdit) return@withTransaction
            if (!existing.isOwnedByMe && (paidByPersonId != -1L || shares.isEmpty() || tx.groupId != existing.groupId)) return@withTransaction
            requireWritableGroup(existing.groupId)
            requireWritableGroup(tx.groupId)
            val syncState = syncStateFor(
                groupId = tx.groupId,
                isSplit = isSplit,
                participants = shares.map { it.personId } + paidByPersonId,
                // Owner rule, 2026-09-23: history already on the phone never starts uploading by
                // itself, and editing an old entry is not asking for it to.
                keepLocal = existing.syncState == SyncState.LOCAL_ONLY,
            )
            // Moving an expense out of a shared group can't be expressed as an update: members of the
            // old group only pull that group's rows and would never see it leave. Retract the old copy
            // and give the row a new identity so the retraction and the new upload can't cancel out.
            val leavesSharedGroup = existing.syncState != SyncState.LOCAL_ONLY &&
                isSharedGroup(existing.groupId) && tx.groupId != existing.groupId
            val remoteId = if (leavesSharedGroup) UUID.randomUUID().toString() else existing.remoteId
            when {
                leavesSharedGroup -> db.syncDao().insertPendingDelete(PendingRemoteDeleteEntity(existing.remoteId))
                // A re-queued upload must not be undone by an older queued delete of the same row.
                syncState == SyncState.PENDING -> db.syncDao().clearPendingDeletes(listOf(existing.remoteId))
                // Became a split outside any shared group (local-only): take the cloud copy down.
                existing.syncState != SyncState.LOCAL_ONLY ->
                    db.syncDao().insertPendingDelete(PendingRemoteDeleteEntity(existing.remoteId))
            }
            // A split that was in the cloud privately and now isn't one there (unsplit, moved into a
            // group, or turned local): take the split down, or a restore would bring it back.
            val wasPrivateSplit = existing.isSplit && existing.groupId == null && existing.syncState != SyncState.LOCAL_ONLY
            val isPrivateSplit = isSplit && tx.groupId == null && syncState == SyncState.PENDING
            if (localFriendSync && wasPrivateSplit && !isPrivateSplit) queueSplitRetraction(existing.remoteId)
            db.transactionDao().updateTransaction(
                // The edit sheet builds a fresh entity; identity and creation time come from the stored row.
                tx.copy(
                    isSplit = isSplit,
                    remoteId = remoteId,
                    createdAt = existing.createdAt,
                    updatedAt = System.currentTimeMillis(),
                    syncState = syncState,
                    // A payer's edit leaves the expense with whoever added it.
                    ownerProfileId = existing.ownerProfileId,
                )
            )
            // Keep the split's server id across edits so save_group_expense updates the same split.
            val splitRemoteId = db.groupSyncDao().splitForTransaction(tx.id)?.remoteId?.takeUnless { leavesSharedGroup }
            db.splitDao().deleteSplitByTransactionId(tx.id)
            if (isSplit) {
                insertSplit(
                    tx.id, tx.amount, paidByPersonId, splitType,
                    sharesFor(syncState, tx.groupId, tx.amount, paidByPersonId, shares),
                    splitRemoteId,
                )
            }
        }
        onLocalTransactionChange()
    }

    suspend fun deleteTransaction(txId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val existing = db.transactionDao().getTransactionByIdDirect(txId) ?: return@withTransaction
            val split = db.groupSyncDao().splitForTransaction(txId)
            if (!FullTransaction(existing, split).canEdit) return@withTransaction
            requireWritableGroup(existing.groupId)
            if (existing.syncState != SyncState.LOCAL_ONLY) {
                db.syncDao().insertPendingDelete(PendingRemoteDeleteEntity(existing.remoteId))
                // A synced personal split lives in local_splits beside its expense. Retracting only
                // the expense left the split's row live on the server (harmless, since a restore
                // skips a split whose expense is gone, but still a leftover).
                if (existing.groupId == null && split != null) queueSplitRetraction(existing.remoteId)
            }
            db.transactionDao().deleteTransactionById(txId)
        }
        onLocalTransactionChange()
    }

    private suspend fun insertSplit(
        txId: Long,
        totalAmount: Double,
        paidByPersonId: Long,
        splitType: String,
        shares: List<SplitShareEntity>,
        remoteId: String? = null,
    ) {
        val split = SplitEntity(
            transactionId = txId,
            totalAmount = totalAmount,
            paidByPersonId = paidByPersonId,
            splitType = splitType
        )
        val splitId = db.splitDao().insertSplit(remoteId?.let { split.copy(remoteId = it) } ?: split)
        db.splitDao().insertSplitShares(shares.map { it.copy(splitId = splitId) })
    }

    suspend fun recordSettlement(
        fromPersonId: Long,
        toPersonId: Long,
        amount: Double,
        note: String = "",
        isIou: Boolean = false,
        groupId: Long? = null,
        date: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val shared = db.withTransaction {
            requireWritableGroup(groupId)
            isSharedGroup(groupId) ||
                // An IOU or settlement with a friend who has no account, outside any group.
                (groupId == null && fromPersonId != toPersonId && canSyncPrivately(listOf(fromPersonId, toPersonId)))
        }
        val settlement = SettlementEntity(
            fromPersonId = fromPersonId,
            toPersonId = toPersonId,
            amount = amount,
            date = date,
            note = note,
            isIou = isIou,
            groupId = groupId,
            // Decision 3: either party records it; inside a shared group it syncs to every member, and
            // outside one, with a friend who has no account, to this account's private table.
            syncState = if (shared) SyncState.PENDING else SyncState.LOCAL_ONLY,
            // Between two members it counts only once the other one confirms (design item 10). The
            // server stamps the same; this is so the phone shows it that way before the first sync.
            status = if (groupId != null && isSharedGroup(groupId)) SettlementStatus.PENDING else SettlementStatus.CONFIRMED,
            recordedByPersonId = if (groupId != null && isSharedGroup(groupId)) -1L else null,
        )
        val id = db.settlementDao().insertSettlement(settlement)
        if (shared) onLocalTransactionChange()
        id
    }

    suspend fun deleteSettlement(settlementId: Long) = withContext(Dispatchers.IO) {
        var queued = false
        db.withTransaction {
            val existing = db.groupSyncDao().settlementById(settlementId) ?: return@withTransaction
            requireWritableGroup(existing.groupId)
            if (existing.syncState != SyncState.LOCAL_ONLY && existing.groupId == null) {
                // Private: only ever this account's own record.
                db.syncDao().insertPendingDelete(
                    PendingRemoteDeleteEntity(existing.remoteId, entityType = PendingRemoteDeleteEntity.TYPE_LOCAL_SETTLEMENT)
                )
                queued = true
            } else if (existing.syncState != SyncState.LOCAL_ONLY) {
                // Only the two parties can retract a shared settlement (RLS); others see it read-only.
                if (existing.fromPersonId != -1L && existing.toPersonId != -1L) return@withTransaction
                // Pending or rejected: only whoever recorded it takes it away. The other party answers
                // instead, so a disagreement stays visible rather than vanishing (design item 10).
                if (!existing.isConfirmed && existing.recordedByPersonId != null && existing.recordedByPersonId != -1L) {
                    throw NotYoursToWithdrawException(existing)
                }
                db.syncDao().insertPendingDelete(
                    PendingRemoteDeleteEntity(existing.remoteId, entityType = PendingRemoteDeleteEntity.TYPE_SETTLEMENT)
                )
                queued = true
            }
            db.settlementDao().deleteSettlementById(settlementId)
        }
        if (queued) onLocalTransactionChange()
    }

    /**
     * My answer to a settlement a groupmate recorded: confirm it, or say it isn't right (with an
     * optional note). Takes effect here at once and is sent by the next sync.
     */
    suspend fun respondToSettlement(settlementId: Long, confirm: Boolean, note: String? = null) = withContext(Dispatchers.IO) {
        val changed = db.withTransaction {
            val existing = db.groupSyncDao().settlementById(settlementId) ?: return@withTransaction false
            if (existing.status != SettlementStatus.PENDING || existing.recordedByPersonId == -1L) return@withTransaction false
            requireWritableGroup(existing.groupId)
            val answer = if (confirm) SettlementStatus.CONFIRMED else SettlementStatus.REJECTED
            db.groupSyncDao().updateSettlement(
                existing.copy(
                    status = answer,
                    respondedAt = System.currentTimeMillis(),
                    responseNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200),
                    pendingResponse = answer,
                )
            )
            true
        }
        if (changed) onLocalTransactionChange()
    }

    suspend fun addPerson(name: String, avatarColor: String): Long = withContext(Dispatchers.IO) {
        db.personDao().insertPerson(PersonEntity(name = name.trim(), avatarColor = avatarColor))
    }

    /**
     * Separates any two people on this phone who ended up wearing the same colour.
     *
     * Colours were picked once, at insert, against nobody — so two people could hold the same one
     * for ever, which is what put three near-identical oranges in one group donut. Whoever had a
     * colour first keeps it; anyone duplicating it moves. See [PersonColours.respace].
     *
     * Runs at startup, writes only the rows that actually have to change, and does nothing at all
     * on a phone where nobody clashes — so it costs one read on every launch after the first.
     */
    suspend fun repairPersonColours() = withContext(Dispatchers.IO) {
        val people = db.personDao().getAllPeopleDirect()
        val changes = PersonColours.respace(
            people.map { PersonColour(id = it.id, key = it.profileId ?: it.name, colorHex = it.avatarColor, createdAt = it.createdAt) }
        )
        if (changes.isEmpty()) return@withContext
        db.withTransaction { changes.forEach { (id, hex) -> db.personDao().updatePersonColour(id, hex) } }
    }

    suspend fun deletePerson(personId: Long) = withContext(Dispatchers.IO) {
        // A groupmate comes from their group's membership; removing them locally would corrupt
        // shared expenses. They go away when they leave, or when this account does.
        if (db.personDao().getPersonById(personId)?.profileId != null) return@withContext
        db.withTransaction {
            val affected = db.splitDao().getSplitsInvolvingPerson(personId).mapNotNull { split ->
                val tx = db.transactionDao().getTransactionByIdDirect(split.transactionId) ?: return@mapNotNull null
                FullTransaction(tx, split, db.splitDao().getSharesForSplitDirect(split.id))
            }
            db.splitDao().deleteSharesForPerson(personId)
            db.settlementDao().deleteSettlementsForPerson(personId)
            db.groupDao().deleteMemberByPersonId(personId)
            db.personDao().deletePersonById(personId)
            affected.forEach {
                collapseSplitWithNobodyElse(it, deletedPersonId = personId)
                if (localFriendSync) resendPrivateSplit(it)
            }
        }
    }

    /**
     * After a friend is removed, a split that no longer involves anyone but me stops being a split,
     * so it doesn't keep showing as shared. What I spent stays what it was:
     * - I paid: the expense is mine in full (their share was cleared, as the delete dialog says).
     * - They paid: it becomes my own expense of the share I had.
     * - They paid and I had no share: nothing of it was mine, so it goes.
     * A split still paid by another friend stays: I still owe them.
     */
    private suspend fun collapseSplitWithNobodyElse(before: FullTransaction, deletedPersonId: Long) {
        val split = before.split ?: return
        val tx = before.transaction
        // Local friends appear in local records, and (with local-friend sync) in private splits; a
        // shared group's expense never reaches this path.
        if (tx.syncState != SyncState.LOCAL_ONLY && !(localFriendSync && tx.groupId == null)) return
        if (split.paidByPersonId != -1L && split.paidByPersonId != deletedPersonId) return
        if (db.splitDao().getSharesForSplitDirect(split.id).any { it.personId != -1L }) return

        val myShare = before.myPersonalShare
        db.splitDao().deleteSplitByTransactionId(tx.id)
        when {
            split.paidByPersonId == -1L ->
                db.transactionDao().updateTransaction(tx.copy(isSplit = false, updatedAt = System.currentTimeMillis()))
            myShare > 0 ->
                db.transactionDao().updateTransaction(tx.copy(isSplit = false, amount = roundPaisa(myShare), updatedAt = System.currentTimeMillis()))
            else -> db.transactionDao().deleteTransactionById(tx.id)
        }
    }

    /**
     * A private split changed under it when a friend was removed: tell the server. Gone entirely,
     * it's retracted; no longer a split, its split is taken down and the expense re-sent as plain;
     * still a split, it's re-sent as it now is (and fails visibly if what's left doesn't add up).
     */
    private suspend fun resendPrivateSplit(before: FullTransaction) {
        val old = before.transaction
        if (old.groupId != null || old.syncState == SyncState.LOCAL_ONLY) return
        val now = db.transactionDao().getTransactionByIdDirect(old.id)
        if (now == null) {
            db.syncDao().insertPendingDelete(PendingRemoteDeleteEntity(old.remoteId))
            return
        }
        if (!now.isSplit) queueSplitRetraction(old.remoteId)
        db.transactionDao().updateTransaction(now.copy(syncState = SyncState.PENDING, updatedAt = System.currentTimeMillis()))
        onLocalTransactionChange()
    }

    // Groups Operations
    suspend fun createGroup(
        name: String,
        description: String = "",
        emoji: String = "🏖️",
        colorHex: String = "#FF7A3D",
        memberPersonIds: List<Long> = emptyList()
    ): Long = withContext(Dispatchers.IO) {
        val groupId = db.groupDao().insertGroup(
            GroupEntity(
                name = name.trim(),
                description = description.trim(),
                emoji = emoji,
                colorHex = colorHex
            )
        )
        val members = mutableListOf<GroupMemberEntity>()
        // Always include 'Me' (-1L)
        members.add(GroupMemberEntity(groupId = groupId, personId = -1L))
        memberPersonIds.filter { it != -1L }.forEach { pid ->
            members.add(GroupMemberEntity(groupId = groupId, personId = pid))
        }
        db.groupDao().insertGroupMembers(members)
        groupId
    }

    /**
     * A shared group (decision 1) syncs to everyone who joins with an invite; membership comes only
     * from the server, so it starts with just this account.
     */
    suspend fun createSharedGroup(
        name: String,
        description: String = "",
        emoji: String = "🏖️",
        colorHex: String = "#FF7A3D",
    ): Long = withContext(Dispatchers.IO) {
        val self = checkNotNull(selfProfileId) { "creating a shared group needs a signed-in account" }
        val groupId = db.withTransaction {
            val groupId = db.groupDao().insertGroup(
                GroupEntity(
                    name = name.trim(),
                    description = description.trim(),
                    emoji = emoji,
                    colorHex = colorHex,
                    syncState = SyncState.PENDING,
                    createdByProfileId = self,
                )
            )
            db.groupDao().insertGroupMembers(listOf(GroupMemberEntity(groupId = groupId, personId = -1L)))
            groupId
        }
        onLocalTransactionChange()
        groupId
    }

    /** Decision 4: shared groups are archived (and may be un-archived) by their creator, never deleted. */
    suspend fun setGroupArchived(groupId: Long, archived: Boolean) = withContext(Dispatchers.IO) {
        val changed = db.withTransaction {
            val group = db.groupDao().getGroupByIdDirect(groupId) ?: return@withTransaction false
            if (!group.isShared || !isCreator(group) || group.isArchived == archived) return@withTransaction false
            db.groupDao().updateGroup(
                group.copy(
                    archivedAt = if (archived) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis(),
                    syncState = SyncState.PENDING,
                )
            )
            true
        }
        if (changed) onLocalTransactionChange()
    }

    suspend fun updateGroup(group: GroupEntity, memberPersonIds: List<Long>) = withContext(Dispatchers.IO) {
        val stored = db.groupDao().getGroupByIdDirect(group.id) ?: return@withContext
        if (stored.isShared) {
            updateSharedGroupDetails(stored, group)
            return@withContext
        }
        db.groupDao().updateGroup(group)
        db.groupDao().deleteMembersForGroup(group.id)
        val members = mutableListOf<GroupMemberEntity>()
        members.add(GroupMemberEntity(groupId = group.id, personId = -1L))
        memberPersonIds.filter { it != -1L }.forEach { pid ->
            members.add(GroupMemberEntity(groupId = group.id, personId = pid))
        }
        db.groupDao().insertGroupMembers(members)
    }

    /**
     * After this account leaves a shared group: the group's history belongs to the members who
     * remain, so it goes from this device with the membership. Anything still queued in it is
     * dropped; the server would refuse it from a non-member.
     */
    suspend fun removeSharedGroupLocally(groupId: Long) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val group = db.groupDao().getGroupByIdDirect(groupId) ?: return@withTransaction
            if (!group.isShared) return@withTransaction
            val dao = db.groupSyncDao()
            dao.deleteTransactionsInGroup(groupId)
            dao.deleteSettlementsInGroup(groupId)
            dao.deleteGroup(groupId)
        }
    }

    /** Name, description, emoji and colour only: membership changes through invites and leaving. */
    private suspend fun updateSharedGroupDetails(stored: GroupEntity, edited: GroupEntity) {
        if (!isCreator(stored)) return
        db.groupDao().updateGroup(
            stored.copy(
                name = edited.name.trim(),
                description = edited.description.trim(),
                emoji = edited.emoji,
                colorHex = edited.colorHex,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.PENDING,
            )
        )
        onLocalTransactionChange()
    }

    suspend fun deleteGroup(groupId: Long) = withContext(Dispatchers.IO) {
        // Shared history belongs to every member; shared groups are archived instead.
        if (db.groupDao().getGroupByIdDirect(groupId)?.isShared == true) return@withContext
        db.withTransaction {
            // The expenses and settlements stay, just no longer in a group that doesn't exist.
            db.groupDao().detachTransactionsFromGroup(groupId)
            db.groupDao().detachSettlementsFromGroup(groupId)
            db.groupDao().deleteMembersForGroup(groupId)
            db.groupDao().deleteGroupById(groupId)
        }
    }


    /**
     * Personal transactions and anything in a shared group sync. Splits with local-only friends and
     * expenses in local-only groups stay on this device (decision 7).
     */
    private suspend fun syncStateFor(
        groupId: Long?,
        isSplit: Boolean,
        participants: Collection<Long> = emptyList(),
        keepLocal: Boolean = false,
    ): SyncState = when {
        isSharedGroup(groupId) -> SyncState.PENDING
        groupId == null && !isSplit -> SyncState.PENDING
        // A personal split syncs to this account's private tables when everyone in it is a friend
        // with no account; anyone with one belongs in a shared group, which the save flow offers.
        groupId == null && !keepLocal && canSyncPrivately(participants) -> SyncState.PENDING
        else -> SyncState.LOCAL_ONLY
    }

    private suspend fun canSyncPrivately(personIds: Collection<Long>): Boolean =
        localFriendSync && personIds.all { id -> id == -1L || db.personDao().getPersonById(id)?.let { it.profileId == null } == true }

    /** Keyed apart from the expense's own tombstone, which a later edit clears on purpose. */
    private suspend fun queueSplitRetraction(transactionRemoteId: String) {
        db.syncDao().insertPendingDelete(
            PendingRemoteDeleteEntity(localSplitTombstone(transactionRemoteId), entityType = PendingRemoteDeleteEntity.TYPE_LOCAL_SPLIT)
        )
    }

    /**
     * Decision 4: an archived shared group is read-only. The server already refuses these writes
     * (RLS requires an active group); refusing here too means nothing is queued that can only fail.
     */
    private suspend fun requireWritableGroup(groupId: Long?) {
        val group = groupId?.let { db.groupDao().getGroupByIdDirect(it) } ?: return
        if (group.isShared && group.isArchived) throw ArchivedGroupException(group.name)
    }

    private suspend fun isSharedGroup(groupId: Long?): Boolean =
        groupId != null && db.groupDao().getGroupByIdDirect(groupId)?.isShared == true

    private fun isCreator(group: GroupEntity): Boolean =
        selfProfileId != null && group.createdByProfileId == selfProfileId

    /**
     * Shared-group shares are rounded to paisa with the leftover on the payer (decision 6), which the
     * server requires. Shares that don't add up are kept as entered: sync marks that expense FAILED
     * instead of this silently changing what someone owes.
     */
    private fun sharesFor(
        syncState: SyncState,
        groupId: Long?,
        amount: Double,
        paidByPersonId: Long,
        shares: List<SplitShareEntity>,
    ): List<SplitShareEntity> {
        // Shared-group and private splits both have to add up to the paisa for the server.
        if (syncState != SyncState.PENDING || shares.isEmpty()) return shares
        // One key per row, so a host's guests (rows under the host, design item 9) round too.
        val members = shares.filterNot { it.isGuest }
        if (members.map { it.personId }.toSet().size != members.size) return shares
        val keys = shares.mapIndexed { index, share -> if (share.isGuest) -1_000_000L - index else share.personId }
        val rounded = try {
            SplitRounding.normalize(amount, keys.zip(shares).associate { (key, share) -> key to share.shareAmount }, paidByPersonId)
        } catch (e: IllegalArgumentException) {
            return shares
        }
        return shares.mapIndexed { index, share -> share.copy(shareAmount = rounded.getValue(keys[index])) }
    }

    // ============================================================
    // Subscriptions (backlog item 8)
    // ============================================================

    val subscriptions: Flow<List<SubscriptionEntity>> = db.subscriptionDao().getAllSubscriptions()

    /**
     * Saves a subscription. [chargeFromStart] false starts it from the next due date, leaving the
     * charges since its start date out of the ledger; true (the default) creates them on the next run.
     */
    suspend fun saveSubscription(subscription: SubscriptionEntity, chargeFromStart: Boolean = true, today: LocalDate = LocalDate.now()): Long =
        withContext(Dispatchers.IO) {
            val chargedThrough = if (chargeFromStart) subscription.chargedThrough else localDateMillis(today)
            db.subscriptionDao().insert(subscription.copy(chargedThrough = chargedThrough))
        }

    /** Stops future charges; the ones already in the ledger stay, as they really happened. */
    suspend fun cancelSubscription(id: Long, at: LocalDate = LocalDate.now()) = withContext(Dispatchers.IO) {
        val existing = db.subscriptionDao().byId(id) ?: return@withContext
        db.subscriptionDao().update(existing.copy(cancelledAt = localDateMillis(at)))
    }

    /** A cancelled subscription can be removed once it's no longer wanted in the list. */
    suspend fun deleteSubscription(id: Long) = withContext(Dispatchers.IO) {
        db.subscriptionDao().delete(id)
    }

    /**
     * Creates the expense for every due date that has arrived and isn't in the ledger yet, and
     * remembers how far each subscription has been charged. Nothing is ever created for the future,
     * and running twice creates nothing twice. Returns how many entries were added.
     */
    suspend fun runDueSubscriptions(today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): Int =
        withContext(Dispatchers.IO) {
            var created = 0
            for (subscription in db.subscriptionDao().getRunnableDirect()) {
                val cycle = runCatching { SubscriptionCycle.valueOf(subscription.cycle) }.getOrNull() ?: continue
                val start = localDate(subscription.startDate, zone)
                val chargedThrough = subscription.chargedThrough?.let { localDate(it, zone) }
                val due = SubscriptionSchedule.dueDates(start, cycle, today, subscription.cancelledAt?.let { localDate(it, zone) })
                    .filter { chargedThrough == null || it.isAfter(chargedThrough) }
                if (due.isEmpty()) continue

                for (date in due) {
                    db.transactionDao().insertTransaction(
                        TransactionEntity(
                            type = "expense",
                            amount = subscription.amount,
                            category = subscription.category,
                            merchant = subscription.name,
                            paymentMethod = subscription.paymentMethod,
                            date = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
                            note = "Subscription",
                            syncState = syncStateFor(groupId = null, isSplit = false),
                        )
                    )
                    created++
                }
                db.subscriptionDao().update(subscription.copy(chargedThrough = localDateMillis(due.last(), zone)))
            }
            if (created > 0) onLocalTransactionChange()
            created
        }

    private fun localDate(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun localDateMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Everything in [range] as a spreadsheet; see [CsvExport]. */
    suspend fun exportCsv(range: ExportRange, zone: ZoneId = ZoneId.systemDefault(), groupId: Long? = null): CsvFile = withContext(Dispatchers.IO) {
        val splits = db.splitDao().getAllSplitsDirect().associateBy { it.transactionId }
        val shares = db.splitDao().getAllSplitSharesDirect().groupBy { it.splitId }
        val transactions = db.transactionDao().getAllTransactionsDirect().map { tx ->
            val split = splits[tx.id]
            FullTransaction(tx, split, split?.let { shares[it.id] }.orEmpty())
        }
        val group = groupId?.let { db.groupDao().getAllGroupsDirect().firstOrNull { g -> g.id == it } }
        CsvExport.build(
            transactions.filter { groupId == null || it.transaction.groupId == groupId },
            db.personDao().getAllPeopleDirect(),
            db.groupDao().getAllGroupsDirect(),
            range,
            zone,
            fileNameSuffix = group?.name,
            settlements = db.settlementDao().getAllSettlementsDirect()
                .filter { groupId == null || it.groupId == groupId },
        )
    }
}

/** A write into an archived shared group, which is read-only for everyone (decision 4). */
class ArchivedGroupException(val groupName: String) : IllegalStateException("$groupName is archived and read-only")

/** The other party recorded this settlement: it's theirs to withdraw, mine to answer. */
class NotYoursToWithdrawException(val settlement: SettlementEntity) : IllegalStateException("only whoever recorded this settlement can withdraw it")
