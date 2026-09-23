package com.vaibhav.kharchapaani.data.model

import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity

data class ParsedTransactionResult(
    val type: String = "expense", // "expense" or "income"
    val amount: Double = 0.0,
    val merchant: String = "",
    val category: String = "Other",
    val paymentMethod: String = "PopUPI",
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val splitWithPersonNames: List<String> = emptyList(),
    val confidence: Float = 1.0f,
    /** The bill couldn't be read: nothing here came from it, and the person should be told. */
    val readFailed: Boolean = false,
    /** The AI couldn't be reached, so this came from matching words on the phone: worth a check. */
    val guessedOnPhone: Boolean = false,
)

data class SplitWithShares(
    val split: SplitEntity,
    val shares: List<SplitShareEntity>
)

data class FullTransaction(
    val transaction: TransactionEntity,
    val split: SplitEntity? = null,
    val shares: List<SplitShareEntity> = emptyList()
) {
    /**
     * Whether this account may change or delete it: it added it, or it's a group expense this account
     * paid for (backlog item 11; the server's private.can_edit_group_expense). Only the adder can
     * change who paid or move it out of its group.
     */
    val canEdit: Boolean
        get() = transaction.isOwnedByMe || (transaction.groupId != null && split?.paidByPersonId == -1L)

    val myPersonalShare: Double
        get() {
            if (transaction.type.equals("income", ignoreCase = true)) return transaction.amount
            if (split == null) return transaction.amount
            if (split.paidByPersonId == -1L) {
                val myShare = shares.find { it.personId == -1L }?.shareAmount
                if (myShare != null) return myShare
                val friendsShare = shares.filter { it.personId != -1L }.sumOf { it.shareAmount }
                return (transaction.amount - friendsShare).coerceAtLeast(0.0)
            } else {
                return shares.find { it.personId == -1L }?.shareAmount ?: 0.0
            }
        }
}

data class PersonBalance(
    val person: PersonEntity,
    val netBalance: Double, // > 0: person owes me; < 0: I owe person; == 0: settled
    val totalTheyOweMe: Double,
    val totalIOweThem: Double
)

data class PersonLedgerItem(
    val id: String,
    val date: Long,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val netChange: Double, // positive = person owes me more, negative = I owe person more / reduced debt
    val isSettlement: Boolean
)

data class GroupWithDetails(
    val group: GroupEntity,
    val members: List<PersonEntity>, // members excluding "Me" (Me is always part)
    val totalExpenses: Double,
    val myPaidAmount: Double,
    val myShare: Double,
    val netBalanceForMe: Double, // > 0: group members owe me; < 0: I owe group
    val transactionsCount: Int,
    val transactions: List<FullTransaction> = emptyList(),
    /** This group's settlements, for who-owes-whom inside it. */
    val settlements: List<SettlementEntity> = emptyList(),
    /** Expenses, settlements and group edits here that haven't reached the server yet. */
    val unsyncedChanges: Int = 0,
)

data class SimplifiedDebt(
    val fromPersonId: Long,
    val fromPersonName: String,
    val toPersonId: Long,
    val toPersonName: String,
    val amount: Double
)

data class CategorySummary(
    val category: String,
    val amount: Double,
    val percentage: Float,
    val count: Int
)

data class MonthlyAnalytics(
    val totalExpense: Double,
    val totalIncome: Double,
    val netCashflow: Double,
    val categorySummaries: List<CategorySummary>,
    val topMerchant: String,
    val dailyStreak: Int,
    val topCategory: String = "Food",
    val topCategoryAmount: Double = 0.0
)

enum class SplitMode {
    EQUAL,
    EXACT,
    PERCENTAGE
}

data class ShareInput(
    val personId: Long, // -1L for "Me", otherwise Person ID
    val personName: String,
    val amount: Double = 0.0,
    val percentage: Double = 0.0,
    val isSelected: Boolean = true
)

data class StatementTransactionItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val date: Long, // timestamp millis
    val dateFormatted: String, // e.g. "12 Aug 2024"
    val monthYearKey: String, // e.g. "August 2024"
    val monthSortKey: String, // e.g. "202408"
    val type: String = "expense", // "expense" or "income"
    val amount: Double,
    val merchant: String,
    val category: String = "Food",
    val paymentMethod: String = "PopUPI",
    val note: String = "",
    val isSelected: Boolean = true
)

data class StatementMonthGroup(
    val monthYearKey: String,
    val monthSortKey: String,
    val totalExpense: Double,
    val totalIncome: Double,
    val transactionCount: Int,
    val items: List<StatementTransactionItem>
)

data class StatementParseResult(
    val bankOrApp: String = "Bank / UPI Statement",
    val statementPeriod: String = "",
    val totalTransactionsCount: Int = 0,
    val totalExpenseSum: Double = 0.0,
    val totalIncomeSum: Double = 0.0,
    val monthGroups: List<StatementMonthGroup> = emptyList(),
    val allItems: List<StatementTransactionItem> = emptyList()
)
