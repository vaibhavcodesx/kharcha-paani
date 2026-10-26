package com.vaibhav.kharchapaani.ui.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.receipts.ReceiptStore
import com.vaibhav.kharchapaani.data.settings.AppSettings
import com.vaibhav.kharchapaani.domain.UpiPayee
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** After the server deletes an account, nothing of it is left on the phone (plan T1.1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountDeletionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val deleted = "aaaaaaaa-1111-4000-8000-000000000001"
    private val other = "bbbbbbbb-2222-4000-8000-000000000002"

    @Test
    fun `the deleted account's ledger, photos and settings go, and another account's stay`() = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val settings = AppSettings(context)
        for (account in listOf(other, deleted)) {
            LedgerDatabases.open(context, account).transactionDao().getAllTransactionsDirect()
            File(ReceiptStore.forAccount(context, account).dir, "bill.jpg").apply { parentFile!!.mkdirs(); writeText("jpeg") }
            settings.setUpiPayee(account, UpiPayee("someone@okaxis", "Someone"))
        }

        AccountDeletion.wipeLocal(context, deleted)

        assertFalse("the ledger file is still there", context.getDatabasePath(LedgerDatabases.databaseName(deleted)).exists())
        assertFalse("the bill photos are still there", ReceiptStore.forAccount(context, deleted).dir.exists())
        assertNull(settings.upiPayee(deleted).first())

        assertTrue(context.getDatabasePath(LedgerDatabases.databaseName(other)).exists())
        assertTrue(File(ReceiptStore.forAccount(context, other).dir, "bill.jpg").exists())
        assertEquals("Someone", settings.upiPayee(other).first()?.name)
    }
}
