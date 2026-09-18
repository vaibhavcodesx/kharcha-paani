package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GroupDetailContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val priya = PersonEntity(id = 2, name = "Priya", avatarColor = "#70C1FF", profileId = "p")
    private val rohan = PersonEntity(id = 3, name = "Rohan", avatarColor = "#B5EAD7", profileId = "r")

    private fun expense(id: Long, amount: Double, paidBy: Long, shares: Map<Long, Double>, category: String) = FullTransaction(
        TransactionEntity(id = id, type = "expense", amount = amount, category = category, merchant = "m$id", paymentMethod = "UPI", date = id, groupId = 7, isSplit = true),
        split = SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = paidBy),
        shares = shares.map { (person, share) -> SplitShareEntity(splitId = id, personId = person, shareAmount = share) },
    )

    private val expenses = listOf(
        expense(1, 900.0, paidBy = -1L, shares = mapOf(-1L to 300.0, 2L to 300.0, 3L to 300.0), category = "Food"),
        expense(2, 600.0, paidBy = 2L, shares = mapOf(-1L to 300.0, 2L to 300.0), category = "Transport"),
    )

    private var settledWith: Long? = null
    private var exported: Long? = null

    private fun show(archived: Boolean = false, settlements: List<SettlementEntity> = emptyList()) {
        val details = GroupWithDetails(
            group = GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️", syncState = SyncState.SYNCED, archivedAt = if (archived) 1L else null),
            members = listOf(priya, rohan),
            totalExpenses = 1500.0,
            myPaidAmount = 900.0,
            myShare = 600.0,
            netBalanceForMe = 300.0,
            transactionsCount = expenses.size,
            transactions = expenses,
            settlements = settlements,
        )
        composeTestRule.setContent {
            SplitFlowTheme {
                GroupDetailContent(
                    groupDetails = details,
                    onAddExpense = {},
                    onEditTransaction = {},
                    onSettleUpWith = { settledWith = it },
                    onExportGroup = { exported = it },
                )
            }
        }
    }

    @Test
    fun `it shows who spent what, who owes whom and where it went`() {
        show()

        composeTestRule.onNodeWithTag("member_share_donut").performScrollTo()
        composeTestRule.onNodeWithText("Rohan → You").performScrollTo()
        composeTestRule.onNodeWithText("Where it went").performScrollTo()
        // Food is the group's biggest category, at its full ₹900 rather than my ₹300 share.
        composeTestRule.onNodeWithTag("group_category_Food").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `a debt that includes you can be settled from its row`() {
        show()

        composeTestRule.onNodeWithTag("settle_3_-1").performScrollTo().performClick()

        assertEquals(3L, settledWith)
    }

    @Test
    fun `settled up, nobody owes anybody`() {
        show(settlements = listOf(SettlementEntity(id = 1, fromPersonId = 3, toPersonId = -1L, amount = 300.0, date = 0L, groupId = 7)))

        composeTestRule.onNodeWithText("Who owes whom").assertDoesNotExist()
    }

    @Test
    fun `the group exports from its own screen`() {
        show()

        // Activated the way TalkBack does: a tap at its centre can land under the floating action bar,
        // which the test's own scrolling doesn't account for.
        composeTestRule.onNodeWithTag("export_group_button").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(7L, exported)
    }

    @Test
    fun `an archived group says so and offers no settle up`() {
        show(archived = true)

        composeTestRule.onNodeWithTag("group_archived_banner").assertExists()
        composeTestRule.onNodeWithTag("settle_3_-1").assertDoesNotExist()
        composeTestRule.onNodeWithText("Archived groups are read-only").assertExists()
    }
}
