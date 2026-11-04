package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GroupDetailLazyTest {

    @get:Rule
    val compose = createComposeRule()

    private val priya = PersonEntity(id = 2, name = "Priya", avatarColor = "#70C1FF")
    private val rohan = PersonEntity(id = 3, name = "Rohan", avatarColor = "#B5EAD7")

    private fun expense(id: Long, amount: Double, paidBy: Long) = FullTransaction(
        TransactionEntity(
            id = id,
            type = "expense",
            amount = amount,
            category = "Food",
            merchant = "Cafe $id",
            paymentMethod = "UPI",
            date = 1700000000000L + (id * 1000L),
            groupId = 7,
            isSplit = true,
        ),
        split = SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = paidBy),
        shares = listOf(
            SplitShareEntity(splitId = id, personId = -1L, shareAmount = amount / 2),
            SplitShareEntity(splitId = id, personId = 2L, shareAmount = amount / 2),
        ),
    )

    @Test
    fun `long feed list renders with lazy column and scrolls to item by key`() {
        var addedExpenseGroupId: Long? = null
        val expenses = (1..60).map { i -> expense(i.toLong(), 200.0, paidBy = -1L) }
        val details = GroupWithDetails(
            group = GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️", syncState = SyncState.SYNCED),
            members = listOf(priya, rohan),
            totalExpenses = 12000.0,
            myPaidAmount = 12000.0,
            myShare = 6000.0,
            netBalanceForMe = 6000.0,
            transactionsCount = expenses.size,
            transactions = expenses,
            settlements = emptyList(),
        )

        compose.setContent {
            SplitFlowTheme {
                GroupDetailContent(
                    groupDetails = details,
                    onAddExpense = { addedExpenseGroupId = it },
                    onEditTransaction = {},
                    onSettleUpWith = {},
                    onExportGroup = {},
                )
            }
        }

        // Top group header and figures exist
        compose.onNodeWithText("Goa trip").assertExists()
        compose.onNodeWithText("Activity · 60").assertExists()
        compose.onNodeWithText("Cafe 1").assertExists()

        // Scroll to the 60th expense using scrollToKey
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("tx_60")
        compose.onNodeWithText("Cafe 60").assertExists()

        // Floating add button in action bar works
        compose.onNodeWithTag("add_group_expense_button").performClick()
        assertEquals(7L, addedExpenseGroupId)
    }

    @Test
    fun `empty feed renders empty state message inside lazy column`() {
        val details = GroupWithDetails(
            group = GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️", syncState = SyncState.SYNCED),
            members = listOf(priya, rohan),
            totalExpenses = 0.0,
            myPaidAmount = 0.0,
            myShare = 0.0,
            netBalanceForMe = 0.0,
            transactionsCount = 0,
            transactions = emptyList(),
            settlements = emptyList(),
        )

        compose.setContent {
            SplitFlowTheme {
                GroupDetailContent(
                    groupDetails = details,
                    onAddExpense = {},
                    onEditTransaction = {},
                )
            }
        }

        compose.onNodeWithText("Goa trip").assertExists()
        compose.onNodeWithText("Activity · 0").assertExists()
        compose.onNodeWithText("Nothing recorded yet.").assertExists()
    }
}
