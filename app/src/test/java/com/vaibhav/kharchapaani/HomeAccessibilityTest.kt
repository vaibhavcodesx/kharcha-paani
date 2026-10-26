package com.vaibhav.kharchapaani

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.ui.shell.AppTab
import com.vaibhav.kharchapaani.ui.shell.KpDock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.ui.home.BalanceHeader
import com.vaibhav.kharchapaani.ui.format.Money
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class HomeAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private lateinit var root: View

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
    }
    @After fun tearDown() = runBlocking {
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        withTimeout(10000) { while (!job.isCompleted) { dispatcher.scheduler.runCurrent(); delay(10) } }
        db.close(); Dispatchers.resetMain()
    }
    @Test fun `Home shows the full monthly amount as one fitting figure at double text size`() = runBlocking {
        val amount = 9999999.99
        db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = amount,
            category = "Food", merchant = "A large month", paymentMethod = "Cash"))
        compose.setContent {
            root = LocalView.current.rootView
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SplitFlowTheme {
                    val analytics by vm.monthlyAnalytics.collectAsState()
                    BalanceHeader(analytics.totalExpense, analytics.dailyStreak, "V", {})
                }
            }
        }
        compose.waitUntil(10000) {
            dispatcher.scheduler.runCurrent()
            compose.onAllNodesWithContentDescription(Money.format(amount)).fetchSemanticsNodes().isNotEmpty()
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(Money.format(amount), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("the whole monthly amount must fit", layouts.single().hasVisualOverflow)
        assertEquals(1, layouts.single().lineCount)
        compose.runOnIdle {
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val out = File("build/test-results/testDebugUnitTest/screens/home_font_2.png").apply { parentFile?.mkdirs() }
            out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Unit
        }
    }
}
