package com.vaibhav.kharchapaani.ui.audit

import com.vaibhav.kharchapaani.data.auth.AuthState
import com.vaibhav.kharchapaani.data.auth.Profile
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.ui.auth.AuthUiState
import com.vaibhav.kharchapaani.ui.auth.ProfileState
import java.time.LocalDate
import java.time.ZoneId

/**
 * A lived-in account for the UI audit (docs/audits/ui-audit-2026-11.md): the cases a screen has to
 * survive, not a showcase. A long friend name, a friend who isn't on the app, someone who owes me
 * and someone I owe, a shared trip, a local flat group, an archived group, income, a payment waiting
 * for my answer, one I sent that was turned down, an IOU, a subscription and last month's spending
 * for Insights to compare against. Fictional people only; Cash and UPI where a method shows.
 */
object AuditFixture {
    const val USER_ID = "a0d17000-0000-4000-8000-0000000000a0"
    const val ROHAN = 1L
    const val ANKIT = 2L
    const val MEERA = 3L
    const val LONG_NAME = 4L
    const val GOA = 10L
    const val FLAT = 11L
    const val OLD_TRIP = 12L
    const val DINNER = 100L
    const val ROHANS_PAYMENT = 200L

    val authUiState = AuthUiState(
        authState = AuthState.SignedIn(userId = USER_ID, email = "priya@example.com", isRefreshPending = false),
        profile = ProfileState.Loaded(Profile(id = USER_ID, displayName = "Priya")),
    )

    private val zone: ZoneId = ZoneId.systemDefault()

    /** This month, never ahead of now; early in a month, pulled forward to its first day. */
    private fun stamp(daysAgo: Long, hour: Int, minute: Int = 0): Long {
        val today = LocalDate.now(zone)
        val day = today.minusDays(daysAgo).coerceAtLeast(today.withDayOfMonth(1))
        val at = day.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        return at.coerceAtMost(System.currentTimeMillis() - (daysAgo + 1) * 7 * 60_000L)
    }

