package com.vaibhav.kharchapaani.ui.store

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.R
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.ui.components.BrandMark
import com.vaibhav.kharchapaani.ui.components.GroupDetailContent
import com.vaibhav.kharchapaani.ui.theme.CharcoalBg
import com.vaibhav.kharchapaani.ui.theme.CharcoalBorder
import com.vaibhav.kharchapaani.ui.theme.CharcoalSurface
import com.vaibhav.kharchapaani.ui.theme.KpShapes
import com.vaibhav.kharchapaani.ui.theme.MutedWarmGray
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.imageio.ImageIO

/**
 * The Play feature graphic (docs/store-listing/feature-graphic.md): 1024×500 px, opaque. Laid out
 * at 512×250 dp, the size the brief says to check it at, and drawn at 2× (xhdpi).
 *
 * Left, the app's own mark (the logo on sign-in and the widget, and the wordmark), its name and
 * the headline. Right, the real group sheet for the listing's Weekend trip, drawn by the shipped
 * composable from the fixture and shown smaller: the trip, its ₹1,800 and the three-way split.
 * Nothing on it is painted on or invented.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w512dp-h250dp-land-xhdpi")
class FeatureGraphicCapture {

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
        runBlocking {
            StoreFixture.seed(db)
            viewModel = MainViewModel(application, db, StoreFixture.USER_ID)
            viewModel.groups.first { it.isNotEmpty() }
        }
        store.put("main", viewModel)
    }

    @After
    fun tearDown() {
        StoreFixture.stopViewModels(store, if (::viewModel.isInitialized) viewModel else null)
        if (::db.isInitialized) db.close()
    }

    @Test
    fun `feature graphic`() {
        compose.setContent {
            SplitFlowTheme {
                Row(
                    Modifier.fillMaxSize().background(CharcoalBg).padding(horizontal = 30.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(0.52f)) {
                        Image(painterResource(R.drawable.ic_kharcha_logo), contentDescription = null, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(14.dp))
                        BrandMark(
                            Modifier
                                .graphicsLayer { scaleX = 1.9f; scaleY = 1.9f; transformOrigin = TransformOrigin(0f, 0.5f) }
                                .padding(vertical = 6.dp),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Everyday spending.", style = MaterialTheme.typography.headlineLarge)
                        Text("Shared plans.", style = MaterialTheme.typography.headlineLarge, color = MutedWarmGray)
                    }
                    Spacer(Modifier.size(16.dp))
                    TripCrop(Modifier.weight(0.48f))
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Weekend trip").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Ankit").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()

        val file = compose.runOnIdle { StoreFixture.save(compose.activity.window.decorView, "feature-graphic") }
        val image = ImageIO.read(file)
        assertEquals(1024, image.width)
        assertEquals(500, image.height)
        assertFalse("opaque: Play takes no alpha", image.colorModel.hasAlpha())
    }

    /**
     * The top of the real group sheet for Weekend trip: its name, what it cost and who was in it,
     * down to the end of the split chart. Scaled so the sheet's full phone width fits the card:
     * no amount is cut off at an edge.
     */
    @androidx.compose.runtime.Composable
    private fun TripCrop(modifier: Modifier) {
        val groups by viewModel.groups.collectAsState()
        val people by viewModel.people.collectAsState()
        BoxWithConstraints(modifier) {
            val sheetWidth = 412.dp
            val scale = maxWidth / sheetWidth
            // From the top of the sheet's content to just past the "Who spent what" chart, with a
            // little headroom so the trip's icon doesn't touch the card's edge.
            val headroom = 12.dp
            val shownHeight = 336.dp + headroom
            Box(
                Modifier
                    .size(maxWidth, shownHeight * scale)
                    .clip(KpShapes.large)
                    .background(CharcoalSurface)
                    .border(1.dp, CharcoalBorder, KpShapes.large),
            ) {
                groups.firstOrNull()?.let { trip ->
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(sheetWidth, 900.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = TransformOrigin(0f, 0f)
                                translationY = headroom.toPx() * scale
                            },
                    ) {
                        GroupDetailContent(groupDetails = trip, onAddExpense = {}, onEditTransaction = {}, aiAvailable = false, people = people)
                    }
                }
            }
        }
    }
}
