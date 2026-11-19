package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.domain.DaySpend
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.screens.HomeScreen
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class LargeMoneyTextTest {
    @get:Rule val compose = createComposeRule()

    private val width = mutableStateOf(320.dp)
    private val fontScale = mutableStateOf(2.0f)
    private val date = LocalDate.of(2026, 11, 19)
    private val spent = 9_876_543.00
    private val income = 1_234_567.89
    private val balance = 23_45_600.50
    private val dayAmount = 1_27_366.50

    @Test
    fun `Home displays every digit of its money figures from default to double text size`() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                SplitFlowTheme {
                    Box(Modifier.width(width.value)) {
                        HomeScreen(
                            activity = emptyList(),
                            totalNetOwed = balance,
                            monthlyAnalytics = MonthlyAnalytics(spent, income, 0.0, emptyList(), "", 0),
                            isParsing = false,
                            searchQuery = "",
                            selectedFilter = "All",
                            onSearchChange = {},
                            onFilterChange = {},
                            onParseText = {},
                            onParseImage = {},
                            onParsePdf = { _, _ -> },
                            onEditTransaction = {},
                            onDeleteTransaction = {},
                            onRetractSettlement = {},
                            onOpenIouDialog = {},
                            onOpenSimplifyDebts = {},
                            accountAvatar = "V",
                            onOpenAccount = {},
                            today = date,
                            aiEnabled = false,
                        )
                    }
                }
            }
        }
        for (screen in listOf(320.dp, 411.dp)) {
            for (scale in listOf(2.0f, 1.3f, 1.0f)) {
                resize(screen, scale)
                for (amount in listOf(spent, income, balance)) {
                    val visibleAmount = Money.format(amount)
                    val textLayouts = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
                    val digits = compose.onAllNodes(
                        hasAnyAncestor(hasContentDescription(visibleAmount)) and textLayouts,
                        useUnmergedTree = true,
                    ).assertCountEquals(visibleAmount.length)
                    visibleAmount.indices.forEach { index ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        digits[index].assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        assertFalse("$visibleAmount clipped at $screen and $scale", layouts.single().hasVisualOverflow)
                    }
                }
            }
        }
    }

    @Test
    fun `SpendCharts show the selected amount without cutting off digits`() {
        val category = CategorySummary("Food", dayAmount, 100f, 1)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale.value)) {
                SplitFlowTheme {
                    Column(Modifier.width(width.value)) {
                        CategoryRing(listOf(category), dayAmount, selected = category.category, onSelect = {})
                        DailyFlowChart(listOf(DaySpend(date, dayAmount, true)), selectedDay = 0, onSelectDay = {})
                    }
                }
            }
        }
        for (screen in listOf(320.dp, 411.dp)) {
            for (scale in listOf(2.0f, 1.3f, 1.0f)) {
                resize(screen, scale)
                for (tag in listOf("category_ring", "flow_tooltip")) {
                    val figure = compose.onNode(
                        hasText(Money.format(dayAmount)) and hasAnyAncestor(hasTestTag(tag)),
                        useUnmergedTree = true,
                    )
                    val layouts = mutableListOf<TextLayoutResult>()
                    figure.assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertFalse("$tag amount clipped at $screen and $scale", layouts.single().hasVisualOverflow)
                }
                val dayLayouts = mutableListOf<TextLayoutResult>()
                compose.onNode(hasText(Money.compactDigits(dayAmount)), useUnmergedTree = true)
                    .assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(dayLayouts) }
                assertFalse("day-strip amount clipped at $screen and $scale", dayLayouts.single().hasVisualOverflow)
            }
        }
    }

    private fun resize(screen: androidx.compose.ui.unit.Dp, scale: Float) {
        compose.runOnIdle {
            width.value = screen
            fontScale.value = scale
        }
    }
}
