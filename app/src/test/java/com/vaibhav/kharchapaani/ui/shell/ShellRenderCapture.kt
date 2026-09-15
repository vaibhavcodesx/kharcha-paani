package com.vaibhav.kharchapaani.ui.shell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.ui.components.CategoryHelper
import com.vaibhav.kharchapaani.ui.components.ScreenHeader
import com.vaibhav.kharchapaani.ui.components.TransactionItemCard
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
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

/**
 * Renders the app shell over sample rows and saves a PNG with the test results, so each PR's CI
 * artifact shows what the change looks like. There's no emulator path past Google sign-in.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ShellRenderCapture {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val rows = CategoryHelper.allCategories.mapIndexed { i, category ->
        FullTransaction(
            TransactionEntity(
                id = i + 1L,
                type = if (category.startsWith("Income")) "income" else "expense",
                amount = listOf(34.5, 1200.0, 89.0, 15400.75, 250.0)[i % 5],
                category = category,
                merchant = category.substringBefore(" ("),
                paymentMethod = "UPI",
                date = 0L,
            ),
        )
    }

    @Test
    fun `dock over the activity list`() {
        lateinit var root: View
        composeTestRule.setContent {
            root = LocalView.current
            SplitFlowTheme {
                val hazeState = rememberHazeState()
                Box(Modifier.fillMaxSize().background(CharcoalBg)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().hazeSource(hazeState).padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = dockClearance()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item { ScreenHeader(title = "Splits", supporting = "Who owes whom, with friends and on trips") }
                        items(rows + rows) { TransactionItemCard(it, onEdit = {}, onDelete = {}) }
                    }
                    CompositionLocalProvider(LocalHazeState provides hazeState) {
                        KpDock(
                            currentTab = AppTab.Splits,
                            onSelectTab = {},
                            onAdd = {},
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        // captureToImage waits for a redraw that Robolectric's paused looper never delivers.
        val image = composeTestRule.runOnIdle {
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
        }
        val out = File("build/test-results/testDebugUnitTest/screens/dock.png").apply { parentFile?.mkdirs() }
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 0)
    }
}
