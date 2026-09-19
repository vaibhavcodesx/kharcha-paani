package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup file's shape and the promises made about it.
 *
 * This exists because of a verified fact and a corrected mistake. The fact: a normal app update
 * keeps local-only data — installing v1.0.1 over the published v1.0.0 on 2026-09-19 left a local
 * friend, a subscription and a custom category untouched, with `firstInstallTime` unchanged. The
 * mistake: a doc once claimed local friends were backed up to the server. They are not, and there
 * is no `people` table for them to live in, which is the whole reason this file format exists.
 */
class LocalBackupTest {

    private val person = BackupPerson(name = "Offline_Friend", avatarColor = "#F2D06B", createdAt = 1_000L)

    private fun backup(
        people: List<BackupPerson> = listOf(person),
        subscriptions: List<BackupSubscription> = emptyList(),
        settlements: List<BackupSettlement> = emptyList(),
        transactions: List<BackupTransaction> = emptyList(),
        settings: BackupSettings = BackupSettings(),
    ) = LocalBackup(
        exportedAt = 1_758_000_000_000L,
        appVersion = "1.1.0",
        people = people,
        subscriptions = subscriptions,
        settlements = settlements,
        transactions = transactions,
        settings = settings,
    )

    // --- Round trip

    @Test
    fun `a backup survives being written and read back`() {
        val original = backup(
            subscriptions = listOf(
                BackupSubscription(
                    name = "SAMPLE SUB", amount = 67.0, category = "Bills", paymentMethod = "PopUPI",
                    cycle = "MONTHLY", startDate = 500L, createdAt = 400L,
                )
            ),
            settlements = listOf(
                BackupSettlement(fromName = "Offline_Friend", toName = null, amount = 25.0, date = 900L, createdAt = 900L)
            ),
            transactions = listOf(
                BackupTransaction(
                    remoteId = "abc", type = "expense", amount = 500.0, category = "Food",
                    merchant = "Dinner", paymentMethod = "Cash", date = 2_000L, createdAt = 2_000L,
                    split = BackupSplit(
                        totalAmount = 500.0, splitType = "equal", paidByName = "Offline_Friend",
                        shares = listOf(BackupShare(null, 250.0), BackupShare("Offline_Friend", 250.0)),
                    ),
                )
            ),
            settings = BackupSettings(customCategories = listOf("Sample_Category"), askBeforeDiscarding = true),
        )

        val decoded = LocalBackup.decode(LocalBackup.encode(original))

        assertTrue(decoded is BackupReadResult.Ok)
        assertEquals(original, (decoded as BackupReadResult.Ok).backup)
    }

    @Test
    fun `a settlement with you on one side round-trips with a null name`() {
        // "You" is personId -1 in the ledger, which is not a row in `people` and so has no name.
        // Writing it as null rather than as "You" keeps it from colliding with a friend called You.
        val settlement = BackupSettlement(fromName = "Offline_Friend", toName = null, amount = 25.0, date = 1L, createdAt = 1L)

        val decoded = LocalBackup.decode(LocalBackup.encode(backup(settlements = listOf(settlement))))

        assertEquals(null, ((decoded as BackupReadResult.Ok).backup).settlements.single().toName)
    }

    // --- Bad input

    @Test
    fun `something that isn't a backup is reported, not thrown`() {
        // The one input here chosen entirely by hand from a file picker.
        assertEquals(BackupReadResult.Unreadable, LocalBackup.decode("not json at all"))
        assertEquals(BackupReadResult.Unreadable, LocalBackup.decode(""))
        assertEquals(BackupReadResult.Unreadable, LocalBackup.decode("""{"unrelated":"object"}"""))
    }

    @Test
    fun `a backup from a newer app says so rather than importing half of it`() {
        val future = LocalBackup.encode(backup()).replace(
            "\"version\": ${LocalBackup.FORMAT_VERSION}",
            "\"version\": ${LocalBackup.FORMAT_VERSION + 1}",
        )

        val result = LocalBackup.decode(future)

        assertTrue(result is BackupReadResult.TooNew)
        assertEquals(LocalBackup.FORMAT_VERSION + 1, (result as BackupReadResult.TooNew).version)
    }

    @Test
    fun `a field added by a later version doesn't make an older app reject the file`() {
        val extended = LocalBackup.encode(backup()).replaceFirst("{", """{"something_new": 5,""")

        assertTrue(LocalBackup.decode(extended) is BackupReadResult.Ok)
    }

    // --- Emptiness

    @Test
    fun `a phone with nothing local-only produces an empty backup`() {
        // Offering to write a file of nothing would imply there was something to lose.
        assertTrue(LocalBackup(exportedAt = 1L, appVersion = "1.0.0").isEmpty)
    }

    @Test
    fun `settings alone still count as worth backing up`() {
        assertFalse(backup(people = emptyList(), settings = BackupSettings(customCategories = listOf("Chai"))).isEmpty)
    }

    // --- What the person is told

    @Test
    fun `the summary counts what is actually in the file`() {
        val summary = backup(
            subscriptions = listOf(
                BackupSubscription(name = "S", amount = 1.0, category = "Bills", paymentMethod = "Cash", cycle = "MONTHLY", startDate = 1L, createdAt = 1L)
            ),
        ).summary()

        assertEquals("1 friend, 1 subscription", summary)
    }

    @Test
    fun `a v1 file still restores, it simply has little in it`() {
        // v1 shipped with an always-empty splits array. Refusing to read those files would strip
        // people of the friends and subscriptions v1 *did* capture.
        val v1 = """{"version":1,"exported_at":1,"app_version":"1.0.0","people":[],"splits":[]}"""

        assertTrue(LocalBackup.decode(v1) is BackupReadResult.Ok)
    }

    @Test
    fun `a restore that found everything already there says so`() {
        // The important case: running a restore twice should visibly do nothing the second time,
        // not report a success that leaves you wondering whether it duplicated the lot.
        val report = RestoreReport(peopleSkipped = 2, subscriptionsSkipped = 1)

        assertEquals("Everything in that backup was already here. Nothing changed.", report.message())
    }

    @Test
    fun `a partial restore reports both halves`() {
        val report = RestoreReport(peopleAdded = 1, subscriptionsSkipped = 2)

        assertEquals("Restored 1 item; 2 were already here.", report.message())
    }

    @Test
    fun `the file name carries the date it was taken`() {
        // So two backups in a downloads folder are told apart without opening them.
        assertTrue(LocalBackup.fileName(1_758_000_000_000L).startsWith("kharcha-paani-backup-2025-"))
        assertTrue(LocalBackup.fileName(1_758_000_000_000L).endsWith(".json"))
    }
}
