package com.vaibhav.kharchapaani.ui.components

import android.os.Bundle
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.model.StatementTransactionItem
import com.vaibhav.kharchapaani.domain.LocalStatementParser
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2000dp")
class StatementImportRotationTest {

    @get:Rule
    val compose = createComposeRule()

    private val testSaverScope = SaverScope { true }

    @Test
    fun `statementItemListSaver saves and restores all fields accurately`() {
        val saver = statementItemListSaver()
        val originalItems = listOf(
            StatementTransactionItem(
                id = "item-1",
                date = 1729000000000L,
                dateFormatted = "15 Oct 2024",
                monthYearKey = "October 2024",
                monthSortKey = "202410",
                type = "expense",
                amount = 250.75,
                merchant = "Swiggy",
                category = "Food",
                paymentMethod = "UPI",
                note = "Dinner",
                isSelected = true,
                flag = null,
            ),
            StatementTransactionItem(
                id = "item-2",
                date = 1729100000000L,
                dateFormatted = "16 Oct 2024",
                monthYearKey = "October 2024",
                monthSortKey = "202410",
                type = "income",
                amount = 1000.0,
                merchant = "Salary Bonus",
                category = "Salary",
                paymentMethod = "NEFT",
                note = "Bonus",
                isSelected = false,
                flag = "LARGE",
            ),
        )

        val bundle = with(saver) { testSaverScope.save(originalItems) } as Bundle
        val restored = saver.restore(bundle)

        assertEquals(2, restored?.size)
        val first = restored!![0]
        assertEquals("item-1", first.id)
        assertEquals(1729000000000L, first.date)
        assertEquals("15 Oct 2024", first.dateFormatted)
        assertEquals("October 2024", first.monthYearKey)
        assertEquals("202410", first.monthSortKey)
        assertEquals("expense", first.type)
        assertEquals(250.75, first.amount, 0.001)
        assertEquals("Swiggy", first.merchant)
        assertEquals("Food", first.category)
        assertEquals("UPI", first.paymentMethod)
        assertEquals("Dinner", first.note)
        assertTrue(first.isSelected)
        assertNull(first.flag)

        val second = restored[1]
        assertEquals("item-2", second.id)
        assertEquals("LARGE", second.flag)
        assertFalse(second.isSelected)
        assertEquals("Salary", second.category)
        assertEquals("income", second.type)
    }

    @Test
    fun `statementExpandedSaver saves and restores map entries`() {
        val saver = statementExpandedSaver()
        val originalMap = mutableStateMapOf("202410" to true, "202409" to false)

        val bundle = with(saver) { testSaverScope.save(originalMap) } as Bundle
        val restored = saver.restore(bundle)

        assertEquals(true, restored?.get("202410"))
        assertEquals(false, restored?.get("202409"))
    }

    @Test
    fun `statement ticks and category fixes survive simulated rotation restore`() {
        val restorationTester = StateRestorationTester(compose)

        val parsed = LocalStatementParser.parseDateToTimestamp("2026-10-27")
        val item1 = StatementTransactionItem(
            id = "row-1",
            date = parsed.first,
            dateFormatted = parsed.second,
            monthYearKey = parsed.third,
            monthSortKey = "202610",
            merchant = "Chai Tapri",
            amount = 40.0,
            category = "Food",
            isSelected = true,
        )
        val item2 = StatementTransactionItem(
            id = "row-2",
            date = parsed.first,
            dateFormatted = parsed.second,
            monthYearKey = parsed.third,
            monthSortKey = "202610",
            merchant = "Metro Card",
            amount = 100.0,
            category = "Transport",
            isSelected = true,
        )
        val statement = LocalStatementParser.buildStatementGroups(
            bankOrApp = "HDFC Bank",
            period = "October 2026",
            items = listOf(item1, item2),
        )

        var confirmedImport: List<StatementTransactionItem>? = null

        restorationTester.setContent {
            SplitFlowTheme {
                StatementImportContent(
                    parseResult = statement,
                    onConfirmImport = { confirmedImport = it },
                )
            }
        }

        // Initially both entries are ticked
        compose.onNodeWithTag("confirm_import_statement_button").assertTextEquals("Import 2 entries")
        compose.onNodeWithText("2 of 2").assertExists()

        // Untick the first item (description is "Import <merchant>")
        compose.onNodeWithContentDescription("Import Chai Tapri").performClick()
        compose.waitForIdle()

        // Button should now say Import 1 entry, and summary says 1 of 2
        compose.onNodeWithTag("confirm_import_statement_button").assertTextEquals("Import 1 entry")
        compose.onNodeWithText("1 of 2").assertExists()

        // Change category of Metro Card from "Transport" to "Bills"
        compose.onNodeWithContentDescription("Transport").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Bills").performClick()
        compose.waitForIdle()

        // Verify Bills category is displayed on the Metro Card CategoryBadge
        compose.onNodeWithContentDescription("Bills").assertExists()

        // Now simulate activity recreation / rotation
        restorationTester.emulateSavedInstanceStateRestore()
        compose.waitForIdle()

        // After rotation, ticks and modified category must survive!
        compose.onNodeWithTag("confirm_import_statement_button").assertTextEquals("Import 1 entry")
        compose.onNodeWithText("1 of 2").assertExists()
        compose.onNodeWithContentDescription("Bills").assertExists()

        // Confirm import and check emitted list
        compose.onNodeWithTag("confirm_import_statement_button").performClick()
        compose.waitForIdle()

        assertEquals(1, confirmedImport?.size)
        val imported = confirmedImport!!.first()
        assertEquals("row-2", imported.id)
        assertEquals("Metro Card", imported.merchant)
        assertEquals("Bills", imported.category)
    }
}
