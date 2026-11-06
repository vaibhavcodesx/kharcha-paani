package com.vaibhav.kharchapaani.ui.store

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.draw.clip
import com.vaibhav.kharchapaani.ui.components.SettlementAnswerContent
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.SplitFlowApp
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.domain.Categories
import com.vaibhav.kharchapaani.ui.components.ConfirmSheetContent
import com.vaibhav.kharchapaani.ui.components.GroupDetailContent
import com.vaibhav.kharchapaani.ui.components.SheetHandle
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.imageio.ImageIO

/**
 * The six Play listing screenshots (docs/store-listing/screenshots.md), rendered from the shipped
 * screens with [StoreFixture] in a real Room database and the real MainViewModel, so every amount
 * is the app's own. 412×732 dp, the common Pixel width, at 420 dpi is 1081×1921 px: Play's
 * "1080×1920 or larger", inside its 2:1 limit. (At 360 dp the group sheet clips "You're owed" to
 * "YOU'RE"; that's reported on #188, not hidden here.)
 *
 * Tabs are the real app shell. A sheet opens in its own window, which a capture of the activity
 * can't see, so sheets are their content composable fed from the same ViewModel, as
 * ScreenRenderCapture does.
 *
 * Output: build/test-results/testDebugUnitTest/store-listing/, in the unit-test-results artifact.
 * The approved copies live in docs/store-listing/assets/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h732dp-420dpi")
class StoreListingCapture {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val store = androidx.lifecycle.ViewModelStore()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { StoreFixture.seed(db) }
        viewModel = MainViewModel(application, db, StoreFixture.USER_ID)
        store.put("main", viewModel)
    }

    @After
    fun tearDown() {
        StoreFixture.stopViewModels(store, if (::viewModel.isInitialized) viewModel else null)
        if (::db.isInitialized) db.close()
    }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun shoot(name: String) {
        compose.waitForIdle()
        val file = compose.runOnIdle { StoreFixture.save(compose.activity.window.decorView, name) }
        val image = ImageIO.read(file)
        assertTrue("Play wants 1080×1920 or larger: ${image.width}×${image.height}", image.width >= 1080 && image.height >= 1920)
        assertTrue("Play's long side is at most twice the short", image.height <= 2 * image.width)
        assertEquals("opaque: Play takes no alpha", false, image.colorModel.hasAlpha())
    }

    /** The real app shell on [tab], with [over] drawn on top of it (a sheet, for screen 5). */
    private fun app(tab: Int, over: @Composable BoxScope.() -> Unit = {}) {
        viewModel.setTab(tab)
        val sharedGroups = SharedGroupsViewModel(application, db, StoreFixture.USER_ID)
        store.put("shared", sharedGroups)
        compose.setContent {
            SplitFlowTheme {
                Box(Modifier.fillMaxSize()) {
                    SplitFlowApp(
                        viewModel = viewModel,
                        sharedGroups = sharedGroups.controller,
                        selfProfileId = StoreFixture.USER_ID,
                        authUiState = StoreFixture.authUiState,
                        onRetryProfile = {},
                        onSaveProfile = { _, _ -> },
                        onSignOut = {},
                    )
                    over()
                }
            }
        }
    }

    /** A sheet as it opens over the app: its handle and content, on the sheet's surface. */
    private fun sheet(content: @Composable () -> Unit) {
        compose.setContent {
            // What the app shell would be collecting behind the sheet.
            viewModel.people.collectAsState()
            viewModel.groups.collectAsState()
            viewModel.rawTransactions.collectAsState()
            SplitFlowTheme {
                Column(Modifier.fillMaxSize().background(CharcoalSurface), horizontalAlignment = Alignment.CenterHorizontally) {
                    SheetHandle()
                    content()
                }
            }
        }
    }

    /** The add/edit sheet exactly as MainActivity feeds it, from the ViewModel's draft. */
    @Composable
    private fun LiveConfirmSheet() {
        val type by viewModel.type.collectAsState()
        val amount by viewModel.amount.collectAsState()
        val merchant by viewModel.merchant.collectAsState()
        val category by viewModel.category.collectAsState()
        val paymentMethod by viewModel.paymentMethod.collectAsState()
        val note by viewModel.note.collectAsState()
        val date by viewModel.date.collectAsState()
        val groupId by viewModel.groupId.collectAsState()
        val rawGroups by viewModel.rawGroups.collectAsState()
        val isSplit by viewModel.isSplit.collectAsState()
        val splitMode by viewModel.splitMode.collectAsState()
        val paidBy by viewModel.paidByPersonId.collectAsState()
        val shares by viewModel.shares.collectAsState()
        val editingId by viewModel.editingTransactionId.collectAsState()
        val addedBy by viewModel.editingAddedBy.collectAsState()
        val splitLocked by viewModel.splitLocked.collectAsState()
        ConfirmSheetContent(
            isParsing = false, isEditing = editingId != null, type = type, amount = amount, merchant = merchant,
            category = category, paymentMethod = paymentMethod, note = note, date = date, groupId = groupId,
            availableGroups = rawGroups.filterNot { it.isShared && it.isArchived }, isSplit = isSplit, splitMode = splitMode,
            paidByPersonId = paidBy, shares = shares, addedBy = addedBy, splitLocked = splitLocked,
            categories = Categories.categoriesFor(type, com.vaibhav.kharchapaani.domain.CategoryPrefs()),
            onTypeChange = {}, onAmountChange = {}, onMerchantChange = {}, onCategoryChange = {}, onPaymentMethodChange = {},
            onNoteChange = {}, onDateChange = {}, onGroupIdChange = {}, onIsSplitChange = {}, onSplitModeChange = {},
            onPaidByPersonIdChange = {}, onTogglePersonShare = {}, onExactAmountChange = { _, _ -> },
            onPercentageChange = { _, _ -> }, onOpenAddFriend = {}, onSave = {},
        )
    }

    /** Scrolls the screen's vertical list so the first node showing [text] starts [topDp] from the top. */
    private fun bringTo(text: String, topDp: Float) {
        compose.waitForIdle()
        val top = compose.onAllNodesWithText(text)[0].fetchSemanticsNode().boundsInRoot.top
        val density = compose.activity.resources.displayMetrics.density
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollAction())
            .performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, top - topDp * density) }
        compose.waitForIdle()
    }

    /** Waits until the fixture has reached the ViewModel through Room, with a sheet on screen. */
    private fun waitForFixture() {
        val loaded = runCatching {
            compose.waitUntil(10_000) {
                viewModel.groups.value.size == 1 && viewModel.rawTransactions.value.size == 4
            }
        }
        check(loaded.isSuccess) {
            "fixture not loaded: groups=${viewModel.groups.value.size} transactions=${viewModel.rawTransactions.value.size}"
        }
    }

    @Test
    fun `1 daily spending on Home`() {
        app(tab = 0)
        waitForText("Chai stall")
        // The feed is the point of this one: the Activity heading near the top, entries below it.
        bringTo("Today", topDp = 190f)
        waitForText("Bus")
        shoot("01-daily-spending")
    }

    @Test
    fun `2 adding an expense by hand`() {
        sheet { LiveConfirmSheet() }
        waitForFixture()
        // A fresh draft, typed in as someone would; nothing is saved, so the fixture isn't doubled.
        compose.runOnIdle {
            viewModel.openManualAdd("expense")
            viewModel.updateAmount("40")
            viewModel.updateMerchant("Chai stall")
            viewModel.updateCategory("Food")
            viewModel.updatePaymentMethod("Cash")
        }
        waitForText("Chai stall")
        shoot("02-add-an-expense")
    }

    @Test
    fun `3 the shared trip`() {
        sheet {
            val groups by viewModel.groups.collectAsState()
            val people by viewModel.people.collectAsState()
            groups.firstOrNull()?.let { trip ->
                GroupDetailContent(groupDetails = trip, onAddExpense = {}, onEditTransaction = {}, aiAvailable = viewModel.aiAvailable, people = people)
            }
        }
        waitForFixture()
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("tx_${StoreFixture.DINNER}")
        waitForText("Dinner")
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("header")
        waitForText("Ankit")
        // As it opens: the trip, what it cost, who was in it and who owes whom. The Dinner's own
        // split is screen 4.
        shoot("03-shared-trip")
    }

    @Test
    fun `4 the dinner's split`() {
        sheet { LiveConfirmSheet() }
        waitForFixture()
        compose.runOnIdle { viewModel.openEditTransaction(viewModel.rawTransactions.value.single { it.transaction.id == StoreFixture.DINNER }) }
        waitForText("Dinner")
        // The split is the point: who paid, equally, and the three ₹600 shares, with the total and
        // the Dinner in the bar below.
        bringTo("WHO'S IT FOR", topDp = 64f)
        shoot("04-split-details")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun `5 a payment to confirm`() {
        runBlocking { StoreFixture.rohanSaysHePaid(db) }
        // Tapping "Rohan paid you" on Splits opens its answer sheet over that screen. KpModalSheet
        // draws that sheet in its own window, which this capture can't see, so it's drawn here the
        // way KpModalSheet draws it: Material's default scrim, then the real answer content on the
        // sheet's surface, shape and handle.
        app(tab = 1) {
            val waiting by viewModel.settlementsAwaitingMe.collectAsState()
            waiting.firstOrNull()?.let { item ->
                Box(Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor))
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(KpShapes.sheet).background(CharcoalSurface),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SheetHandle()
                    SettlementAnswerContent(item = item, onConfirm = {}, onReject = {})
                }
            }
        }
        waitForText("Confirm")
        shoot("05-settle-up")
    }

    @Test
    fun `6 where the month went`() {
        app(tab = 2)
        waitForText("Groceries")
        shoot("06-monthly-insights")
    }
}
