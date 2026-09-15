package com.vaibhav.kharchapaani.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.data.model.PersonBalance
import com.vaibhav.kharchapaani.domain.SharedRecordGroups
import com.vaibhav.kharchapaani.ui.components.SettleUpContent
import com.vaibhav.kharchapaani.ui.components.TripCard
import com.vaibhav.kharchapaani.ui.screens.SplitsScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.vaibhav.kharchapaani.data.model.CategorySummary
import com.vaibhav.kharchapaani.data.model.MonthlyAnalytics
import com.vaibhav.kharchapaani.data.model.ShareInput
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.components.ConfirmSheetContent
import com.vaibhav.kharchapaani.ui.components.SheetHandle
import com.vaibhav.kharchapaani.ui.screens.HomeScreen
import com.vaibhav.kharchapaani.ui.screens.InsightsScreen
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
        Column(Modifier.fillMaxSize().background(CharcoalSurface), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHandle()
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

    private val rohan = PersonEntity(id = 2, name = "Rohan Mehta", avatarColor = "#70C1FF", profileId = "r")
    private val meera = PersonEntity(id = 3, name = "Meera", avatarColor = "#FFB3BA")
    private val arjun = PersonEntity(id = 4, name = "Arjun", avatarColor = "#B5EAD7")
    private val kabir = PersonEntity(id = 5, name = "Kabir", avatarColor = "#D7BDE2")

    @Test
    fun `splits`() = capture("splits") {
        SplitsScreen(
            personBalances = listOf(
                PersonBalance(meera, 1250.5, 1250.5, 0.0),
                PersonBalance(arjun, 0.0, 0.0, 0.0),
                PersonBalance(rohan, -415.5, 0.0, 415.5),
                PersonBalance(kabir, 3200.0, 3200.0, 0.0),
            ),
            groups = trips,
            onOpenAddFriend = {}, onDeleteFriend = {}, onOpenCreateGroup = {}, onJoinGroup = {}, onSelectGroup = {},
            onDeleteGroup = {}, onOpenSimplifyDebts = {}, onOpenIouDialog = {}, onSettleUp = {}, onViewHistory = {},
        )
    }

    private val trips = listOf(
        GroupWithDetails(GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️", colorHex = "#70C1FF", syncState = SyncState.SYNCED), listOf(rohan, meera, arjun, kabir), 24500.0, 9000.0, 4900.0, 4100.0, 18),
        GroupWithDetails(GroupEntity(id = 8, name = "Flat 402", emoji = "🏠", colorHex = "#C6F24C"), listOf(meera), 61200.75, 20000.0, 30600.38, -10600.38, 42),
        GroupWithDetails(GroupEntity(id = 9, name = "Lonavala monsoon weekend with college friends", emoji = "⛰️", colorHex = "#FF7A3D"), listOf(arjun, kabir), 8400.0, 2800.0, 2800.0, 0.0, 6),
    )

    @Test
    fun `trip cards`() = capture("trip_cards") {
        Column(Modifier.fillMaxSize().background(CharcoalBg).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            trips.forEach { TripCard(details = it, onOpen = {}, onDelete = {}) }
        }
    }

    @Test
    fun `settle up sheet`() = capture("settle_up_sheet") {
        Column(Modifier.fillMaxSize().background(CharcoalSurface), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHandle()
            SettleUpContent(
                person = rohan,
                suggestedAmount = 1250.5,
                groupOptions = SharedRecordGroups.Options(listOf(trips[0].group, GroupEntity(id = 11, name = "Flat 402", emoji = "🏠", syncState = SyncState.SYNCED)), emptyList(), involvesGroupmates = true),
                onCreateGroup = {},
                onConfirm = { _, _, _, _, _ -> },
            )
        }
    }

    @Test
    fun `insights with a category open`() = capture("insights") {
        val food = activity.filterIsInstance<ActivityItem.Transaction>().map { it.fullTransaction }
        InsightsScreen(
            analytics = MonthlyAnalytics(
                totalExpense = 18420.25, totalIncome = 42000.0, netCashflow = 23579.75,
                categorySummaries = listOf(
                    CategorySummary("Bills", 6840.75, 37.1f, 4),
                    CategorySummary("Food", 5120.5, 27.8f, 23),
                    CategorySummary("Entertainment", 3600.0, 19.5f, 3),
                    CategorySummary("Transport", 1959.0, 10.6f, 14),
                    CategorySummary("Shopping", 900.0, 4.9f, 2),
                ),
                topMerchant = "Swiggy", dailyStreak = 6,
            ),
            transactions = food, aiSummary = null, isGeneratingSummary = false,
            onGenerateAiSummary = {}, onExportCsv = {}, today = today,
        )
    }

    /** The widget's real layout, inflated the way the launcher does, at a 4x2 size. */
    @Test
    fun `home screen widget`() {
        val context: android.content.Context = org.robolectric.RuntimeEnvironment.getApplication()
        val density = context.resources.displayMetrics.density
        val parent = android.widget.FrameLayout(context)
        val widget = android.view.LayoutInflater.from(context).inflate(com.vaibhav.kharchapaani.R.layout.widget_splitflow, parent, false)
        val summary = com.vaibhav.kharchapaani.widget.WidgetSummary.of(18420.25, 42000.0, 2450.5)
        widget.findViewById<android.widget.TextView>(com.vaibhav.kharchapaani.R.id.widget_month_spent).text = summary.spent
        widget.findViewById<android.widget.TextView>(com.vaibhav.kharchapaani.R.id.widget_month_income).text = summary.income
        widget.findViewById<android.widget.TextView>(com.vaibhav.kharchapaani.R.id.widget_net_owed_badge).apply {
            text = summary.balance
            setTextColor(summary.balanceColor)
        }
        val width = (340 * density).toInt()
        val height = (170 * density).toInt()
        val backdrop = (16 * density).toInt()
        widget.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        widget.layout(0, 0, width, height)
        // A plain wallpaper-ish backdrop around it, so the rounded corners and border show.
        val image = Bitmap.createBitmap(width + backdrop * 2, height + backdrop * 2, Bitmap.Config.ARGB_8888)
        Canvas(image).apply {
            drawColor(0xFF3A4A5C.toInt())
            translate(backdrop.toFloat(), backdrop.toFloat())
            widget.draw(this)
        }
        val out = File("build/test-results/testDebugUnitTest/screens/widget.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }

    @Test
    fun `group detail sheet`() = capture("group_detail") {
        val priya = PersonEntity(id = 6, name = "Priya", avatarColor = "#FFB3BA", profileId = "priya")
        val goa = trips[0].copy(
            members = listOf(rohan, priya),
            transactions = listOf(
                FullTransaction(
                    TransactionEntity(id = 21, type = "expense", amount = 4800.0, category = "Food", merchant = "Thalassa dinner", paymentMethod = "Card", date = at(today, 21), isSplit = true, ownerProfileId = "priya"),
                    split = SplitEntity(id = 11, transactionId = 21, totalAmount = 4800.0, paidByPersonId = 2),
                    shares = listOf(SplitShareEntity(splitId = 11, personId = -1, shareAmount = 1600.0)),
                ),
                FullTransaction(
                    TransactionEntity(id = 22, type = "expense", amount = 1200.0, category = "Transport", merchant = "Scooter rental", paymentMethod = "UPI", date = at(today.minusDays(1), 10), isSplit = true),
                    split = SplitEntity(id = 12, transactionId = 22, totalAmount = 1200.0, paidByPersonId = -1),
                    shares = listOf(SplitShareEntity(splitId = 12, personId = -1, shareAmount = 400.0)),
                ),
            ),
        )
        Column(Modifier.fillMaxSize().background(CharcoalSurface), horizontalAlignment = Alignment.CenterHorizontally) {
            SheetHandle()
            com.vaibhav.kharchapaani.ui.components.GroupDetailContent(groupDetails = goa, onAddExpense = {}, onEditTransaction = {})
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
            syncStatus = com.vaibhav.kharchapaani.ui.sync.SyncStatus(failedChanges = 1),
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
