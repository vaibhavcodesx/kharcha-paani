package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
import com.vaibhav.kharchapaani.domain.BackupSettings
import com.vaibhav.kharchapaani.domain.BackupReadResult
import com.vaibhav.kharchapaani.domain.LocalBackup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The test that should have existed the first time.
 *
 * `LocalBackupTest` built a `LocalBackup` by hand and round-tripped it through JSON. It passed
 * while the feature was broken, because it never called [LocalBackupRepository.collect] — the
 * `splits` list was computed there and then never passed to the constructor, and `settings` was
 * never computed at all. Both defaulted to empty, silently, in every export anyone took.
 *
 * So this one starts from a database with real money in it — expenses split in both directions, an
 * IOU each way, a settlement, a local group, a subscription, custom categories — exports, restores
 * into an empty database, and asserts the **balances** match. Not "export succeeded": the numbers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupRoundTripTest {
    private val copyContext = ApplicationProvider.getApplicationContext<android.content.Context>()

    private lateinit var source: AppDatabase
    private lateinit var target: AppDatabase

    private val me = -1L
    private var friendId = 0L
    private var groupId = 0L

    private fun newDb(): AppDatabase = Room
        .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @Before
    fun setUp() {
        source = newDb()
        target = newDb()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    /** A ledger with something actually at stake in it. */
    private suspend fun seed() {
        friendId = source.personDao().insertPerson(
            PersonEntity(name = "sample_frnd", avatarColor = "#F2D06B", createdAt = 1_000L)
        )
        groupId = source.groupDao().insertGroup(
            GroupEntity(name = "Flat", emoji = "🏠", colorHex = "#70C1FF", createdAt = 900L, syncState = SyncState.LOCAL_ONLY)
        )
        source.groupSyncDao().insertMembers(
            listOf(
                GroupMemberEntity(groupId = groupId, personId = me),
                GroupMemberEntity(groupId = groupId, personId = friendId),
            )
        )

        // 1. I paid ₹500, split evenly — they owe me ₹250.
        addSplit(merchant = "Dinner", amount = 500.0, paidBy = me, myShare = 250.0, theirShare = 250.0, date = 2_000L)

        // 2. They paid ₹300, split evenly — I owe them ₹150. The direction that a lost payer
        //    field would silently reverse.
        addSplit(merchant = "Cab", amount = 300.0, paidBy = friendId, myShare = 150.0, theirShare = 150.0, date = 3_000L)

        // 3. An IOU each way, and a settlement.
        source.groupSyncDao().insertSettlement(
            SettlementEntity(fromPersonId = friendId, toPersonId = me, amount = 40.0, date = 4_000L, isIou = true, createdAt = 4_000L)
        )
        source.groupSyncDao().insertSettlement(
            SettlementEntity(fromPersonId = me, toPersonId = friendId, amount = 15.0, date = 5_000L, isIou = true, createdAt = 5_000L)
        )
        source.groupSyncDao().insertSettlement(
            SettlementEntity(fromPersonId = friendId, toPersonId = me, amount = 25.0, date = 6_000L, isIou = false, createdAt = 6_000L)
        )

        source.subscriptionDao().insert(
            SubscriptionEntity(
                name = "Netflis", amount = 499.0, category = "Bills", paymentMethod = "PopUPI",
                cycle = "MONTHLY", startDate = 7_000L, createdAt = 7_000L,
            )
        )
    }

    private suspend fun addSplit(
        merchant: String,
        amount: Double,
        paidBy: Long,
        myShare: Double,
        theirShare: Double,
        date: Long,
    ) {
        val txId = source.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense", amount = amount, category = "Food", merchant = merchant,
                paymentMethod = "Cash", date = date, isSplit = true, createdAt = date,
                syncState = SyncState.LOCAL_ONLY,
            )
        )
        val splitId = source.splitDao().insertSplit(
            SplitEntity(transactionId = txId, totalAmount = amount, paidByPersonId = paidBy, splitType = "equal")
        )
        source.groupSyncDao().insertShares(
            listOf(
                SplitShareEntity(splitId = splitId, personId = me, shareAmount = myShare),
                SplitShareEntity(splitId = splitId, personId = friendId, shareAmount = theirShare),
            )
        )
    }

    private val settings = BackupSettings(
        customCategories = listOf("Sample_Category"),
        customPaymentMethods = listOf("Sample_Method"),
        hiddenCategories = listOf("Rent"),
        askBeforeDiscarding = false,
    )

    private suspend fun exported(): LocalBackup =
        LocalBackupRepository(source).collect(appVersion = "1.1.0", settings = settings)

    // --- What the broken version got wrong

    @Test
    fun `a split expense is in the backup at all`() = runTest {
        seed()

        val backup = exported()

        // The bug, in one assertion. Every export shipped with this list empty.
        assertEquals(2, backup.transactions.size)
        assertTrue(backup.transactions.all { it.split != null })
    }

    @Test
    fun `custom categories and payment methods are in the backup`() = runTest {
        seed()

        val backup = exported()

        assertEquals(listOf("Sample_Category"), backup.settings.customCategories)
        assertEquals(listOf("Sample_Method"), backup.settings.customPaymentMethods)
        assertEquals(listOf("Rent"), backup.settings.hiddenCategories)
        assertEquals(false, backup.settings.askBeforeDiscarding)
    }

    @Test
    fun `who paid is recorded for each split`() = runTest {
        seed()

        val backup = exported()
        val dinner = backup.transactions.single { it.merchant == "Dinner" }
        val cab = backup.transactions.single { it.merchant == "Cab" }

        // Null is me. Losing this would turn "they paid" into "I paid" and flip the balance
        // without anything failing.
        assertEquals(null, dinner.split!!.paidByName)
        assertEquals("sample_frnd", cab.split!!.paidByName)
    }

    @Test
    fun `IOUs and settlements in both directions are all present`() = runTest {
        seed()

        val backup = exported()

        assertEquals(3, backup.settlements.size)
        assertEquals(2, backup.settlements.count { it.isIou })
        assertEquals(1, backup.settlements.count { it.fromName == null }) // one I owed
        assertEquals(2, backup.settlements.count { it.toName == null })   // two owed to me
    }

    // --- The thing that actually matters

    @Test
    fun `the balance with a local friend survives a full round trip`() = runTest {
        seed()
        val before = balanceOf(source)

        val report = LocalBackupRepository(target).restore(exported())

        assertEquals("nothing should have been skipped on an empty phone", 0, report.skipped)
        assertEquals(before, balanceOf(target), 0.001)
    }

    @Test
    fun `the balance is not merely non-zero, it is the right number`() = runTest {
        seed()

        LocalBackupRepository(target).restore(exported())

        // They owe me 250 for dinner, I owe them 150 for the cab, they owe 40 and I owe 15 in
        // IOUs, and they have settled 25. Net: 250 - 150 + 40 - 15 - 25 = 100 to me.
        assertEquals(100.0, balanceOf(target), 0.001)
    }

    @Test
    fun `when each expense happened, and when it was logged, both survive`() = runTest {
        // A restored ledger with every date reset to the day of the restore would balance
        // correctly and still be useless: you could not tell which month anything belonged to,
        // and Insights would report the lot as spent today.
        seed()

        LocalBackupRepository(target).restore(exported())

        val restored = target.transactionDao().getAllTransactionsDirect().associateBy { it.merchant }
        assertEquals(2_000L, restored.getValue("Dinner").date)
        assertEquals(2_000L, restored.getValue("Dinner").createdAt)
        assertEquals(3_000L, restored.getValue("Cab").date)
        assertEquals(3_000L, restored.getValue("Cab").createdAt)
    }

    @Test
    fun `every IOU and settlement keeps its own date too`() = runTest {
        seed()

        LocalBackupRepository(target).restore(exported())

        val dates = target.settlementDao().getAllSettlementsDirect().map { it.date }.sorted()
        assertEquals(listOf(4_000L, 5_000L, 6_000L), dates)
    }

    @Test
    fun `the exported file carries a date for every row`() = runTest {
        // Checked on the file itself, not just after a restore: the backup is something a person
        // can open and read, and a row with no date in it is not a record of anything.
        seed()
        val backup = exported()

        assertTrue(backup.transactions.all { it.date > 0 && it.createdAt > 0 })
        assertTrue(backup.settlements.all { it.date > 0 && it.createdAt > 0 })
    }

    @Test
    fun `a local group and its membership come back`() = runTest {
        seed()

        LocalBackupRepository(target).restore(exported())

        val group = target.groupDao().getAllGroupsDirect().single()
        assertEquals("Flat", group.name)
        assertEquals(false, group.isShared)
        assertEquals(2, target.groupDao().getMembersForGroupDirect(group.id).size)
    }

    @Test
    fun `restoring twice changes nothing the second time`() = runTest {
        seed()
        val backup = exported()
        val repo = LocalBackupRepository(target)
        repo.restore(backup)
        val balanceAfterFirst = balanceOf(target)

        val second = repo.restore(backup)

        assertEquals("the second run must add nothing", 0, second.added)
        assertEquals(balanceAfterFirst, balanceOf(target), 0.001)
    }

    @Test
    fun `an expense already on the phone is not duplicated by a restore`() = runTest {
        seed()
        val backup = exported()
        // The realistic case: the phone has synced some of this back already.
        target.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense", amount = 500.0, category = "Food", merchant = "Dinner",
                paymentMethod = "Cash", date = 2_000L, isSplit = true, createdAt = 2_000L,
            )
        )

        LocalBackupRepository(target).restore(backup)

        assertEquals(2, target.transactionDao().getAllTransactionsDirect().count())
    }

    // --- The rule the contents are chosen by

    @Test
    fun `an ordinary personal expense is left out, because the server has it`() = runTest {
        seed()
        source.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense", amount = 99.0, category = "Food", merchant = "Chai",
                paymentMethod = "Cash", date = 8_000L, isSplit = false, syncState = SyncState.SYNCED,
            )
        )

        val backup = exported()

        assertEquals(null, backup.transactions.firstOrNull { it.merchant == "Chai" })
    }

    @Test
    fun `an expense inside a local-only group is included`() = runTest {
        seed()
        source.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense", amount = 60.0, category = "Food", merchant = "Milk",
                paymentMethod = "Cash", date = 9_000L, isSplit = false, groupId = groupId,
                syncState = SyncState.LOCAL_ONLY,
            )
        )

        val backup = exported()
        val milk = backup.transactions.single { it.merchant == "Milk" }

        assertEquals("Flat", milk.groupName)
    }

    @Test
    fun `the exported file is readable back`() = runTest {
        seed()

        val decoded = LocalBackup.decode(copyContext, LocalBackup.encode(exported()))

        assertNotNull(decoded)
        assertTrue(decoded is com.vaibhav.kharchapaani.domain.BackupReadResult.Ok)
    }

    /**
     * What the friend is owed, from the ledger's own rows: their share of things I paid, less my
     * share of things they paid, plus IOUs and settlements either way. Positive means they owe me.
     */

    @Test
    fun `backup file round trip preserves every supported backup field`() = runTest {
        val personId = source.personDao().insertPerson(
            PersonEntity(
                name = "Aarav",
                avatarColor = "#4A90E2",
                avatarEmoji = "⭐",
                createdAt = 100_001L,
            )
        )

        val localGroupId = source.groupDao().insertGroup(
            GroupEntity(
                name = "Weekend Trek",
                description = "Western Ghats trail trip",
                emoji = "⛰️",
                colorHex = "#2ECC71",
                createdAt = 200_001L,
                archivedAt = 300_001L,
                syncState = SyncState.LOCAL_ONLY,
            )
        )
        source.groupSyncDao().insertMembers(
            listOf(
                GroupMemberEntity(groupId = localGroupId, personId = me),
                GroupMemberEntity(groupId = localGroupId, personId = personId),
            )
        )

        source.subscriptionDao().insert(
            SubscriptionEntity(
                name = "Cloud Backup Service",
                amount = 299.0,
                category = "Bills",
                paymentMethod = "PopUPI",
                cycle = "MONTHLY",
                startDate = 400_001L,
                chargedThrough = 500_001L,
                cancelledAt = 600_001L,
                createdAt = 700_001L,
            )
        )

        // 1. Direct expense inside local group with note and custom remoteId
        source.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense",
                amount = 350.0,
                category = "Food",
                merchant = "Dhaba Express",
                paymentMethod = "Cash",
                date = 800_001L,
                note = "Breakfast on highway",
                isSplit = false,
                groupId = localGroupId,
                createdAt = 800_001L,
                remoteId = "uuid-tx-direct-1",
                syncState = SyncState.LOCAL_ONLY,
            )
        )

        // 2. Split expense inside local group with exact split type and percentages
        val splitTxId = source.transactionDao().insertTransaction(
            TransactionEntity(
                type = "expense",
                amount = 1200.0,
                category = "Transport",
                merchant = "Jeep Rental",
                paymentMethod = "Bank Transfer",
                date = 900_001L,
                note = "Trail head transfer",
                isSplit = true,
                groupId = localGroupId,
                createdAt = 900_001L,
                remoteId = "uuid-tx-split-2",
                syncState = SyncState.LOCAL_ONLY,
            )
        )
        val splitId = source.splitDao().insertSplit(
            SplitEntity(
                transactionId = splitTxId,
                totalAmount = 1200.0,
                paidByPersonId = personId,
                splitType = "exact",
            )
        )
        source.groupSyncDao().insertShares(
            listOf(
                SplitShareEntity(splitId = splitId, personId = me, shareAmount = 400.0, percentage = 33.33),
                SplitShareEntity(splitId = splitId, personId = personId, shareAmount = 800.0, percentage = 66.67),
            )
        )

        // 3. Income transaction (local only)
        source.transactionDao().insertTransaction(
            TransactionEntity(
                type = "income",
                amount = 5000.0,
                category = "Income (Client)",
                merchant = "Consulting Stipend",
                paymentMethod = "Bank Transfer",
                date = 1_200_001L,
                note = "Project milestone 1",
                isSplit = false,
                groupId = null,
                createdAt = 1_200_001L,
                remoteId = "uuid-tx-income-3",
                syncState = SyncState.LOCAL_ONLY,
            )
        )

        // 4. IOU with note and group
        source.groupSyncDao().insertSettlement(
            SettlementEntity(
                fromPersonId = personId,
                toPersonId = me,
                amount = 150.0,
                date = 1_000_001L,
                note = "Cash borrowed for toll plaza",
                isIou = true,
                groupId = localGroupId,
                createdAt = 1_000_001L,
                syncState = SyncState.LOCAL_ONLY,
            )
        )

        // 5. Non-IOU settlement with note and group
        source.groupSyncDao().insertSettlement(
            SettlementEntity(
                fromPersonId = me,
                toPersonId = personId,
                amount = 50.0,
                date = 1_100_001L,
                note = "Returned change",
                isIou = false,
                groupId = localGroupId,
                createdAt = 1_100_001L,
                syncState = SyncState.LOCAL_ONLY,
            )
        )

        val fullSettings = BackupSettings(
            customCategories = listOf("Trek Supplies"),
            hiddenCategories = listOf("Gambling"),
            customIncomeCategories = listOf("Freelance Dev"),
            customPaymentMethods = listOf("Corporate Card"),
            hiddenPaymentMethods = listOf("Crypto"),
            askBeforeDiscarding = true,
        )

        val backup = LocalBackupRepository(source).collect(appVersion = "1.6.0", settings = fullSettings)

        // --- Verify Settings round trip ---
        assertEquals(fullSettings, backup.settings)

        val decoded = LocalBackup.decode(copyContext, LocalBackup.encode(backup))
        assertTrue("The exported backup file must decode successfully", decoded is BackupReadResult.Ok)
        val decodedBackup = (decoded as BackupReadResult.Ok).backup
        assertEquals(fullSettings, decodedBackup.settings)
        assertEquals("Serialization must preserve every supported backup field", backup, decodedBackup)

        // --- Restore the file's decoded contents into target ---
        val report = LocalBackupRepository(target).restore(decodedBackup)
        assertEquals(0, report.skipped)
        assertTrue(report.added > 0)

        // --- 1. PersonEntity column assertions ---
        val restoredPerson = target.personDao().getAllPeopleDirect().single { it.name == "Aarav" }
        assertEquals("Aarav", restoredPerson.name)
        assertEquals("#4A90E2", restoredPerson.avatarColor)
        assertEquals("⭐", restoredPerson.avatarEmoji)
        assertEquals(100_001L, restoredPerson.createdAt)

        // --- 2. GroupEntity & GroupMemberEntity column assertions ---
        val restoredGroup = target.groupDao().getAllGroupsDirect().single { it.name == "Weekend Trek" }
        assertEquals("Weekend Trek", restoredGroup.name)
        assertEquals("Western Ghats trail trip", restoredGroup.description)
        assertEquals("⛰️", restoredGroup.emoji)
        assertEquals("#2ECC71", restoredGroup.colorHex)
        assertEquals(200_001L, restoredGroup.createdAt)
        assertEquals(300_001L, restoredGroup.archivedAt)
        assertEquals(SyncState.LOCAL_ONLY, restoredGroup.syncState)
        assertEquals(false, restoredGroup.isShared)

        val restoredMembers = target.groupDao().getMembersForGroupDirect(restoredGroup.id)
        assertEquals(2, restoredMembers.size)
        assertEquals(setOf(me, restoredPerson.id), restoredMembers.map { it.personId }.toSet())

        // --- 3. SubscriptionEntity column assertions ---
        val restoredSub = target.subscriptionDao().getAllDirect().single { it.name == "Cloud Backup Service" }
        assertEquals("Cloud Backup Service", restoredSub.name)
        assertEquals(299.0, restoredSub.amount, 0.0)
        assertEquals("Bills", restoredSub.category)
        assertEquals("PopUPI", restoredSub.paymentMethod)
        assertEquals("MONTHLY", restoredSub.cycle)
        assertEquals(400_001L, restoredSub.startDate)
        assertEquals(500_001L, restoredSub.chargedThrough)
        assertEquals(600_001L, restoredSub.cancelledAt)
        assertEquals(700_001L, restoredSub.createdAt)

        // --- 4. TransactionEntity (Direct) column assertions ---
        val restoredDirectTx = target.transactionDao().getAllTransactionsDirect().single { it.remoteId == "uuid-tx-direct-1" }
        assertEquals("expense", restoredDirectTx.type)
        assertEquals(350.0, restoredDirectTx.amount, 0.0)
        assertEquals("Food", restoredDirectTx.category)
        assertEquals("Dhaba Express", restoredDirectTx.merchant)
        assertEquals("Cash", restoredDirectTx.paymentMethod)
        assertEquals(800_001L, restoredDirectTx.date)
        assertEquals("Breakfast on highway", restoredDirectTx.note)
        assertEquals(false, restoredDirectTx.isSplit)
        assertEquals(restoredGroup.id, restoredDirectTx.groupId)
        assertEquals(800_001L, restoredDirectTx.createdAt)
        assertEquals(SyncState.LOCAL_ONLY, restoredDirectTx.syncState)

        // --- 5. TransactionEntity (Split) column assertions ---
        val restoredSplitTx = target.transactionDao().getAllTransactionsDirect().single { it.remoteId == "uuid-tx-split-2" }
        assertEquals("expense", restoredSplitTx.type)
        assertEquals(1200.0, restoredSplitTx.amount, 0.0)
        assertEquals("Transport", restoredSplitTx.category)
        assertEquals("Jeep Rental", restoredSplitTx.merchant)
        assertEquals("Bank Transfer", restoredSplitTx.paymentMethod)
        assertEquals(900_001L, restoredSplitTx.date)
        assertEquals("Trail head transfer", restoredSplitTx.note)
        assertEquals(true, restoredSplitTx.isSplit)
        assertEquals(restoredGroup.id, restoredSplitTx.groupId)
        assertEquals(900_001L, restoredSplitTx.createdAt)
        assertEquals(SyncState.LOCAL_ONLY, restoredSplitTx.syncState)

        // --- 6. TransactionEntity (Income) column assertions ---
        val restoredIncomeTx = target.transactionDao().getAllTransactionsDirect().single { it.remoteId == "uuid-tx-income-3" }
        assertEquals("income", restoredIncomeTx.type)
        assertEquals(5000.0, restoredIncomeTx.amount, 0.0)
        assertEquals("Income (Client)", restoredIncomeTx.category)
        assertEquals("Consulting Stipend", restoredIncomeTx.merchant)
        assertEquals("Bank Transfer", restoredIncomeTx.paymentMethod)
        assertEquals(1_200_001L, restoredIncomeTx.date)
        assertEquals("Project milestone 1", restoredIncomeTx.note)
        assertEquals(false, restoredIncomeTx.isSplit)
        assertEquals(null, restoredIncomeTx.groupId)
        assertEquals(1_200_001L, restoredIncomeTx.createdAt)
        assertEquals(SyncState.LOCAL_ONLY, restoredIncomeTx.syncState)

        // --- 7. SplitEntity column assertions ---
        val restoredSplit = target.splitDao().getAllSplitsDirect().single { it.transactionId == restoredSplitTx.id }
        assertEquals(1200.0, restoredSplit.totalAmount, 0.0)
        assertEquals(restoredPerson.id, restoredSplit.paidByPersonId)
        assertEquals("exact", restoredSplit.splitType)

        // --- 8. SplitShareEntity column assertions ---
        val restoredShares = target.splitDao().getSharesForSplitDirect(restoredSplit.id).associateBy { it.personId }
        val myShare = restoredShares.getValue(me)
        assertEquals(400.0, myShare.shareAmount, 0.0)
        assertEquals(33.33, myShare.percentage ?: 0.0, 0.001)

        val theirShare = restoredShares.getValue(restoredPerson.id)
        assertEquals(800.0, theirShare.shareAmount, 0.0)
        assertEquals(66.67, theirShare.percentage ?: 0.0, 0.001)

        // --- 9. SettlementEntity (IOU & Settle) column assertions ---
        val restoredIou = target.settlementDao().getAllSettlementsDirect().single { it.isIou }
        assertEquals(restoredPerson.id, restoredIou.fromPersonId)
        assertEquals(me, restoredIou.toPersonId)
        assertEquals(150.0, restoredIou.amount, 0.0)
        assertEquals(1_000_001L, restoredIou.date)
        assertEquals("Cash borrowed for toll plaza", restoredIou.note)
        assertEquals(true, restoredIou.isIou)
        assertEquals(restoredGroup.id, restoredIou.groupId)
        assertEquals(1_000_001L, restoredIou.createdAt)
        assertEquals(SyncState.LOCAL_ONLY, restoredIou.syncState)

        val restoredSettle = target.settlementDao().getAllSettlementsDirect().single { !it.isIou }
        assertEquals(me, restoredSettle.fromPersonId)
        assertEquals(restoredPerson.id, restoredSettle.toPersonId)
        assertEquals(50.0, restoredSettle.amount, 0.0)
        assertEquals(1_100_001L, restoredSettle.date)
        assertEquals("Returned change", restoredSettle.note)
        assertEquals(false, restoredSettle.isIou)
        assertEquals(restoredGroup.id, restoredSettle.groupId)
        assertEquals(1_100_001L, restoredSettle.createdAt)
        assertEquals(SyncState.LOCAL_ONLY, restoredSettle.syncState)
    }

    private suspend fun balanceOf(db: AppDatabase): Double {
        val people = db.personDao().getAllPeopleDirect()
        val friend = people.single { it.name == "sample_frnd" }
        var net = 0.0

        for (split in db.splitDao().getAllSplitsDirect()) {
            val shares = db.splitDao().getSharesForSplitDirect(split.id)
            val theirShare = shares.firstOrNull { it.personId == friend.id }?.shareAmount ?: 0.0
            val myShare = shares.firstOrNull { it.personId == me }?.shareAmount ?: 0.0
            when (split.paidByPersonId) {
                me -> net += theirShare
                friend.id -> net -= myShare
            }
        }
        for (settlement in db.settlementDao().getAllSettlementsDirect()) {
            when {
                settlement.fromPersonId == friend.id && settlement.toPersonId == me ->
                    net += if (settlement.isIou) settlement.amount else -settlement.amount
                settlement.fromPersonId == me && settlement.toPersonId == friend.id ->
                    net -= if (settlement.isIou) settlement.amount else -settlement.amount
            }
        }
        return net
    }
}
