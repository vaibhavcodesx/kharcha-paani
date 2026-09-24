package com.vaibhav.kharchapaani.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.vaibhav.kharchapaani.data.db.entity.SubscriptionEntity
import com.vaibhav.kharchapaani.domain.CategoryPrefs
import com.vaibhav.kharchapaani.domain.SubscriptionCycle
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SubscriptionsContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 18)
    private val zone = ZoneId.systemDefault()

    private var added: Pair<NewSubscription, Boolean>? = null
    private var cancelled: Long? = null
    private var deleted: Long? = null

    private fun netflix(cancelledOn: LocalDate? = null) = SubscriptionEntity(
        id = 4,
        name = "Netflix",
        amount = 649.0,
        category = "Entertainment",
        paymentMethod = "Credit Card",
        cycle = SubscriptionCycle.MONTHLY.name,
        startDate = LocalDate.of(2026, 6, 15).atStartOfDay(zone).toInstant().toEpochMilli(),
        cancelledAt = cancelledOn?.atStartOfDay(zone)?.toInstant()?.toEpochMilli(),
    )

    private fun show(existing: List<SubscriptionEntity> = emptyList()) {
        composeTestRule.setContent {
            SplitFlowTheme {
                SubscriptionsContent(
                    subscriptions = existing,
                    prefs = CategoryPrefs(),
                    onAdd = { new, fromStart -> added = new to fromStart },
                    onCancel = { cancelled = it },
                    onDelete = { deleted = it },
                    today = today,
                )
            }
        }
    }

    @Test
    fun `a subscription needs a name and an amount`() {
        show()

        composeTestRule.onNodeWithTag("add_subscription_button").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("subscription_name_input").performScrollTo().performTextReplacement("Netflix")
        composeTestRule.onNodeWithTag("add_subscription_button").assertIsNotEnabled()
    }

    @Test
    fun `one starting today is added without asking about past charges`() {
        show()

        composeTestRule.onNodeWithTag("subscription_name_input").performScrollTo().performTextReplacement("Netflix")
        composeTestRule.onNodeWithTag("subscription_amount_input").performScrollTo().performTextReplacement("649")
        composeTestRule.onNodeWithTag("add_subscription_button").performClick()

        composeTestRule.onNodeWithTag("backdate_dialog").assertDoesNotExist()
        assertEquals(NewSubscription("Netflix", 649.0, "Bills", "PopUPI", SubscriptionCycle.MONTHLY, today) to true, added)
    }

    @Test
    fun `a cancelled one is shown as cancelled and can be removed`() {
        show(listOf(netflix(cancelledOn = LocalDate.of(2026, 8, 20))))

        composeTestRule.onNodeWithText("Cancelled").performScrollTo()
        composeTestRule.onNodeWithTag("cancel_subscription_4").assertDoesNotExist()
        composeTestRule.onNodeWithTag("delete_subscription_4").performScrollTo().performClick()

        assertEquals(4L, deleted)
        assertNull(cancelled)
    }

    @Test
    fun `a running one shows its next charge and can be cancelled`() {
        show(listOf(netflix()))

        // The date itself is formatted for the phone's locale, so just that a next charge is named.
        composeTestRule.onNodeWithText("Monthly · next", substring = true).performScrollTo()
        composeTestRule.onNodeWithTag("cancel_subscription_4").performScrollTo().performClick()
        // Nothing yet: it asks first, because it can't be undone.
        assertEquals(null, cancelled)
        composeTestRule.onNodeWithTag("cancel_subscription_confirm").performClick()

        assertEquals(4L, cancelled)
    }

    @Test
    fun `keeping it from the question cancels nothing`() {
        show(listOf(netflix()))

        composeTestRule.onNodeWithTag("cancel_subscription_4").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Keep it").performClick()

        assertEquals(null, cancelled)
    }
}
