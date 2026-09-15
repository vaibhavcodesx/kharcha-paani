package com.vaibhav.kharchapaani.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.domain.MonthAnalytics
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Insights with entries across three months: the picker moves between them and each shows its own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InsightsMonthPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 9, 15)

    private fun tx(id: Long, time: LocalDateTime, amount: Double, category: String, merchant: String) =
        TransactionEntity(id = id, type = "expense", amount = amount, category = category, merchant = merchant, paymentMethod = "UPI", date = time.atZone(zone).toInstant().toEpochMilli())

    private val entries = listOf(
        tx(1, LocalDateTime.of(2026, 7, 10, 12, 0), 2200.0, "Health", "Apollo Pharmacy"),
        tx(2, LocalDateTime.of(2026, 8, 31, 23, 59), 500.0, "Shopping", "Myntra"),
        tx(3, LocalDateTime.of(2026, 9, 1, 0, 0), 18000.0, "Rent", "Landlord"),
        tx(4, LocalDateTime.of(2026, 9, 14, 20, 0), 120.0, "Food", "Chai Point"),
    )

    private fun showInsights(startMonth: YearMonth = YearMonth.from(today)) {
        composeTestRule.setContent {
            var month by remember { mutableStateOf(startMonth) }
            SplitFlowTheme {
                InsightsScreen(
                    analytics = MonthAnalytics.of(entries, emptyList(), emptyList(), month, today, zone),
                    transactions = entries.map { FullTransaction(it) },
                    aiSummary = null,
                    isGeneratingSummary = false,
                    onGenerateAiSummary = {},
                    onExportCsv = {},
                    today = today,
                    month = month,
                    earliestMonth = MonthAnalytics.earliestMonth(entries, today, zone),
                    onMonthChange = { month = it },
                )
            }
        }
    }

    private fun assertCategories(present: List<String>, absent: List<String>) {
        present.forEach { composeTestRule.onNodeWithTag("category_row_$it").performScrollTo() }
        absent.forEach { composeTestRule.onNodeWithTag("category_row_$it").assertDoesNotExist() }
    }

    @Test
    fun `opens on this month, which can't step forward`() {
        showInsights()

        composeTestRule.onNodeWithTag("month_label").assertTextEquals("September 2026")
        composeTestRule.onNodeWithTag("month_next").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("month_previous").assertIsEnabled()
        assertCategories(present = listOf("Rent", "Food"), absent = listOf("Shopping", "Health"))
    }

    @Test
    fun `stepping back shows August's entries and only August's`() {
        showInsights()

        composeTestRule.onNodeWithTag("month_previous").performClick()

        composeTestRule.onNodeWithTag("month_label").assertTextEquals("August 2026")
        composeTestRule.onNodeWithTag("month_next").assertIsEnabled()
        assertCategories(present = listOf("Shopping"), absent = listOf("Rent", "Food", "Health"))
        composeTestRule.onNodeWithText("Per day").performScrollTo()
    }

    @Test
    fun `the first month with entries is as far back as it goes`() {
        showInsights()

        composeTestRule.onNodeWithTag("month_previous").performClick()
        composeTestRule.onNodeWithTag("month_previous").performClick()

        composeTestRule.onNodeWithTag("month_label").assertTextEquals("July 2026")
        composeTestRule.onNodeWithTag("month_previous").assertIsNotEnabled()
        assertCategories(present = listOf("Health"), absent = listOf("Shopping", "Rent", "Food"))
    }

    @Test
    fun `stepping forward again comes back to this month's figures`() {
        showInsights(startMonth = YearMonth.of(2026, 7))

        composeTestRule.onNodeWithTag("month_next").performClick()
        composeTestRule.onNodeWithTag("month_next").performClick()

        composeTestRule.onNodeWithTag("month_label").assertTextEquals("September 2026")
        assertCategories(present = listOf("Rent", "Food"), absent = listOf("Shopping", "Health"))
        composeTestRule.onNodeWithText("Per day so far").performScrollTo()
    }

    @Test
    fun `an opened category's entries are that month's`() {
        val extraFood = tx(5, LocalDateTime.of(2026, 8, 3, 9, 0), 60.0, "Food", "Idli Stall")
        composeTestRule.setContent {
            var month by remember { mutableStateOf(YearMonth.of(2026, 8)) }
            val all = entries + extraFood
            SplitFlowTheme {
                InsightsScreen(
                    analytics = MonthAnalytics.of(all, emptyList(), emptyList(), month, today, zone),
                    transactions = all.map { FullTransaction(it) },
                    aiSummary = null, isGeneratingSummary = false, onGenerateAiSummary = {}, onExportCsv = {},
                    today = today, month = month, earliestMonth = YearMonth.of(2026, 7), onMonthChange = { month = it },
                )
            }
        }

        composeTestRule.onNodeWithTag("category_row_Food").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Idli Stall").performScrollTo()
        composeTestRule.onNodeWithText("Chai Point").assertDoesNotExist()
    }
}
