package com.vaibhav.kharchapaani.data.repository

import android.content.Context
import androidx.core.content.FileProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class LedgerRepository(private val db: AppDatabase) {

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
                    // I paid for the split -> everyone else owes me their share
                    val personShare = splitShares.find { it.personId == person.id }
                    if (personShare != null) {
                        theyOweMe += personShare.shareAmount
                    }
                } else if (split.paidByPersonId == person.id) {
                    // Person paid for the split -> I owe them my share
                    val myShare = splitShares.find { it.personId == -1L }
                    if (myShare != null) {
                        iOweThem += myShare.shareAmount
                    }
                }
            }

            // 2. Calculate from settlements / IOUs
            for (settlement in settlements) {
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
                netBalance = (net * 100).toLong() / 100.0,
                totalTheyOweMe = (theyOweMe * 100).toLong() / 100.0,
                totalIOweThem = (iOweThem * 100).toLong() / 100.0
            )
        }
    }.flowOn(Dispatchers.IO)

    val groupsWithDetails: Flow<List<GroupWithDetails>> = combine(
        allGroups,
        allGroupMembers,
        allPeople,
        fullTransactions
    ) { groups, groupMembers, people, transactions ->
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
                        val meShareAmount = shares.find { it.personId == -1L }?.shareAmount ?: 0.0
                        myShare += meShareAmount
                        othersOweMeInGroup += (tx.transaction.amount - meShareAmount)
                    } else {
                        // Someone else paid
                        val meShareAmount = shares.find { it.personId == -1L }?.shareAmount ?: 0.0
                        myShare += meShareAmount
                        iOweOthersInGroup += meShareAmount
                    }
                } else {
                    myPaidAmount += tx.transaction.amount
                    myShare += tx.transaction.amount
                }
            }

            val netBalance = (othersOweMeInGroup - iOweOthersInGroup)

            GroupWithDetails(
                group = group,
                members = memberEntities,
                totalExpenses = (totalExpenses * 100).toLong() / 100.0,
                myPaidAmount = (myPaidAmount * 100).toLong() / 100.0,
                myShare = (myShare * 100).toLong() / 100.0,
                netBalanceForMe = (netBalance * 100).toLong() / 100.0,
                transactionsCount = groupTxs.size,
                transactions = groupTxs
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
                val personShare = splitShares.find { it.personId == personId }
                if (personShare != null) {
                    items.add(
                        PersonLedgerItem(
                            id = "split_${split.id}",
                            date = tx.date,
                            title = tx.merchant.ifBlank { tx.category },
                            subtitle = "You paid ₹${String.format(Locale.US, "%.0f", split.totalAmount)} (Their share)",
                            amount = personShare.shareAmount,
                            netChange = personShare.shareAmount, // they owe me more (+)
                            isSettlement = false
                        )
                    )
                }
            } else if (split.paidByPersonId == personId) {
                // Person paid
                val myShare = splitShares.find { it.personId == -1L }
                if (myShare != null) {
                    items.add(
                        PersonLedgerItem(
                            id = "split_${split.id}",
                            date = tx.date,
                            title = tx.merchant.ifBlank { tx.category },
                            subtitle = "They paid ₹${String.format(Locale.US, "%.0f", split.totalAmount)} (Your share)",
                            amount = myShare.shareAmount,
                            netChange = -myShare.shareAmount, // I owe them more (-)
                            isSettlement = false
                        )
                    )
                }
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
                        title = if (settlement.isIou) "Direct Loan / Transfer" else "Settlement Payment",
                        subtitle = settlement.note.ifBlank { "You paid them" },
                        amount = settlement.amount,
                        netChange = settlement.amount,
                        isSettlement = true
                    )
                )
            } else if (settlement.fromPersonId == personId && settlement.toPersonId == -1L) {
                // Person paid me
                items.add(
                    PersonLedgerItem(
                        id = "settlement_${settlement.id}",
                        date = settlement.date,
                        title = if (settlement.isIou) "Borrowed / Loan Received" else "Settlement Received",
                        subtitle = settlement.note.ifBlank { "They paid you" },
                        amount = settlement.amount,
                        netChange = -settlement.amount,
                        isSettlement = true
                    )
                )
            }
        }

        items.sortedByDescending { it.date }
    }.flowOn(Dispatchers.IO)

    val monthlyAnalytics: Flow<MonthlyAnalytics> = combine(
        allTransactions,
        allSplits,
        allSplitShares
    ) { transactions, splits, shares ->
        val splitMap = splits.associateBy { it.transactionId }
        val shareMap = shares.groupBy { it.splitId }

        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        val monthTransactions = transactions.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
            cal.get(Calendar.MONTH) == currentMonth && cal.get(Calendar.YEAR) == currentYear
        }

        var totalPersonalExpense = 0.0
        var totalIncome = 0.0
        val categoryTotals = mutableMapOf<String, Double>()
        val categoryCounts = mutableMapOf<String, Int>()
        val merchantTotals = mutableMapOf<String, Double>()

        for (tx in monthTransactions) {
            if (tx.type.equals("income", ignoreCase = true)) {
                totalIncome += tx.amount
            } else {
                val split = splitMap[tx.id]
                val personalExpenseAmount = if (split != null) {
                    val splitShares = shareMap[split.id] ?: emptyList()
                    if (split.paidByPersonId == -1L) {
                        val myShare = splitShares.find { it.personId == -1L }?.shareAmount
                        if (myShare != null) {
                            myShare
                        } else {
                            val friendsShare = splitShares.filter { it.personId != -1L }.sumOf { it.shareAmount }
                            (tx.amount - friendsShare).coerceAtLeast(0.0)
                        }
                    } else {
                        val myShare = splitShares.find { it.personId == -1L }?.shareAmount ?: 0.0
                        myShare
                    }
                } else {
                    tx.amount
                }

                totalPersonalExpense += personalExpenseAmount
                categoryTotals[tx.category] = (categoryTotals[tx.category] ?: 0.0) + personalExpenseAmount
                categoryCounts[tx.category] = (categoryCounts[tx.category] ?: 0) + 1
                if (tx.merchant.isNotBlank()) {
                    merchantTotals[tx.merchant] = (merchantTotals[tx.merchant] ?: 0.0) + personalExpenseAmount
                }
            }
        }

        val summaries = categoryTotals.map { (cat, amount) ->
            CategorySummary(
                category = cat,
                amount = (amount * 100).toLong() / 100.0,
                percentage = if (totalPersonalExpense > 0) ((amount / totalPersonalExpense) * 100f).toFloat() else 0f,
                count = categoryCounts[cat] ?: 1
            )
        }.sortedByDescending { it.amount }

        val topCategory = summaries.firstOrNull()?.category ?: "Food"
        val topCategoryAmount = summaries.firstOrNull()?.amount ?: 0.0
        val topMerchant = merchantTotals.maxByOrNull { it.value }?.key ?: (summaries.firstOrNull()?.category ?: "None")
        val streak = calculateDailyStreak(transactions)

        MonthlyAnalytics(
            totalExpense = (totalPersonalExpense * 100).toLong() / 100.0,
            totalIncome = (totalIncome * 100).toLong() / 100.0,
            netCashflow = ((totalIncome - totalPersonalExpense) * 100).toLong() / 100.0,
            categorySummaries = summaries,
            topMerchant = topMerchant,
            dailyStreak = streak,
            topCategory = topCategory,
            topCategoryAmount = topCategoryAmount
        )
    }.flowOn(Dispatchers.IO)

    private fun calculateDailyStreak(transactions: List<TransactionEntity>): Int {
        if (transactions.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val daysWithTransactions = transactions.map { sdf.format(Date(it.date)) }.toSet()

        val cal = Calendar.getInstance()
        var streak = 0
        val todayStr = sdf.format(cal.time)

        if (!daysWithTransactions.contains(todayStr)) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            val yesterdayStr = sdf.format(cal.time)
            if (!daysWithTransactions.contains(yesterdayStr)) {
                return 0
            }
        } else {
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val dateStr = sdf.format(cal.time)
            if (daysWithTransactions.contains(dateStr)) {
                streak++
                cal.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    suspend fun saveTransactionWithSplits(
        tx: TransactionEntity,
        paidByPersonId: Long = -1L,
        shares: List<SplitShareEntity> = emptyList(),
        splitType: String = "equal"
    ): Long = withContext(Dispatchers.IO) {
        val txId = db.transactionDao().insertTransaction(tx.copy(isSplit = shares.isNotEmpty()))
        if (shares.isNotEmpty()) {
            val split = SplitEntity(
                transactionId = txId,
                totalAmount = tx.amount,
                paidByPersonId = paidByPersonId,
                splitType = splitType
            )
            val splitId = db.splitDao().insertSplit(split)
            val mappedShares = shares.map { it.copy(splitId = splitId) }
            db.splitDao().insertSplitShares(mappedShares)
        }
        txId
    }

    suspend fun batchInsertTransactions(
        transactions: List<TransactionEntity>
    ): List<Long> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) return@withContext emptyList()
        db.transactionDao().insertTransactions(transactions)
    }

    suspend fun updateTransactionWithSplits(
        tx: TransactionEntity,
        paidByPersonId: Long = -1L,
        shares: List<SplitShareEntity> = emptyList(),
        splitType: String = "equal"
    ) = withContext(Dispatchers.IO) {
        db.transactionDao().updateTransaction(tx.copy(isSplit = shares.isNotEmpty()))
        db.splitDao().deleteSplitByTransactionId(tx.id)
        if (shares.isNotEmpty()) {
            val split = SplitEntity(
                transactionId = tx.id,
                totalAmount = tx.amount,
                paidByPersonId = paidByPersonId,
                splitType = splitType
            )
            val splitId = db.splitDao().insertSplit(split)
            val mappedShares = shares.map { it.copy(splitId = splitId) }
            db.splitDao().insertSplitShares(mappedShares)
        }
    }

    suspend fun deleteTransaction(txId: Long) = withContext(Dispatchers.IO) {
        db.transactionDao().deleteTransactionById(txId)
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
        val settlement = SettlementEntity(
            fromPersonId = fromPersonId,
            toPersonId = toPersonId,
            amount = amount,
            date = date,
            note = note,
            isIou = isIou,
            groupId = groupId
        )
        db.settlementDao().insertSettlement(settlement)
    }

    suspend fun deleteSettlement(settlementId: Long) = withContext(Dispatchers.IO) {
        db.settlementDao().deleteSettlementById(settlementId)
    }

    suspend fun addPerson(name: String, avatarColor: String): Long = withContext(Dispatchers.IO) {
        db.personDao().insertPerson(PersonEntity(name = name.trim(), avatarColor = avatarColor))
    }

    suspend fun deletePerson(personId: Long) = withContext(Dispatchers.IO) {
        db.splitDao().deleteSharesForPerson(personId)
        db.settlementDao().deleteSettlementsForPerson(personId)
        db.groupDao().deleteMemberByPersonId(personId)
        db.personDao().deletePersonById(personId)
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

    suspend fun updateGroup(group: GroupEntity, memberPersonIds: List<Long>) = withContext(Dispatchers.IO) {
        db.groupDao().updateGroup(group)
        db.groupDao().deleteMembersForGroup(group.id)
        val members = mutableListOf<GroupMemberEntity>()
        members.add(GroupMemberEntity(groupId = group.id, personId = -1L))
        memberPersonIds.filter { it != -1L }.forEach { pid ->
            members.add(GroupMemberEntity(groupId = group.id, personId = pid))
        }
        db.groupDao().insertGroupMembers(members)
    }

    suspend fun deleteGroup(groupId: Long) = withContext(Dispatchers.IO) {
        db.groupDao().deleteMembersForGroup(groupId)
        db.groupDao().deleteGroupById(groupId)
    }

    /**
     * Professional, statement-like CSV generation for analysis, spreadsheets, and taxation.
     */
    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val transactions = db.transactionDao().getAllTransactionsDirect()
        val splits = db.splitDao().getAllSplitsDirect().associateBy { it.transactionId }
        val shares = db.splitDao().getAllSplitSharesDirect().groupBy { it.splitId }
        val people = db.personDao().getAllPeopleDirect().associateBy { it.id }
        val groups = db.groupDao().getAllGroupsDirect().associateBy { it.id }

        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US)
        val exportTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val totalRecords = transactions.size
        val totalExpenses = transactions.filter { it.type.equals("expense", ignoreCase = true) }.sumOf { it.amount }
        val totalIncome = transactions.filter { it.type.equals("income", ignoreCase = true) }.sumOf { it.amount }

        val sb = StringBuilder()
        sb.append("# ==========================================================================\n")
        sb.append("# KHARCHA PAANI - COMPREHENSIVE FINANCIAL & EXPENSE STATEMENT\n")
        sb.append("# Generated On: $exportTimestamp\n")
        sb.append("# Total Transactions: $totalRecords | Total Expenses: INR $totalExpenses | Total Income: INR $totalIncome\n")
        sb.append("# ==========================================================================\n\n")

        sb.append("Transaction ID,Date,Time,Type,Amount (INR),Your Share (INR),Category,Merchant/Payee,Payment Method,Trip / Group,Split Status,Paid By,Participants Breakdown,Note/Reference\n")

        for (tx in transactions) {
            val date = Date(tx.date)
            val dateStr = sdfDate.format(date)
            val timeStr = sdfTime.format(date)
            val split = splits[tx.id]
            val splitShares = split?.let { shares[it.id] } ?: emptyList()
            val fullTx = FullTransaction(tx, split, splitShares)
            val yourShare = fullTx.myPersonalShare
            val groupName = tx.groupId?.let { groups[it]?.name } ?: "General"

            val paidByName = if (split == null || split.paidByPersonId == -1L) "You" else (people[split.paidByPersonId]?.name ?: "Friend")
            val splitStatus = if (split != null) "Split (${split.splitType})" else "Personal"

            val splitDesc = if (split != null) {
                splitShares.joinToString("; ") { share ->
                    val name = if (share.personId == -1L) "You" else people[share.personId]?.name ?: "Friend"
                    "$name: INR ${share.shareAmount}"
                }
            } else "N/A"

            sb.append("\"KP-${tx.id}\",")
            sb.append("\"$dateStr\",")
            sb.append("\"$timeStr\",")
            sb.append("\"${tx.type.uppercase(Locale.US)}\",")
            sb.append("${tx.amount},")
            sb.append("$yourShare,")
            sb.append("\"${tx.category}\",")
            sb.append("\"${tx.merchant.replace("\"", "\"\"")}\",")
            sb.append("\"${tx.paymentMethod}\",")
            sb.append("\"${groupName.replace("\"", "\"\"")}\",")
            sb.append("\"$splitStatus\",")
            sb.append("\"$paidByName\",")
            sb.append("\"${splitDesc.replace("\"", "\"\"")}\",")
            sb.append("\"${tx.note.replace("\"", "\"\"")}\"\n")
        }

        sb.append("\n# ==========================================================================\n")
        sb.append("# EXPENSE CATEGORY SUMMARY\n")
        sb.append("# ==========================================================================\n")
        sb.append("Category,Total Spent (INR),Transaction Count\n")

        val catMap = transactions.filter { it.type.equals("expense", true) }.groupBy { it.category }
        for ((cat, txList) in catMap.entries.sortedByDescending { it.value.sumOf { tx -> tx.amount } }) {
            val sum = txList.sumOf { it.amount }
            sb.append("\"$cat\",$sum,${txList.size}\n")
        }

        sb.toString()
    }
}
