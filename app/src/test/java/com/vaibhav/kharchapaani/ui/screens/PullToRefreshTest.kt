package com.vaibhav.kharchapaani.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.ui.sync.SyncStatus
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PullToRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun homeScreenHasPullToRefreshContainer() {
        var syncRequested = false
        compose.setContent {
            SplitFlowTheme {
                HomeScreen(
                    activity = emptyList(),
                    totalNetOwed = 0.0,
                    monthlyAnalytics = MonthlyAnalytics(0.0, 0.0, 0.0, emptyList()),
                    isParsing = false,
                    searchQuery = "",
                    selectedFilter = "ALL",
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
                    onRequestSync = { syncRequested = true },
                )
            }
        }
        compose.onNodeWithTag("home_pull_to_refresh").assertExists()
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
