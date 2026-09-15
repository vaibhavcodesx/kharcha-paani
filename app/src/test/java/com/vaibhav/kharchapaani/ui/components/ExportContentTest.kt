package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.domain.ExportRange
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExportContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 15)
    private fun on(date: LocalDate, id: Long) = FullTransaction(
        TransactionEntity(id = id, type = "expense", amount = 10.0, category = "Food", merchant = "m$id", paymentMethod = "UPI", date = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()),
    )

    // 3 in July, none in August, 2 in September.
    private val entries = listOf(
        on(LocalDate.of(2026, 7, 2), 1), on(LocalDate.of(2026, 7, 9), 2), on(LocalDate.of(2026, 7, 30), 3),
        on(LocalDate.of(2026, 9, 1), 4), on(LocalDate.of(2026, 9, 14), 5),
    )

    private var exported: ExportRange? = null

    private fun show(startScope: ExportScope = ExportScope.Month) {
        composeTestRule.setContent {
            var scope by remember { mutableStateOf(startScope) }
            var month by remember { mutableStateOf(YearMonth.from(today)) }
            SplitFlowTheme {
                ExportContent(
                    transactions = entries,
                    scope = scope,
                    month = month,
                    from = LocalDate.of(2026, 7, 5),
                    to = LocalDate.of(2026, 9, 1),
                    earliestMonth = YearMonth.of(2026, 7),
                    today = today,
                    onScopeChange = { scope = it },
                    onMonthChange = { month = it },
                    onDatesChange = { _, _ -> },
                    onExport = { exported = it },
                )
            }
        }
    }

    @Test
    fun `a month says how many entries it holds and exports that month`() {
        show()

        composeTestRule.onNodeWithText("2 entries").assertExists()
        composeTestRule.onNodeWithTag("export_button").performClick()

        assertEquals(ExportRange.Month(YearMonth.of(2026, 9)), exported)
    }

    @Test
    fun `a month with nothing in it can't be exported`() {
        show()

        composeTestRule.onNodeWithTag("month_previous").performClick()

        composeTestRule.onNodeWithText("0 entries").assertExists()
        composeTestRule.onNodeWithTag("export_button").assertIsNotEnabled()
    }

    @Test
    fun `everything counts every entry`() {
        show()

        composeTestRule.onNodeWithTag("export_scope_Everything").performClick()

        composeTestRule.onNodeWithText("5 entries").assertExists()
        composeTestRule.onNodeWithTag("export_button").assertIsEnabled().performClick()
        assertEquals(ExportRange.Everything, exported)
    }

    @Test
    fun `a date range counts both ends`() {
        show(startScope = ExportScope.Dates)

        // 9 Jul, 30 Jul and 1 Sep fall in 5 Jul – 1 Sep.
        composeTestRule.onNodeWithText("3 entries").assertExists()
        composeTestRule.onNodeWithTag("export_button").performClick()
        assertEquals(ExportRange.Dates(LocalDate.of(2026, 7, 5), LocalDate.of(2026, 9, 1)), exported)
    }
}
