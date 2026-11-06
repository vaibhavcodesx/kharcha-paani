package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.StatementMonthGroup
import com.vaibhav.kharchapaani.data.model.StatementParseResult
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MoneyAutoSizeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val user = PersonEntity(id = -1L, name = "You", avatarColor = "#FFB300")
    private val rohan = PersonEntity(id = 2L, name = "Rohan", avatarColor = "#70C1FF")

    @Test
    fun `group detail figures do not visually overflow at double font size`() {
        val totalAmount = 125000.0
        val myShareAmount = 62500.0
        val group = GroupEntity(id = 10, name = "Goa Trip", colorHex = "#FFB300")
        val tx = FullTransaction(
            transaction = TransactionEntity(
                id = 1,
                type = "expense",
                amount = totalAmount,
                category = "Travel",
                merchant = "Flight Booking",
                paymentMethod = "UPI",
                date = System.currentTimeMillis(),
                groupId = 10,
                isSplit = true,
            ),
            split = SplitEntity(id = 1, transactionId = 1, totalAmount = totalAmount, paidByPersonId = -1L),
            shares = listOf(
                SplitShareEntity(splitId = 1, personId = -1L, shareAmount = myShareAmount),
                SplitShareEntity(splitId = 1, personId = 2L, shareAmount = totalAmount - myShareAmount),
            ),
        )
        val details = GroupWithDetails(
            group = group,
            members = listOf(user, rohan),
            totalExpenses = totalAmount,
            myPaidAmount = totalAmount,
            myShare = myShareAmount,
            netBalanceForMe = totalAmount - myShareAmount,
            transactionsCount = 1,
            transactions = listOf(tx),
            settlements = emptyList(),
        )

        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2.0f)) {
                SplitFlowTheme {
                    GroupDetailContent(
                        groupDetails = details,
                        onAddExpense = {},
                        onEditTransaction = {},
                    )
                }
            }
        }

        val spentLayouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNode(hasText(Money.format(totalAmount)) and hasAnyAncestor(hasTestTag("group_figure_SPENT")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(spentLayouts) }

        assertFalse(
            "Group spent money figure must not visually overflow when font scale is 2.0",
            spentLayouts.first().hasVisualOverflow,
        )
        assertEquals(1, spentLayouts.first().lineCount)

        val shareLayouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNode(hasText(Money.format(myShareAmount)) and hasAnyAncestor(hasTestTag("group_figure_YOUR SHARE")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(shareLayouts) }

        assertFalse(
            "Group share money figure must not visually overflow when font scale is 2.0",
            shareLayouts.first().hasVisualOverflow,
        )
        assertEquals(1, shareLayouts.first().lineCount)
    }

    @Test
    fun `statement import figures do not visually overflow at double font size`() {
        val spentAmount = 125000.0
        val incomeAmount = 180000.0
        val items = listOf(
            StatementTransactionItem(
                id = "s1",
                date = 1730419200000L,
                dateFormatted = "1 Nov 2026",
                monthYearKey = "November 2026",
                monthSortKey = "202611",
                type = "expense",
                amount = spentAmount,
                merchant = "Major Equipment",
                category = "Shopping",
                paymentMethod = "UPI",
                isSelected = true,
            ),
            StatementTransactionItem(
                id = "s2",
                date = 1730505600000L,
                dateFormatted = "2 Nov 2026",
                monthYearKey = "November 2026",
                monthSortKey = "202611",
                type = "income",
                amount = incomeAmount,
                merchant = "Annual Bonus",
                category = "Salary",
                paymentMethod = "IMPS",
                isSelected = true,
            ),
        )
        val parseResult = StatementParseResult(
            bankOrApp = "HDFC Bank",
            statementPeriod = "Nov 2026",
            totalTransactionsCount = 2,
            totalExpenseSum = spentAmount,
            totalIncomeSum = incomeAmount,
            monthGroups = listOf(
                StatementMonthGroup(
                    monthYearKey = "November 2026",
                    monthSortKey = "202611",
                    totalExpense = spentAmount,
                    totalIncome = incomeAmount,
                    transactionCount = 2,
                    items = items,
                ),
            ),
            allItems = items,
        )

        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2.0f)) {
                SplitFlowTheme {
                    StatementImportContent(
                        parseResult = parseResult,
                        onConfirmImport = {},
                    )
                }
            }
        }

        val spentLayouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNode(hasText(Money.format(spentAmount)) and hasAnyAncestor(hasTestTag("import_figure_SPENT")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(spentLayouts) }

        assertFalse(
            "Statement spent figure must not visually overflow when font scale is 2.0",
            spentLayouts.first().hasVisualOverflow,
        )
        assertEquals(1, spentLayouts.first().lineCount)

        val incomeLayouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNode(hasText(Money.format(incomeAmount)) and hasAnyAncestor(hasTestTag("import_figure_CAME IN")), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(incomeLayouts) }

        assertFalse(
            "Statement came in figure must not visually overflow when font scale is 2.0",
            incomeLayouts.first().hasVisualOverflow,
        )
        assertEquals(1, incomeLayouts.first().lineCount)
    }
}
