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
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.ui.components.TripCard
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
class SplitsAccessibilityTest {
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
    @Test fun `a group card grows for double size text and keeps every money value whole`() = runBlocking {
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        db.groupDao().insertGroup(GroupEntity(id = 7, name = "Holiday with friends"))
        db.groupDao().insertGroupMembers(listOf(-1L, 2L).map { GroupMemberEntity(groupId = 7, personId = it) })
        db.transactionDao().insertTransaction(TransactionEntity(id = 1, type = "expense", amount = 12000000.0,
            merchant = "Trip", category = "Travel", paymentMethod = "Cash", groupId = 7, isSplit = true))
        db.splitDao().insertSplit(SplitEntity(id = 1, transactionId = 1, totalAmount = 12000000.0, paidByPersonId = -1))
        db.splitDao().insertSplitShares(listOf(SplitShareEntity(splitId = 1, personId = -1, shareAmount = 5000000.0),
            SplitShareEntity(splitId = 1, personId = 2, shareAmount = 7000000.0)))
        val group = LedgerRepository(db).groupsWithDetails.first().single()
        var opened = false
        compose.setContent {
            root = LocalView.current.rootView
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                SplitFlowTheme { TripCard(group, onOpen = { vm.selectGroupForDetail(group); opened = true }, onDelete = {}) }
            }
        }
        for (amount in listOf(group.totalExpenses, group.myShare, group.netBalanceForMe)) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(Money.format(amount), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse("full amount must fit: $amount", layouts.single().hasVisualOverflow)
            assertEquals("money stays on one line", 1, layouts.single().lineCount)
        }
        compose.runOnIdle {
            val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(image))
            val out = File("build/test-results/testDebugUnitTest/screens/splits_font_2.png").apply { parentFile?.mkdirs() }
            out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithTag("group_card_7").performClick()
        assertTrue(opened)
    }
}
