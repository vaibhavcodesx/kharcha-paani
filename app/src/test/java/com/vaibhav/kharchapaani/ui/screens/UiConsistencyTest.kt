package com.vaibhav.kharchapaani.ui.screens

import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToKey
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.MonthAnalytics
import com.vaibhav.kharchapaani.ui.components.SubscriptionsContent
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

/** UI audit batch B (docs/production-readiness/audits/ui-audit-2026-11.md): consistency at normal size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class UiConsistencyTest {

    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 3)

    private fun insights(entries: List<TransactionEntity>) {
        val month = YearMonth.from(today)
        compose.setContent {
            SplitFlowTheme {
                InsightsScreen(
                    analytics = MonthAnalytics.of(entries, emptyList(), emptyList(), month, today, zone),
                    transactions = entries.map { FullTransaction(it) },
                    onAsk = {},
                    onExport = {},
                    today = today,
                    month = month,
                    earliestMonth = MonthAnalytics.earliestMonth(entries, today, zone),
                    onMonthChange = {},
                )
            }
        }
    }

    @Test
    fun `a month with nothing in it has no empty highlight tiles`() {
        // U16: "Spent most at —" and "Per day so far ₹0" said nothing.
        insights(emptyList())

        assertEquals(0, compose.onAllNodesWithText("SPENT MOST ON").fetchSemanticsNodes().size)
        assertEquals(0, compose.onAllNodesWithText("PER DAY SO FAR").fetchSemanticsNodes().size)
    }

    @Test
    fun `a month with spending names what it went on`() {
        // U24: "Spent most at Laptop" assumed the merchant was a place.
        val laptop = TransactionEntity(
            id = 1, type = "expense", amount = 60_000.0, category = "Shopping", merchant = "Laptop", paymentMethod = "UPI",
            date = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        insights(listOf(laptop))
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("highlights")

        compose.onNodeWithText("SPENT MOST ON").assertExists()
    }

    @Test
    fun `subscriptions write dates the way the rest of the app does, and money with its sign`() {
        // U14: the phone's medium style said "Nov 5, 2026" on English (US) phones; the amount had no ₹.
        val music = SubscriptionEntity(
            id = 1, name = "Music", amount = 119.0, category = "Entertainment", paymentMethod = "UPI", cycle = "MONTHLY",
            startDate = LocalDate.of(2026, 10, 5).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
        )
        compose.setContent {
            SplitFlowTheme {
                SubscriptionsContent(subscriptions = listOf(music), prefs = CategoryPrefs(), onAdd = { _, _ -> }, onCancel = {}, onDelete = {}, today = today)
            }
        }

        compose.onNodeWithText("5 Nov 2026", substring = true).assertExists()
        assertEquals(0, compose.onAllNodesWithText("Nov 5, 2026", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("₹").assertExists()
    }
}
