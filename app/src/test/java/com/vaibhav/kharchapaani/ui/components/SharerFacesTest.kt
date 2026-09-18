package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Backlog §5, Image 13: a split expense should show *who* on the row, not a count. "Split between
 * 3" made you open the expense to find out who it was with.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SharerFacesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun person(id: Long, name: String) =
        PersonEntity(id = id, name = name, avatarColor = "#70C1FF")

    private fun show(sharers: List<PersonEntity>, count: Int) {
        composeTestRule.setContent {
            SplitFlowTheme { SharerFaces(sharers = sharers, count = count) }
        }
    }

    @Test
    fun `a split names the people it is with`() {
        show(listOf(person(1, "Rohan Mehta"), person(2, "Meera")), count = 2)

        composeTestRule.onNodeWithContentDescription("Split with Rohan Mehta, Meera").assertIsDisplayed()
    }

    @Test
    fun `a big split shows three faces and counts the rest`() {
        show(listOf(person(1, "Rohan"), person(2, "Meera"), person(3, "Asha"), person(4, "Dev")), count = 5)

        // The "+2" chip itself is cleared from semantics on purpose — the row carries one
        // description for the whole group, so TalkBack reads it as one thing.
        composeTestRule.onNodeWithContentDescription("Split with Rohan, Meera, Asha and 2 more").assertIsDisplayed()
    }

    @Test
    fun `a share whose person this phone doesn't know yet still counts`() {
        // A groupmate mid-sync has a share but no local person row. The "+n" comes off the split's
        // own size, so the row never claims a split is smaller than it is.
        show(listOf(person(1, "Rohan")), count = 4)

        composeTestRule.onNodeWithContentDescription("Split with Rohan and 3 more").assertIsDisplayed()
    }

    @Test
    fun `with nobody resolved it falls back to the count it always showed`() {
        show(emptyList(), count = 3)

        composeTestRule.onNodeWithContentDescription("Split between 3").assertIsDisplayed()
    }

    @Test
    fun `an unsplit expense draws nothing at all`() {
        show(listOf(person(1, "Rohan")), count = 1)

        assertEquals(
            0,
            composeTestRule.onAllNodes(hasContentDescriptionStartingWithSplit()).fetchSemanticsNodes().size,
        )
    }

    private fun hasContentDescriptionStartingWithSplit() = SemanticsMatcher("has a split description") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Split") } == true
    }
}
