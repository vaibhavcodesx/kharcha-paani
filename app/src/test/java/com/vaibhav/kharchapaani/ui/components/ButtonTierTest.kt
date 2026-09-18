package com.vaibhav.kharchapaani.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backlog item 9. The app had two weights of "tappable thing" — a gradient pill, and bare coloured
 * text — so settling a real debt looked exactly like an inline link that only scrolls you somewhere.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ButtonTierTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val clicks = mutableMapOf<KpButtonTier, Int>()

    private fun showAll() {
        composeTestRule.setContent {
            SplitFlowTheme {
                Column {
                    KpButtonTier.entries.forEach { tier ->
                        KpButton(
                            // A short label on purpose: "Join" is the real one that was 42dp wide.
                            label = "Join",
                            onClick = { clicks[tier] = (clicks[tier] ?: 0) + 1 },
                            tier = tier,
                            testTag = "tier_$tier",
                        )
                    }
                }
            }
        }
    }

    private fun show(tier: KpButtonTier, enabled: Boolean) {
        composeTestRule.setContent {
            SplitFlowTheme {
                Column {
                    KpButton(
                        label = "Settle up",
                        onClick = { clicks[tier] = (clicks[tier] ?: 0) + 1 },
                        tier = tier,
                        enabled = enabled,
                        testTag = "tier_button",
                    )
                }
            }
        }
    }

    @Test
    fun `every tier renders, is clickable and meets the minimum touch target`() {
        showAll()

        for (tier in KpButtonTier.entries) {
            composeTestRule.onNodeWithTag("tier_$tier")
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
                .assertWidthIsAtLeast(48.dp)
            composeTestRule.onNodeWithTag("tier_$tier").performClick()
            assertEquals("tier $tier", 1, clicks[tier])
        }
    }

    @Test
    fun `a disabled button does not fire`() {
        show(KpButtonTier.Primary, enabled = false)

        composeTestRule.onNodeWithTag("tier_button").assertIsNotEnabled()
        assertEquals(null, clicks[KpButtonTier.Primary])
    }

    @Test
    fun `three of the four tiers carry a container, so colour is not the only signal`() {
        // The old failure mode: everything below the gradient pill was colour-only text, which
        // flattened a debt settlement and a "see more" link into the same thing.
        assertEquals(3, KpButtonTier.entries.count { it.hasContainer })
        assertFalse(KpButtonTier.Link.hasContainer)
        assertTrue(KpButtonTier.Outline.hasContainer)
        assertTrue(KpButtonTier.Ghost.hasContainer)
        assertTrue(KpButtonTier.Primary.hasContainer)
    }

    @Test
    fun `the tiers are ordered loudest first`() {
        assertEquals(
            listOf(KpButtonTier.Primary, KpButtonTier.Outline, KpButtonTier.Ghost, KpButtonTier.Link),
            KpButtonTier.entries.toList(),
        )
    }
}
