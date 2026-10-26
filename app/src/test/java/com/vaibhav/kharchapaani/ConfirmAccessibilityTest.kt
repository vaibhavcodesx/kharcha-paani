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
import com.vaibhav.kharchapaani.ui.components.GlassActionBar
import com.vaibhav.kharchapaani.ui.format.Money
import kotlinx.coroutines.flow.first
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
class ConfirmAccessibilityTest {
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
    @Test fun `the confirm action shows every digit at double size and still saves once`() = runBlocking {
        val amount = 9999999.99
        vm.openManualAdd("expense"); vm.updateAmount(Money.input(amount)); vm.updateMerchant("A large bill")
        compose.setContent {
            root = LocalView.current.rootView
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SplitFlowTheme {
                    GlassActionBar(label = "Save", enabled = true, summary = Money.format(amount),
                        buttonTestTag = "save_large_entry", onClick = { vm.saveCurrentTransaction(false) })
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(Money.format(amount), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(com.vaibhav.kharchapaani.ui.theme.MoneyText.title.color, layouts.single().layoutInput.style.color)
        assertFalse("confirmation money must not ellipsize", layouts.single().hasVisualOverflow)
        assertEquals(1, layouts.single().lineCount)
        compose.runOnIdle {
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val out = File("build/test-results/testDebugUnitTest/screens/confirm_font_2.png").apply { parentFile?.mkdirs() }
            out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("save_large_entry").performClick()
        withTimeout(10000) {
            while (vm.submitting.value.isNotEmpty()) { dispatcher.scheduler.runCurrent(); delay(10) }
        }
        val saved = db.transactionDao().getAllTransactions().first()
        assertEquals(1, saved.size)
        assertEquals(amount, saved.single().amount, 0.0)
    }
}
