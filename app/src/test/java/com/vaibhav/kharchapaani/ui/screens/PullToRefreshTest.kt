package com.vaibhav.kharchapaani.ui.screens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.BuildConfig
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PullToRefreshTest {
    @get:Rule val compose = createComposeRule()

    private val versions
        get() = ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("app_version_gate", Context.MODE_PRIVATE)

    @After
    fun clearGate() {
        versions.edit().clear().commit()
    }

    private fun showHome(
        activity: List<ActivityItem> = emptyList(),
        onEdit: (FullTransaction) -> Unit = {},
    ) {
        compose.setContent {
            SplitFlowTheme {
                HomeScreen(
                    activity = activity,
                    totalNetOwed = 0.0,
                    monthlyAnalytics = MonthlyAnalytics(0.0, 0.0, 0.0, emptyList(), topMerchant = "", dailyStreak = 0),
                    isParsing = false,
                    searchQuery = "",
                    selectedFilter = "ALL",
                    onSearchChange = {},
                    onFilterChange = {},
                    onParseText = {},
                    onParseImage = {},
                    onParsePdf = { _, _ -> },
                    onEditTransaction = onEdit,
                    onDeleteTransaction = {},
                    onRetractSettlement = {},
                    onOpenIouDialog = {},
                    onOpenSimplifyDebts = {},
                    accountAvatar = "V",
                    onOpenAccount = {},
                )
            }
        }
    }

    @Test
    fun homeScreenHasPullToRefreshContainer() {
        showHome()
        compose.onNodeWithTag("home_pull_to_refresh").assertExists()
    }

    @Test
    fun `on Home the required update leaves existing entries accessible`() {
        versions.edit().putInt("min_version_code", BuildConfig.VERSION_CODE + 1).commit()
        val entry = FullTransaction(
            TransactionEntity(id = 17, type = "expense", amount = 250.0, category = "Food", merchant = "Saved lunch", paymentMethod = "UPI"),
        )
        var opened: Long? = null
        showHome(listOf(ActivityItem.Transaction(entry))) { opened = it.transaction.id }

        compose.onNodeWithText("Update Kharcha Paani to keep syncing.").assertExists()
        compose.onNodeWithTag("home_activity_list").performScrollToNode(hasText("Saved lunch"))
        compose.onNodeWithText("Saved lunch").assertExists().performClick()
        assertEquals(17L, opened)
    }

    @Test
    fun splitsScreenHasPullToRefreshContainer() {
        var syncRequested = false
        compose.setContent {
            SplitFlowTheme {
                SplitsScreen(
                    personBalances = emptyList(),
                    groups = emptyList(),
                    onOpenAddFriend = {},
                    onDeleteFriend = {},
                    onOpenCreateGroup = {},
                    onJoinGroup = {},
                    onSelectGroup = {},
                    onDeleteGroup = {},
                    onOpenSimplifyDebts = {},
                    onOpenIouDialog = {},
                    onSettleUp = {},
                    onViewHistory = {},
                    onRequestSync = { syncRequested = true },
                )
            }
        }
        compose.onNodeWithTag("splits_pull_to_refresh").assertExists()
    }
}