    private fun lastMonth(day: Int, hour: Int): Long =
        LocalDate.now(zone).minusMonths(1).withDayOfMonth(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    suspend fun seed(db: AppDatabase) {
        val people = db.personDao()
        people.insertPerson(PersonEntity(id = ROHAN, name = "Rohan", avatarColor = "#70C1FF", profileId = "f1c0ffee-0000-4000-8000-00000000r001"))
        people.insertPerson(PersonEntity(id = ANKIT, name = "Ankit", avatarColor = "#B5EAD7", profileId = "f1c0ffee-0000-4000-8000-00000000a001"))
        // Not on the app: a guest in shared splits.
        people.insertPerson(PersonEntity(id = MEERA, name = "Meera", avatarColor = "#FFB3BA"))
        people.insertPerson(PersonEntity(id = LONG_NAME, name = "Venkataraghavan Subramaniam", avatarColor = "#D7BDE2"))

        val groups = db.groupDao()
        groups.insertGroup(GroupEntity(id = GOA, name = "Goa trip", emoji = "🏖️", colorHex = "#70C1FF", syncState = SyncState.SYNCED, createdByProfileId = USER_ID))
        groups.insertGroupMembers(listOf(GroupMemberEntity(groupId = GOA, personId = ROHAN), GroupMemberEntity(groupId = GOA, personId = ANKIT)))
        groups.insertGroup(GroupEntity(id = FLAT, name = "Flat 402", emoji = "🏠", colorHex = "#C6F24C"))
        groups.insertGroupMembers(listOf(GroupMemberEntity(groupId = FLAT, personId = MEERA), GroupMemberEntity(groupId = FLAT, personId = LONG_NAME)))
        groups.insertGroup(
            GroupEntity(
                id = OLD_TRIP, name = "Lonavala monsoon weekend with college friends", emoji = "⛰️", colorHex = "#FF7A3D",
                syncState = SyncState.SYNCED, createdByProfileId = USER_ID, archivedAt = lastMonth(20, 10),
            ),
        )
        groups.insertGroupMembers(listOf(GroupMemberEntity(groupId = OLD_TRIP, personId = ROHAN)))

        val tx = db.transactionDao()
        val splits = db.splitDao()
        suspend fun split(id: Long, amount: Double, merchant: String, category: String, method: String, date: Long, group: Long?, paidBy: Long, shares: Map<Long, Double>, synced: Boolean) {
            tx.insertTransaction(
                TransactionEntity(
                    id = id, type = "expense", amount = amount, category = category, merchant = merchant, paymentMethod = method, date = date,
                    isSplit = true, groupId = group, syncState = if (synced) SyncState.SYNCED else SyncState.LOCAL_ONLY,
                ),
            )
            val splitId = splits.insertSplit(SplitEntity(transactionId = id, totalAmount = amount, paidByPersonId = paidBy))
            splits.insertSplitShares(shares.map { (person, share) -> SplitShareEntity(splitId = splitId, personId = person, shareAmount = share, percentage = 100.0 * share / amount) })
        }
        split(DINNER, 2400.0, "Dinner at the shack", "Food", "UPI", stamp(2, 21), GOA, -1L, mapOf(-1L to 800.0, ROHAN to 800.0, ANKIT to 800.0), synced = true)
        split(101, 1500.0, "Scooter rental", "Transport", "Cash", stamp(2, 10), GOA, ROHAN, mapOf(-1L to 500.0, ROHAN to 500.0, ANKIT to 500.0), synced = true)
        split(102, 3200.0, "Electricity bill", "Bills", "UPI", stamp(4, 9), FLAT, -1L, mapOf(-1L to 1066.67, MEERA to 1066.67, LONG_NAME to 1066.66), synced = false)
        split(103, 640.0, "Movie", "Entertainment", "UPI", stamp(1, 19), null, LONG_NAME, mapOf(-1L to 320.0, LONG_NAME to 320.0), synced = false)

        tx.insertTransaction(TransactionEntity(type = "expense", amount = 40.0, category = "Food", merchant = "Chai stall", paymentMethod = "Cash", date = stamp(0, 9, 10)))
        tx.insertTransaction(TransactionEntity(type = "expense", amount = 1260.0, category = "Groceries", merchant = "Weekly groceries", paymentMethod = "UPI", date = stamp(1, 18, 30)))
        tx.insertTransaction(TransactionEntity(type = "expense", amount = 80.0, category = "Transport", merchant = "Bus", paymentMethod = "Cash", date = stamp(1, 8, 45)))
        tx.insertTransaction(TransactionEntity(type = "income", amount = 42000.0, category = "Income (Client)", merchant = "Client payment", paymentMethod = "Bank Transfer", date = stamp(3, 11)))
        tx.insertTransaction(TransactionEntity(type = "expense", amount = 124999.5, category = "Shopping", merchant = "Laptop", paymentMethod = "UPI", date = stamp(5, 16)))
        // Last month, so Insights has something to compare with.
        tx.insertTransaction(TransactionEntity(type = "expense", amount = 3100.0, category = "Food", merchant = "Restaurants", paymentMethod = "UPI", date = lastMonth(12, 20)))
        tx.insertTransaction(TransactionEntity(type = "expense", amount = 900.0, category = "Transport", merchant = "Cabs", paymentMethod = "UPI", date = lastMonth(18, 9)))

        val settlements = db.settlementDao()
        // Rohan says he paid me; waiting for my answer.
        settlements.insertSettlement(
            SettlementEntity(
                id = ROHANS_PAYMENT, fromPersonId = ROHAN, toPersonId = -1L, amount = 300.0, date = stamp(0, 8),
                groupId = GOA, syncState = SyncState.SYNCED, status = SettlementStatus.PENDING, recordedByPersonId = ROHAN,
            ),
        )
        // I said I paid Ankit back; he said it isn't right.
        settlements.insertSettlement(
            SettlementEntity(
                fromPersonId = -1L, toPersonId = ANKIT, amount = 150.0, date = stamp(3, 20), groupId = GOA, syncState = SyncState.SYNCED,
                status = SettlementStatus.REJECTED, recordedByPersonId = -1L, responseNote = "That was for the cab, not dinner",
            ),
        )
        // An IOU: I lent Meera money.
        settlements.insertSettlement(SettlementEntity(fromPersonId = -1L, toPersonId = MEERA, amount = 2000.0, date = stamp(6, 13), isIou = true, note = "Train tickets"))

        db.subscriptionDao().insert(
            SubscriptionEntity(name = "Music", amount = 119.0, category = "Entertainment", paymentMethod = "UPI", cycle = "MONTHLY", startDate = lastMonth(5, 9)),
        )
    }
}
