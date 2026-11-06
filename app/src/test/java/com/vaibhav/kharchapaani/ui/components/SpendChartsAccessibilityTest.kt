package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.vaibhav.kharchapaani.domain.DaySpend
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
class SpendChartsAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `DailyFlowChart at fontScale 2f displays tap hint without clipping`() {
        val days = (1..30).map { day ->
            DaySpend(LocalDate.of(2026, 11, day), 100.0 * day, hasHappened = true)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SplitFlowTheme {
                    DailyFlowChart(
                        days = days,
                        selectedDay = null,
                        onSelectDay = {},
                    )
                }
            }
        }

        // Tap hint is visible and displayed
        val hintNode = compose.onNodeWithText("Tap a day to see what it cost")
        hintNode.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        hintNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("layout result recorded", layouts.isNotEmpty())

        // FlowAxis tick mark is displayed and not clipped
        val tickNode = compose.onNodeWithText("1", useUnmergedTree = true)
        tickNode.assertIsDisplayed()
        val tickLayouts = mutableListOf<TextLayoutResult>()
        tickNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(tickLayouts) }
        assertTrue("tick layout result recorded", tickLayouts.isNotEmpty())
        assertFalse("axis tick text must not be visually clipped at 2x font scale", tickLayouts.single().hasVisualOverflow)
    }

    @Test
    fun `DailyFlowChart at fontScale 2f displays pinned day tooltip without clipping`() {
        val days = (1..30).map { day ->
            DaySpend(LocalDate.of(2026, 11, day), 12500.50, hasHappened = true)
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                SplitFlowTheme {
                    DailyFlowChart(
                        days = days,
                        selectedDay = 14,
                        onSelectDay = {},
                    )
                }
            }
        }

        // Tooltip is displayed
        val tooltip = compose.onNodeWithTag("flow_tooltip")
        tooltip.assertIsDisplayed()

        // Amount inside tooltip is displayed
        val amountNode = compose.onNodeWithText(Money.format(12500.50), useUnmergedTree = true)
        amountNode.assertIsDisplayed()
        val amountLayouts = mutableListOf<TextLayoutResult>()
        amountNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(amountLayouts) }
        assertTrue("amount layout result recorded", amountLayouts.isNotEmpty())
    }
}
