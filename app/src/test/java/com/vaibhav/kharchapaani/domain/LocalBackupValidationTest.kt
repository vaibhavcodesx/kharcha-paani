package com.vaibhav.kharchapaani.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A backup that parses still has to hold numbers a ledger can take. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupValidationTest {
    private val copyContext = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun tx(amount: Double = 300.0, split: BackupSplit? = null, type: String = "expense") = BackupTransaction(
        type = type, amount = amount, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = 1L, createdAt = 1L, split = split,
    )

    private fun split(total: Double = 300.0, type: String = "equal", vararg shares: Double = doubleArrayOf(150.0, 150.0)) =
        BackupSplit(totalAmount = total, splitType = type, shares = shares.mapIndexed { i, a -> BackupShare(name = if (i == 0) null else "Rohan", amount = a) })

    private fun read(vararg transactions: BackupTransaction, settlements: List<BackupSettlement> = emptyList(), subscriptions: List<BackupSubscription> = emptyList()) =
        LocalBackup.decode(copyContext, LocalBackup.encode(LocalBackup(exportedAt = 1L, appVersion = "test", transactions = transactions.toList(), settlements = settlements, subscriptions = subscriptions)))

    private fun problem(result: BackupReadResult) = (result as BackupReadResult.Invalid).problem

    @Test
    fun `an ordinary backup reads`() {
        assertTrue(read(tx(), tx(split = split()), tx(split = split(100.0, "exact", 33.33, 33.33, 33.33))) is BackupReadResult.Ok)
    }

    @Test
    fun `a negative, zero or infinite amount is refused`() {
        assertEquals("an expense (Dinner) has no proper amount", problem(read(tx(amount = -5.0))))
        assertTrue(read(tx(amount = 0.0)) is BackupReadResult.Invalid)
        assertTrue(read(settlements = listOf(BackupSettlement(amount = -100.0, date = 1L, createdAt = 1L))) is BackupReadResult.Invalid)
        assertTrue(
            read(subscriptions = listOf(BackupSubscription(name = "Netflix", amount = -649.0, category = "Bills", paymentMethod = "Card", cycle = "MONTHLY", startDate = 1L, createdAt = 1L)))
                is BackupReadResult.Invalid
        )
    }

    @Test
    fun `an unknown split kind or subscription cycle is refused`() {
        assertEquals("the split of Dinner is of an unknown kind (shares)", problem(read(tx(split = split(type = "shares")))))
        // Any case: the app writes lowercase, older rows may not.
        assertTrue(read(tx(split = split(type = "EQUAL"))) is BackupReadResult.Ok)
        assertTrue(
            read(subscriptions = listOf(BackupSubscription(name = "Gym", amount = 999.0, category = "Health", paymentMethod = "Card", cycle = "HOURLY", startDate = 1L, createdAt = 1L)))
                is BackupReadResult.Invalid
        )
    }

    @Test
    fun `shares adding up to more than the total are refused, less is fine`() {
        assertEquals("the shares of Dinner add up to more than its total", problem(read(tx(split = split(300.0, "exact", 150.0, 9_000.0)))))
        assertTrue(read(tx(split = split(300.0, "exact", 150.0, 100.0))) is BackupReadResult.Ok)
        assertTrue(read(tx(split = split(300.0, "exact", 150.0, -150.0, 300.0))) is BackupReadResult.Invalid)
    }
}
