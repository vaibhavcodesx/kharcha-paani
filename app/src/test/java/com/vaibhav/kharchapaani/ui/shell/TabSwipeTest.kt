package com.vaibhav.kharchapaani.ui.shell

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.ui.components.TransactionItemCard
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

class TabSwipeDecisionTest {

    private val min = 100f

    @Test
    fun `a clear swipe left goes forward, right goes back`() {
        assertEquals(1, TabSwipe.decide(current = 0, count = 3, dx = -300f, dy = 20f, minDistancePx = min))
        assertEquals(0, TabSwipe.decide(current = 1, count = 3, dx = 300f, dy = -20f, minDistancePx = min))
    }

    @Test
    fun `short, diagonal or off-the-end swipes stay put`() {
        assertNull(TabSwipe.decide(0, 3, dx = -60f, dy = 0f, minDistancePx = min))
        assertNull(TabSwipe.decide(0, 3, dx = -300f, dy = 200f, minDistancePx = min))
        assertNull(TabSwipe.decide(0, 3, dx = 300f, dy = 0f, minDistancePx = min))
        assertNull(TabSwipe.decide(2, 3, dx = -300f, dy = 0f, minDistancePx = min))
    }
}

/**
 * Both gestures together, on a real swipeable activity row inside the swiping page: the row keeps
 * its swipe (delete) and the page doesn't turn; the same swipe on the header turns the page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TabSwipeGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val tx = FullTransaction(
        TransactionEntity(id = 9, type = "expense", amount = 34.5, category = "Food", merchant = "Idli", paymentMethod = "UPI", date = 0L),
    )

    private var tab = 0
    private var deletes = 0
    private var edits = 0

    private fun page() {
        composeTestRule.setContent {
            SplitFlowTheme {
                var current by remember { mutableIntStateOf(0) }
                Column(
                    Modifier
                        .fillMaxSize()
                        .tabSwipe(current = current, count = 3, onSelect = { current = it; tab = it }),
                ) {
                    Box(Modifier.fillMaxWidth().height(160.dp).testTag("header")) { Text("Spent this month") }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("chips")) {
                        repeat(8) { Box(Modifier.width(120.dp).height(40.dp)) { Text("Chip $it") } }
                    }
                    TransactionItemCard(tx, onEdit = { edits++ }, onDelete = { deletes++ })
                }
            }
        }
    }

    @Test
    fun `swiping a row deletes it and does not change tab`() {
        page()
        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertEquals(1, deletes)
        assertEquals(0, tab)
    }

    @Test
    fun `swiping a row right edits it and does not change tab`() {
        page()
        // From tab 1, so a stolen swipe-right would show up as a move back to 0.
        composeTestRule.onNodeWithTag("header").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(1, tab)

        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()

        assertEquals(1, edits)
        assertEquals(1, tab)
    }

    @Test
    fun `the same swipe on the header changes tab, then the row still deletes`() {
        page()
        composeTestRule.onNodeWithTag("header").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(1, tab)

        composeTestRule.onNodeWithTag("transaction_item_9").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(1, deletes)
        assertEquals(1, tab)
        assertEquals(0, edits)
    }

    @Test
    fun `scrolling the chip row sideways does not change tab`() {
        page()
        composeTestRule.onNodeWithTag("chips").performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()

        assertEquals(0, tab)
    }

    @Test
    fun `a vertical swipe does not change tab`() {
        page()
        composeTestRule.onNodeWithTag("header").performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        assertEquals(0, tab)
    }
}
