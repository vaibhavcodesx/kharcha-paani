package com.vaibhav.kharchapaani.domain

import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GroupExpenseCreditTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val priya = PersonEntity(id = 2, name = "Priya", avatarColor = "#fff", profileId = "priya")
    private val rohan = PersonEntity(id = 3, name = "Rohan", avatarColor = "#fff", profileId = "rohan")
    private val members = listOf(priya, rohan)

    private fun expense(addedBy: String?, paidBy: Long?) = FullTransaction(
        TransactionEntity(id = 1, type = "expense", amount = 900.0, category = "Food", merchant = "Dinner", paymentMethod = "UPI", date = 0, isSplit = paidBy != null, ownerProfileId = addedBy),
        split = paidBy?.let { SplitEntity(id = 1, transactionId = 1, totalAmount = 900.0, paidByPersonId = it) },
    )

    @Test
    fun `someone who added but didn't pay shows who paid too`() {
        assertEquals("Added by Priya · Paid by Rohan", GroupExpenseCredit.line(context, expense(addedBy = "priya", paidBy = 3), members))
    }

    @Test
    fun `the payer alone when they're also who added it`() {
        assertEquals("Paid by Priya", GroupExpenseCredit.line(context, expense(addedBy = "priya", paidBy = 2), members))
        assertEquals("You paid", GroupExpenseCredit.line(context, expense(addedBy = null, paidBy = -1), members))
    }

    @Test
    fun `you appear as you on either side`() {
        assertEquals("Added by Priya · Paid by you", GroupExpenseCredit.line(context, expense(addedBy = "priya", paidBy = -1), members))
        assertEquals("Added by you · Paid by Rohan", GroupExpenseCredit.line(context, expense(addedBy = null, paidBy = 3), members))
    }

    @Test
    fun `an expense with no split was paid by whoever added it`() {
        assertEquals("Paid by Rohan", GroupExpenseCredit.line(context, expense(addedBy = "rohan", paidBy = null), members))
    }

    @Test
    fun `someone no longer in the group is a former member, not a blank`() {
        assertEquals("Added by a former member · Paid by Priya", GroupExpenseCredit.line(context, expense(addedBy = "gone", paidBy = 2), members))
    }
}
