package com.vaibhav.kharchapaani.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.components.ConfirmSheetContent
import com.vaibhav.kharchapaani.ui.screens.HomeScreen
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.shell.KpDock
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.LocalHazeState
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders screens with sample data and saves PNGs with the test results, so each PR's CI artifact
 * shows what the change looks like. There's no emulator path past Google sign-in. Robolectric draws
 * in software, so glass shows its no-blur fallback here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenRenderCapture {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val today = LocalDate.of(2026, 9, 15)
    private fun at(day: LocalDate, hour: Int, minute: Int = 0) =
        day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun tx(id: Long, date: Long, merchant: String, category: String, amount: Double, method: String = "UPI", type: String = "expense") =
        ActivityItem.Transaction(FullTransaction(TransactionEntity(id = id, type = type, amount = amount, category = category, merchant = merchant, paymentMethod = method, date = date)))

    private val activity = listOf(
        tx(1, at(today, 21, 10), "Swiggy", "Food", 412.0, "GPay"),
        ActivityItem.Settlement(SettlementEntity(id = 50, fromPersonId = 2, toPersonId = -1, amount = 1250.0, date = at(today, 18, 5)), "Rohan", "You", "Goa trip"),
        tx(2, at(today, 9, 40), "Blue Tokai", "Food", 34.5, "Cash"),
        ActivityItem.Transaction(
            FullTransaction(
                TransactionEntity(id = 3, type = "expense", amount = 3600.0, category = "Entertainment", merchant = "PVR IMAX", paymentMethod = "Credit Card", date = at(today.minusDays(1), 20), isSplit = true),
                split = SplitEntity(id = 1, transactionId = 3, totalAmount = 3600.0),
                shares = listOf(SplitShareEntity(splitId = 1, personId = -1, shareAmount = 1200.0), SplitShareEntity(splitId = 1, personId = 2, shareAmount = 1200.0), SplitShareEntity(splitId = 1, personId = 3, shareAmount = 1200.0)),
            ),
        ),
        tx(4, at(today.minusDays(1), 8, 15), "Rapido auto", "Transport", 89.0),
        tx(5, at(today.minusDays(3), 11), "Acme Studio", "Income (Client)", 42000.0, "Bank Transfer", type = "income"),
        tx(6, at(today.minusDays(3), 10), "Electricity bill", "Bills", 1840.75, "PhonePe"),
    )

    @Test
    fun `dock over the activity list`() = capture("dock") {
        val hazeState = rememberHazeState()
        Box(Modifier.fillMaxSize().background(CharcoalBg)) {
            Box(Modifier.fillMaxSize().hazeSource(hazeState)) { Home(longFeed = true) }
            CompositionLocalProvider(LocalHazeState provides hazeState) {
                KpDock(currentTab = AppTab.Activity, onSelectTab = {}, onAdd = {}, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }

    @Test
    fun `home`() = capture("home") {
        Box(Modifier.fillMaxSize().background(CharcoalBg)) { Home(longFeed = false) }
    }

    @Test
    fun `quick add confirm sheet`() = capture("confirm_sheet") {
        Box(Modifier.fillMaxSize().background(CharcoalSurface)) {
            ConfirmSheetContent(
                isParsing = false, isEditing = false, type = "expense", amount = "370", merchant = "McDonald's",
                category = "Food", paymentMethod = "GPay", note = "", date = at(today, 13, 20), groupId = 7,
                availableGroups = listOf(GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️")), isSplit = true,
                splitMode = SplitMode.EQUAL, paidByPersonId = -1L,
                shares = listOf(ShareInput(-1, "You", 123.34), ShareInput(2, "Rohan", 123.33), ShareInput(3, "Meera", 123.33, isSelected = true)),
                onTypeChange = {}, onAmountChange = {}, onMerchantChange = {}, onCategoryChange = {}, onPaymentMethodChange = {},
                onNoteChange = {}, onDateChange = {}, onGroupIdChange = {}, onIsSplitChange = {}, onSplitModeChange = {},
                onPaidByPersonIdChange = {}, onTogglePersonShare = {}, onExactAmountChange = { _, _ -> },
                onPercentageChange = { _, _ -> }, onOpenAddFriend = {}, onSave = {},
            )
        }
    }

    @Composable
    private fun Home(longFeed: Boolean) {
        HomeScreen(
            activity = if (longFeed) activity + activity.map { it.shifted() } else activity,
            totalNetOwed = 2450.5,
            monthlyAnalytics = MonthlyAnalytics(totalExpense = 18420.25, totalIncome = 42000.0, netCashflow = 23579.75, categorySummaries = emptyList(), topMerchant = "Swiggy", dailyStreak = 6),
            isParsing = false, searchQuery = "", selectedFilter = "All",
            onSearchChange = {}, onFilterChange = {}, onParseText = {}, onParseImage = {}, onParsePdf = { _, _ -> },
            onEditTransaction = {}, onDeleteTransaction = {}, onRetractSettlement = {}, onOpenIouDialog = {},
            onOpenSimplifyDebts = {}, accountInitial = "V", onOpenAccount = {}, today = today,
        )
    }

    private fun ActivityItem.shifted(): ActivityItem = when (this) {
        is ActivityItem.Transaction -> copy(fullTransaction = fullTransaction.copy(transaction = fullTransaction.transaction.copy(id = fullTransaction.transaction.id + 100, date = fullTransaction.transaction.date - 7 * DAY)))
        is ActivityItem.Settlement -> copy(settlement = settlement.copy(id = settlement.id + 100, date = settlement.date - 7 * DAY))
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        lateinit var root: View
        composeTestRule.setContent {
            root = LocalView.current
            SplitFlowTheme { content() }
        }
        // captureToImage waits for a redraw that Robolectric's paused looper never delivers.
        val image = composeTestRule.runOnIdle {
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
        val out = File("build/test-results/testDebugUnitTest/screens/$name.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
