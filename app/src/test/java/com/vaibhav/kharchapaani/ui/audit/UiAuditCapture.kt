package com.vaibhav.kharchapaani.ui.audit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.SplitFlowApp
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsViewModel
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Every screen of the app for the UI audit (docs/audits/ui-audit-2026-11.md), rendered from the
 * real app shell with [AuditFixture] in a real Room database and the real MainViewModel. Sheets and
 * dialogs are opened the way a tap opens them (the ViewModel's own open calls, or a click), and
 * every window on screen is drawn in order, so a sheet appears over the app with its own scrim, as
 * on a phone. Subclasses set the device: a Pixel-width phone, a small phone, the largest font.
 *
 * Output: build/test-results/testDebugUnitTest/ui-audit/<device>/<screen>.png (CI's
 * unit-test-results artifact). This is an audit tool, not a regression test: it only asserts that
 * each screen rendered.
 */
abstract class UiAuditCapture(private val device: String, private val seeded: Boolean = true) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    protected lateinit var viewModel: MainViewModel
    private var started = false

    /** An AI that's switched on but never answers: enough for the notice and the Ask chips to show. */
    private val gemini = GeminiRepository(
        client = OkHttpClient.Builder().addInterceptor { throw java.io.IOException("offline in the audit") }.build(),
        endpoint = { model -> "https://example.invalid/v1beta/models/$model:generateContent" },
        accessToken = { "audit-token" },
        configured = true,
    )

    private fun start(tab: Int = 0) {
        if (!started) {
            WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
            db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
            if (seeded) runBlocking { AuditFixture.seed(db) }
            viewModel = MainViewModel(application, db, AuditFixture.USER_ID, geminiRepo = gemini)
            started = true
        }
        viewModel.setTab(tab)
        val sharedGroups = SharedGroupsViewModel(application, db, AuditFixture.USER_ID)
        compose.setContent {
            SplitFlowTheme {
                SplitFlowApp(
                    viewModel = viewModel,
                    sharedGroups = sharedGroups.controller,
                    selfProfileId = AuditFixture.USER_ID,
                    authUiState = AuditFixture.authUiState,
                    onRetryProfile = {},
                    onSaveProfile = { _, _ -> },
                    onSignOut = {},
                )
            }
        }
        // Wait for the ledger, not for one row's text: the fixture keeps today's entries before
        // now, so in the morning the later ones move above a given row and, on a short screen,
        // push it out of the lazy feed. The account button is on every tab once it's drawn.
        if (seeded) compose.waitUntil(10_000) { viewModel.rawTransactions.value.isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_account_button").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @After
    fun tearDown() {
        if (started) db.close()
    }

    private fun waitFor(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    /** Scrolls the topmost vertical list by [fraction] of the screen height. */
    private fun scrollDown(fraction: Float) {
        compose.waitForIdle()
        val height = compose.activity.window.decorView.height
        val list = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasScrollAction()
        // A new user's Home has no data to wait for, so its list can still be composing here.
        compose.waitUntil(10_000) { compose.onAllNodes(list).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(list).onFirst()
            .performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy -> scrollBy(0f, height * fraction) }
        compose.waitForIdle()
    }

    /** Every window on screen, bottom first (the activity, then any sheet or dialog), drawn as one image. */
    private fun shoot(screen: String) {
        compose.waitForIdle()
        val file = compose.runOnIdle {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
            @Suppress("UNCHECKED_CAST")
            val roots = (global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global) as List<View>)
                .filter { it.isShown && it.width > 0 && it.height > 0 }
            val base = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            roots.forEach { root ->
                val at = IntArray(2).also(root::getLocationOnScreen)
                canvas.save()
                canvas.translate(at[0].toFloat(), at[1].toFloat())
                root.draw(canvas)
                canvas.restore()
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val rgb = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_RGB)
            rgb.setRGB(0, 0, bitmap.width, bitmap.height, pixels, 0, bitmap.width)
            File("build/test-results/testDebugUnitTest/ui-audit/$device/$screen.png").apply {
                parentFile?.mkdirs()
                ImageIO.write(rgb, "png", this)
            }
        }
        assertTrue(file.length() > 0)
    }

    private fun group(id: Long) = viewModel.groups.value.first { it.group.id == id }
    private fun person(id: Long) = viewModel.people.value.first { it.id == id }

    // ---- Tabs ------------------------------------------------------------------------------

    @Test fun `home 1 top`() { start(0); shoot("home-1-top") }
    @Test fun `home 2 feed`() { start(0); scrollDown(0.55f); shoot("home-2-feed") }
    @Test fun `home 3 feed further`() { start(0); scrollDown(1.3f); shoot("home-3-feed-further") }
    @Test fun `splits 1 friends`() { start(1); shoot("splits-1-friends") }
    @Test fun `splits 2 friends scrolled`() { start(1); scrollDown(0.6f); shoot("splits-2-friends-scrolled") }
    @Test fun `splits 3 groups`() {
        start(1)
        compose.onNodeWithTag("splits_tab_groups").performClick()
        shoot("splits-3-groups")
    }
    @Test fun `insights 1 top`() { start(2); shoot("insights-1-top") }
    @Test fun `insights 2 scrolled`() { start(2); scrollDown(0.7f); shoot("insights-2-scrolled") }
    @Test fun `insights 3 bottom`() { start(2); scrollDown(1.6f); shoot("insights-3-bottom") }

    // ---- Sheets and dialogs, opened as a tap opens them -----------------------------------

    @Test fun `sheet add expense`() { start(); compose.runOnIdle { viewModel.openManualAdd() }; shoot("sheet-add-expense") }
    @Test fun `sheet add expense split`() {
        start()
        compose.runOnIdle {
            viewModel.openManualAdd()
            viewModel.updateAmount("1200")
            viewModel.updateMerchant("Lunch")
            viewModel.setIsSplit(true)
        }
        scrollDown(0.5f)
        shoot("sheet-add-expense-split")
    }
    @Test fun `sheet edit group expense`() { assumeTrue("needs the lived-in account", seeded);
        start()
        compose.runOnIdle { viewModel.openEditTransaction(viewModel.rawTransactions.value.first { it.transaction.id == AuditFixture.DINNER }) }
        shoot("sheet-edit-group-expense")
    }
    @Test fun `sheet group detail`() { assumeTrue("needs the lived-in account", seeded); start(1); compose.runOnIdle { viewModel.selectGroupForDetail(group(AuditFixture.GOA)) }; shoot("sheet-group-detail") }
    @Test fun `sheet group detail archived`() { assumeTrue("needs the lived-in account", seeded); start(1); compose.runOnIdle { viewModel.selectGroupForDetail(group(AuditFixture.OLD_TRIP)) }; shoot("sheet-group-detail-archived") }
    @Test fun `sheet settle up`() { assumeTrue("needs the lived-in account", seeded); start(1); compose.runOnIdle { viewModel.openSettleUp(person(AuditFixture.LONG_NAME)) }; shoot("sheet-settle-up") }
    @Test fun `sheet payment to answer`() { assumeTrue("needs the lived-in account", seeded); start(1); compose.runOnIdle { viewModel.openSettlementAnswer(AuditFixture.ROHANS_PAYMENT) }; waitFor("says"); shoot("sheet-payment-to-answer") }
    @Test fun `sheet iou`() { start(1); compose.runOnIdle { viewModel.openIouDialog() }; shoot("sheet-iou") }
    @Test fun `sheet simplify debts`() { start(1); compose.runOnIdle { viewModel.openSimplifyDebts() }; shoot("sheet-simplify-debts") }
    @Test fun `sheet person ledger`() { assumeTrue("needs the lived-in account", seeded); start(1); compose.runOnIdle { viewModel.openPersonLedger(person(AuditFixture.ROHAN)) }; shoot("sheet-person-ledger") }
    @Test fun `sheet add friend`() { start(1); compose.runOnIdle { viewModel.openAddFriend() }; shoot("sheet-add-friend") }
    @Test fun `sheet create group`() { start(1); compose.runOnIdle { viewModel.openCreateGroup() }; shoot("sheet-create-group") }
    @Test fun `sheet categories`() { start(); compose.runOnIdle { viewModel.openCategories() }; shoot("sheet-categories") }
    @Test fun `sheet subscriptions`() { start(); compose.runOnIdle { viewModel.openSubscriptions() }; shoot("sheet-subscriptions") }
    @Test fun `sheet export`() { start(2); compose.runOnIdle { viewModel.openExport() }; shoot("sheet-export") }
    @Test fun `sheet upi`() { start(); compose.runOnIdle { viewModel.openUpi() }; shoot("sheet-upi") }
    @Test fun `sheet account`() { start(); compose.onNodeWithTag("home_account_button").performClick(); shoot("sheet-account") }
    @Test fun `dialog ai notice`() { start(); compose.runOnIdle { viewModel.parseInputText("dinner 1200 with rohan") }; shoot("dialog-ai-notice") }
}

