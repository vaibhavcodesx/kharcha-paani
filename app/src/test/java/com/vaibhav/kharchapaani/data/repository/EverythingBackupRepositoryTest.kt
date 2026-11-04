package com.vaibhav.kharchapaani.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Start with actual v10 rows, serialize, restore, then query every column and real balance flow. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EverythingBackupRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val directory = File(context.cacheDir, "everything-backup-${java.util.UUID.randomUUID()}").apply { check(mkdirs()) }
    private val sourcePhotos = ReceiptStore(File(directory, "source").apply { mkdirs() })
    private val targetPhotos = ReceiptStore(File(directory, "target").apply { mkdirs() })
    private val exporter = EverythingBackupRepository(source, sourcePhotos)
    private val importer = EverythingBackupRepository(target, targetPhotos)
    private val settings = EverythingSettings(
        customExpenseCategories = listOf("Groceries"), customIncomeCategories = listOf("Freelance"),
        hiddenCategories = listOf("Food"), customPaymentMethods = listOf("My card"), hiddenPaymentMethods = listOf("Cash"),
        askBeforeDiscarding = false, reminderEnabled = false, reminderMinute = 720,
        paymentsToConfirm = false, quietHoursEnabled = false, quietHoursStart = 100, quietHoursEnd = 200,
        groupSort = "Name", upiId = "sample@upi", upiName = "Sample", aiEnabled = false,
    )

    @After fun close() {
        source.close()
        target.close()
        directory.deleteRecursively()
    }

    private suspend fun seed() {
        source.personDao().insertPerson(PersonEntity(11, "Same name", "#123456", 101, "profile-a", "https://example.invalid/a", "A", "sample@upi", 123))
        source.personDao().insertPerson(PersonEntity(22, "Same name", "#654321", 102, null, null, null, null, null))
        source.groupSyncDao().insertGroup(GroupEntity(31, "Same group", "description", "G", "#112233", 103, "group-a", 203, SyncState.SYNCED, 303, "profile-a"))
        source.groupSyncDao().insertGroup(GroupEntity(32, "Same group", "second", "H", "#223344", 104, "group-b", 204, SyncState.LOCAL_ONLY, null, null))
        source.groupSyncDao().insertMembers(listOf(GroupMemberEntity(41, 31, -1), GroupMemberEntity(42, 31, 11), GroupMemberEntity(43, 32, 22)))
        source.transactionDao().insertTransaction(TransactionEntity(51, "expense", 100.0, "Food", "Same purchase", "Cash", 1000, "edited note", true, 31, 105, "tx-a", 205, SyncState.FAILED, "profile-a"))
        source.transactionDao().insertTransaction(TransactionEntity(52, "expense", 100.0, "Food", "Same purchase", "Cash", 1000, "second distinct purchase", false, null, 106, "tx-b", 206, SyncState.PENDING, null))
        source.transactionDao().insertTransaction(TransactionEntity(53, "income", 200.0, "Salary", "Salary", "Bank Transfer", 1001, "income", false, 32, 107, "tx-c", 207, SyncState.SYNCED, null))
        source.groupSyncDao().insertSplit(SplitEntity(61, 51, 100.0, 11, "percentage", "split-a"))
        source.groupSyncDao().insertShares(listOf(
            SplitShareEntity(71, 61, -1, 50.0, 50.0, null, null),
            SplitShareEntity(72, 61, 11, 20.0, 20.0, "Guest", 22),
            SplitShareEntity(73, 61, 22, 30.0, null, null, null),
        ))
        source.groupSyncDao().insertSettlement(SettlementEntity(81, 11, -1, 5.0, 1002, "pending edit", false, 31, 108, "settlement-a", 208, SyncState.PENDING, SettlementStatus.PENDING, 11, 308, "answer note", SettlementStatus.REJECTED, 2))
        source.groupSyncDao().insertSettlement(SettlementEntity(82, -1, 22, 7.0, 1003, "IOU", true, null, 109, "settlement-b", 209, SyncState.LOCAL_ONLY, SettlementStatus.CONFIRMED, null, null, null, null, -1))
        source.groupSyncDao().insertSettlement(SettlementEntity(83, 22, -1, 9.0, 1004, "rejected", false, 32, 110, "settlement-c", 210, SyncState.FAILED, SettlementStatus.REJECTED, -1, 310, "no", null, 1))
        source.subscriptionDao().insert(SubscriptionEntity(91, "Same subscription", 50.0, "Bills", "My card", "MONTHLY", 111, 211, 311, 411))
        source.subscriptionDao().insert(SubscriptionEntity(92, "Same subscription", 50.0, "Bills", "My card", "MONTHLY", 111, null, null, 412))
        source.paymentAskDao().insert(PaymentAskEntity(101, 11, 12.5, 112))
        source.paymentAskDao().insert(PaymentAskEntity(102, 22, 15.0, 113))
        val bytes = byteArrayOf(1, 3, 5, 7)
        File(sourcePhotos.dir, "bill-a.jpg").writeBytes(bytes)
        File(sourcePhotos.dir, "bill-b.jpg").writeBytes(bytes.reversedArray())
        source.receiptDao().insert(ReceiptEntity(111, 51, "bill-a.jpg", 100, 200, 4, 114, "historic/path"))
        source.receiptDao().insert(ReceiptEntity(112, 52, "bill-b.jpg", 200, 100, 4, 115, null))
        source.syncDao().putMeta(SyncMetaEntity("cursor", "not exported"))
        source.syncDao().insertPendingDelete(PendingRemoteDeleteEntity("deleted-remote", 116, "transaction"))
    }

    @Test fun `full portable round trip preserves every column except network queues and sync state`() = runBlocking {
        seed()
        val gathered = exporter.collect("1.7-test", settings)
        val decoded = EverythingBackup.decode(EverythingBackup.encode(gathered))
        assertEquals(gathered, decoded)
        assertEquals(settings, importer.restoreIntoEmptyLocalLedger(decoded))
        assertEquals(source.personDao().getAllPeopleDirect().sortedBy { it.id }, target.personDao().getAllPeopleDirect().sortedBy { it.id })
        assertEquals(source.groupDao().getAllGroupsDirect().map { it.copy(syncState = if (it.id == 31L) SyncState.SYNCED else SyncState.LOCAL_ONLY) }.sortedBy { it.id }, target.groupDao().getAllGroupsDirect().sortedBy { it.id })
        assertEquals(source.groupDao().getAllGroupMembersDirect().sortedBy { it.id }, target.groupDao().getAllGroupMembersDirect().sortedBy { it.id })
        assertEquals(source.transactionDao().getAllTransactionsDirect().map { it.copy(syncState = SyncState.LOCAL_ONLY) }.sortedBy { it.id }, target.transactionDao().getAllTransactionsDirect().sortedBy { it.id })
        assertEquals(source.splitDao().getAllSplitsDirect().sortedBy { it.id }, target.splitDao().getAllSplitsDirect().sortedBy { it.id })
        assertEquals(source.splitDao().getAllSplitSharesDirect().sortedBy { it.id }, target.splitDao().getAllSplitSharesDirect().sortedBy { it.id })
        assertEquals(source.settlementDao().getAllSettlementsDirect().map { it.copy(syncState = SyncState.LOCAL_ONLY, pendingResponse = null) }.sortedBy { it.id }, target.settlementDao().getAllSettlementsDirect().sortedBy { it.id })
        assertEquals(source.subscriptionDao().getAllDirect().sortedBy { it.id }, target.subscriptionDao().getAllDirect().sortedBy { it.id })
        assertEquals(source.paymentAskDao().observeAll().first(), target.paymentAskDao().observeAll().first())
        for (id in listOf(51L, 52L)) assertEquals(source.receiptDao().forTransaction(id), target.receiptDao().forTransaction(id))
        for (name in listOf("bill-a.jpg", "bill-b.jpg")) assertArrayEquals(File(sourcePhotos.dir, name).readBytes(), File(targetPhotos.dir, name).readBytes())
        assertEquals(LedgerRepository(source).personBalances.first().sortedBy { it.person.id }, LedgerRepository(target).personBalances.first().sortedBy { it.person.id })
        assertEquals(0, target.syncDao().observeUnsyncedChangeCount().first())
        assertNull(target.syncDao().meta("cursor"))
        assertTrue(target.syncDao().pendingDeletes(100).isEmpty())
        assertEquals(listOf(31L), target.groupDao().getAllGroupsDirect().filter { it.isShared }.map { it.id })
        // IDs, not names or purchase shapes, retain two legitimate equal-looking rows.
        assertEquals(listOf(11L, 22L), target.personDao().getAllPeopleDirect().map { it.id }.sorted())
        assertEquals(3, target.transactionDao().getAllTransactionsDirect().size)
        assertEquals(2, target.subscriptionDao().getAllDirect().size)
    }

    private suspend fun reject(backup: EverythingBackup) {
        try {
            importer.restoreIntoEmptyLocalLedger(backup)
            fail("Malformed backup was accepted")
        } catch (expected: IllegalArgumentException) {
            assertTrue(target.transactionDao().getAllTransactionsDirect().isEmpty())
            assertTrue(target.personDao().getAllPeopleDirect().isEmpty())
            assertTrue(targetPhotos.dir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun `invalid references identities and money fail before writes`() = runBlocking {
        seed()
        val good = exporter.collect("test")
        reject(good.copy(people = good.people + good.people.first()))
        reject(good.copy(members = good.members.map { it.copy(groupId = 9999) }))
        reject(good.copy(shares = good.shares.map { it.copy(splitId = 9999) }))
        reject(good.copy(transactions = good.transactions.map { it.copy(amount = Double.NaN) }))
        reject(good.copy(settlements = good.settlements.map { it.copy(toPersonId = 9999) }))
        reject(good.copy(splits = good.splits.map { it.copy(paidByPersonId = 9999) }))
        reject(good.copy(shares = good.shares.map { it.copy(shareAmount = Double.POSITIVE_INFINITY) }))
        reject(good.copy(subscriptions = good.subscriptions.map { it.copy(amount = -1.0) }))
        reject(good.copy(version = 2))
    }

    @Test fun `receipt traversal corruption and bounds fail before writes`() = runBlocking {
        seed()
        val good = exporter.collect("test")
        for (name in listOf("../escape.jpg", "..\\escape.jpg", "/absolute.jpg", "C:\\absolute.jpg")) {
            reject(good.copy(receipts = good.receipts.take(1).map { it.copy(fileName = name) }))
        }
        reject(good.copy(receipts = good.receipts.map { it.copy(contentBase64 = "not base64!") }))
        reject(good.copy(receipts = good.receipts.map { it.copy(bytes = 5) }))
        reject(good.copy(receipts = good.receipts.map { it.copy(bytes = EverythingBackup.MAX_PHOTO_BYTES + 1L) }))
        reject(good.copy(receipts = good.receipts.map { it.copy(transactionId = 9999) }))
    }

    @Test fun `restore refuses existing ledger and leaves its rows and files alone`() = runBlocking {
        seed()
        val row = PersonEntity(id = 999, name = "Keep me", avatarColor = "#123456")
        target.personDao().insertPerson(row)
        val existing = File(targetPhotos.dir, "keep.jpg").apply { writeBytes(byteArrayOf(9)) }
        try { importer.restoreIntoEmptyLocalLedger(exporter.collect("test")); fail("Nonempty target accepted") }
        catch (expected: IllegalArgumentException) {
            assertEquals(listOf(row), target.personDao().getAllPeopleDirect())
            assertTrue(target.transactionDao().getAllTransactionsDirect().isEmpty())
            assertArrayEquals(byteArrayOf(9), existing.readBytes())
        }
    }

    @Test fun `receipt collision rolls back database and removes only newly created files`() = runBlocking {
        seed()
        val existing = File(targetPhotos.dir, "bill-b.jpg").apply { writeBytes(byteArrayOf(9)) }
        val archive = exporter.collect("test").let { it.copy(receipts = it.receipts.sortedBy { row -> row.fileName }) }
        try { importer.restoreIntoEmptyLocalLedger(archive); fail("Receipt overwrite accepted") }
        catch (expected: IllegalArgumentException) {
            assertTrue(target.personDao().getAllPeopleDirect().isEmpty())
            assertTrue(target.transactionDao().getAllTransactionsDirect().isEmpty())
            assertFalse(File(targetPhotos.dir, "bill-a.jpg").exists())
            assertArrayEquals(byteArrayOf(9), existing.readBytes())
        }
    }

    @Test fun `Room insertion failure rolls back rows and newly written receipts`() = runBlocking {
        seed()
        val archive = exporter.collect("test")
        target.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_restore BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'fixture rejection'); END"
        )
        try { importer.restoreIntoEmptyLocalLedger(archive); fail("Trigger did not reject insertion") }
        catch (expected: Exception) {
            assertTrue(target.personDao().getAllPeopleDirect().isEmpty())
            assertTrue(target.groupDao().getAllGroupsDirect().isEmpty())
            assertTrue(target.transactionDao().getAllTransactionsDirect().isEmpty())
            assertTrue(targetPhotos.dir.listFiles().orEmpty().isEmpty())
        }
    }

    @Test fun `decode rejects malformed JSON and unsupported archive identity`() {
        for (text in listOf("not json", "{}", "{\"format\":\"other\",\"exportedAt\":1,\"appVersion\":\"x\"}")) {
            try { EverythingBackup.decode(text); fail("Invalid JSON archive accepted") }
            catch (expected: IllegalArgumentException) { /* No database operation was attempted. */ }
        }
    }


    private suspend fun roundTripAfterDeleting(personId: Long) {
        seed()
        // Real local-friend deletion can leave the payer/guest reference in surviving history.
        val person = source.personDao().getPersonById(personId)!!
        source.personDao().updatePerson(person.copy(profileId = null))
        val group = source.groupDao().getGroupByIdDirect(31)!!
        source.groupDao().updateGroup(group.copy(syncState = SyncState.LOCAL_ONLY))
        LedgerRepository(source, localFriendSync = false).deletePerson(personId)
        assertNull(source.personDao().getPersonById(personId))
        val archive = EverythingBackup.decode(EverythingBackup.encode(exporter.collect("test")))
        assertTrue(archive.missingPersonIds.contains(personId))
        importer.restoreIntoEmptyLocalLedger(archive)
        assertNull(target.personDao().getPersonById(personId))
        assertEquals(source.splitDao().getAllSplitsDirect(), target.splitDao().getAllSplitsDirect())
        assertEquals(source.splitDao().getAllSplitSharesDirect().sortedBy { it.id }, target.splitDao().getAllSplitSharesDirect().sortedBy { it.id })
        assertEquals(LedgerRepository(source).personBalances.first().sortedBy { it.person.id }, LedgerRepository(target).personBalances.first().sortedBy { it.person.id })
    }

    @Test fun `deleted payer is retained as historical reference without inventing or flipping payer`() = runBlocking {
        roundTripAfterDeleting(11)
        assertEquals(11L, target.splitDao().getAllSplitsDirect().single().paidByPersonId)
    }

    @Test fun `deleted guest is retained in surviving host share without inventing a friend`() = runBlocking {
        roundTripAfterDeleting(22)
        assertEquals(22L, target.splitDao().getAllSplitSharesDirect().first { it.guestName == "Guest" }.guestPersonId!!)
    }

    @Test fun `shared legacy partial shares keep group totals and member balances after archive restore`() = runBlocking {
        source.personDao().insertPerson(PersonEntity(id = 11, name = "Registered friend", avatarColor = "#123456", profileId = "profile-a"))
        source.groupSyncDao().insertGroup(GroupEntity(id = 31, name = "Shared history", syncState = SyncState.SYNCED))
        source.groupSyncDao().insertMembers(listOf(
            GroupMemberEntity(id = 41, groupId = 31, personId = -1),
            GroupMemberEntity(id = 42, groupId = 31, personId = 11),
        ))
        source.transactionDao().insertTransaction(TransactionEntity(
            id = 51, type = "expense", amount = 100.0, category = "Food", merchant = "Dinner",
            paymentMethod = "Cash", isSplit = true, groupId = 31, syncState = SyncState.SYNCED,
        ))
        source.groupSyncDao().insertSplit(SplitEntity(id = 61, transactionId = 51, totalAmount = 100.0, paidByPersonId = -1))
        // A legacy ledger may retain only surviving shares. Never manufacture the missing Rs 30.
        source.groupSyncDao().insertShares(listOf(
            SplitShareEntity(id = 71, splitId = 61, personId = -1, shareAmount = 50.0),
            SplitShareEntity(id = 72, splitId = 61, personId = 11, shareAmount = 20.0),
        ))
        val before = LedgerRepository(source).groupsWithDetails.first().single()
        val expectedPositions = mapOf(-1L to 20.0, 11L to -20.0)
        assertEquals(100.0, before.totalExpenses, 0.0)
        assertEquals(100.0, before.myPaidAmount, 0.0)
        assertEquals(50.0, before.myShare, 0.0)
        assertEquals(20.0, before.netBalanceForMe, 0.0)
        assertEquals(expectedPositions, GroupBalances.netPositions(before.transactions, before.settlements))

        val archive = EverythingBackup.decode(EverythingBackup.encode(exporter.collect("test")))
        assertEquals(70.0, archive.shares.sumOf { it.shareAmount }, 0.0)
        importer.restoreIntoEmptyLocalLedger(archive)
        val after = LedgerRepository(target).groupsWithDetails.first().single()
        assertEquals(before.totalExpenses, after.totalExpenses, 0.0)
        assertEquals(before.myPaidAmount, after.myPaidAmount, 0.0)
        assertEquals(before.myShare, after.myShare, 0.0)
        assertEquals(expectedPositions, GroupBalances.netPositions(after.transactions, after.settlements))
        assertEquals(LedgerRepository(source).personBalances.first(), LedgerRepository(target).personBalances.first())
        assertEquals("Restoring shared history must preserve the displayed group balance", before.netBalanceForMe, after.netBalanceForMe, 0.0)
    }

}
