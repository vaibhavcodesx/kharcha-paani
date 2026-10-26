package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.platform.LocalView
import org.robolectric.annotation.GraphicsMode
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room -> repository -> group UI, with the real edit view model at the callback boundary. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GroupMemberColourTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
            db.groupDao().insertGroup(GroupEntity(id = 7, name = "Trip"))
            db.groupDao().insertGroup(GroupEntity(id = 8, name = "Flat"))
            db.groupDao().insertGroupMembers(listOf(-1L, 2L).map { GroupMemberEntity(groupId = 7, personId = it) })
            expense(1, "Dinner", "Food", 900.0, 7)
            expense(2, "Breakfast", "Food", 300.0, 7)
            expense(3, "Taxi", "Transport", 600.0, 7)
            expense(4, "Other group's meal", "Food", 1200.0, 8)
            db.transactionDao().insertTransaction(TransactionEntity(
                id = 5, type = "income", amount = 5000.0, category = "Food",
                merchant = "Refund", paymentMethod = "Cash", date = 5, groupId = 7,
            ))
        }
        vm = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
    }

    private suspend fun expense(id: Long, merchant: String, category: String, amount: Double, group: Long) {
        db.transactionDao().insertTransaction(TransactionEntity(
            id = id, type = "expense", amount = amount, category = category,
            merchant = merchant, paymentMethod = "Cash", date = id, groupId = group, isSplit = true,
        ))
        db.splitDao().insertSplit(SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = if (id == 1L) 2 else -1))
        db.splitDao().insertSplitShares(listOf(
            SplitShareEntity(splitId = id, personId = -1, shareAmount = amount / 3),
            SplitShareEntity(splitId = id, personId = 2, shareAmount = amount * 2 / 3),
        ))
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private lateinit var root: View

    private fun show() {
        val details = runBlocking { LedgerRepository(db).groupsWithDetails.first() }.single { it.group.id == 7L }
        compose.setContent {
            root = LocalView.current
            SplitFlowTheme {
                GroupDetailContent(groupDetails = details, onAddExpense = {},
                    onEditTransaction = vm::openEditTransaction, aiAvailable = false)
            }
        }
    }

    private fun assertDot(tag: String, color: Int) {
        val dot = compose.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
        val center = dot.fetchSemanticsNode().boundsInRoot.center
        val pixel = compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
            root.draw(Canvas(bitmap))
            bitmap.getPixel(center.x.toInt(), center.y.toInt()).also { bitmap.recycle() }
        }
        assertEquals(color, pixel)
    }

    @Test
    fun `payer and split participants show their actual saved colours beside their names`() {
        show()
        assertDot("group_expense_1_payer_2", android.graphics.Color.parseColor("#70C1FF"))
        assertDot("group_expense_1_share_2", android.graphics.Color.parseColor("#70C1FF"))
        assertDot("group_expense_1_share_-1", android.graphics.Color.parseColor("#FF7A3D"))
        compose.onNode(hasTestTag("group_expense_1") and hasText("Rohan", substring = true)).assertExists()
        // Only people who take part in this expense: the other group's row never appears here.
        compose.onNodeWithTag("group_expense_4_payer_-1", useUnmergedTree = true).assertDoesNotExist()
    }
}
