package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GroupCategoryToggleTest {
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
        db.splitDao().insertSplit(SplitEntity(id = id, transactionId = id, totalAmount = amount))
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

    private fun show(archived: Boolean = false): () -> Unit {
        val groups = runBlocking { LedgerRepository(db).groupsWithDetails.first() }
        val trip = groups.single { it.group.id == 7L }
        val details = mutableStateOf(if (archived) trip.copy(group = trip.group.copy(archivedAt = 1L)) else trip)
        compose.setContent {
            SplitFlowTheme {
                GroupDetailContent(
                    groupDetails = details.value,
                    onAddExpense = {},
                    onEditTransaction = vm::openEditTransaction,
                    aiAvailable = false,
                )
            }
        }
        return { details.value = groups.single { it.group.id == 8L } }
    }

    private fun toggle(category: String) {
        compose.onNodeWithTag("group_category_$category").performScrollTo()
            .assertHasClickAction().performClick()
    }

    private fun inCategory(category: String, row: String) =
        hasTestTag(row) and hasAnyAncestor(hasTestTag("group_category_entries_$category"))

    @Test
    fun `category opens matching group expenses and toggles closed or to another category`() {
        show()
        compose.onNodeWithTag("group_category_entries_Food").assertDoesNotExist()

        toggle("Food")

        compose.onAllNodes(hasAnyAncestor(hasTestTag("group_category_entries_Food")) and
            (hasTestTag("group_expense_1") or hasTestTag("group_expense_2"))).assertCountEquals(2)
        compose.onNode(inCategory("Food", "group_expense_3")).assertDoesNotExist()
        compose.onNode(inCategory("Food", "group_expense_4")).assertDoesNotExist()
        compose.onNode(inCategory("Food", "group_expense_5")).assertDoesNotExist()
        // Full group amounts, not just my one-third share.
        compose.onNode(hasTestTag("group_category_Food")).assertExists()
        compose.onNodeWithText("₹1,200").assertExists()

        toggle("Food")
        compose.onNodeWithTag("group_category_entries_Food").assertDoesNotExist()
        toggle("Food")
        toggle("Transport")
        compose.onNodeWithTag("group_category_entries_Food").assertDoesNotExist()
        compose.onNode(inCategory("Transport", "group_expense_3")).performScrollTo().assertIsDisplayed()
        compose.onNode(inCategory("Transport", "group_expense_1")).assertDoesNotExist()
    }

    @Test
    fun `an expanded expense opens the real editor and selection resets for another group`() {
        val switchGroup = show()
        toggle("Food")
        compose.onNode(inCategory("Food", "group_expense_1")).performScrollTo().performClick()
        assertEquals("Dinner", vm.merchant.value)
        assertEquals(7L, vm.groupId.value)

        compose.runOnIdle { switchGroup() }

        compose.onNodeWithTag("group_category_entries_Food").assertDoesNotExist()
        toggle("Food")
        compose.onNode(inCategory("Food", "group_expense_4")).performScrollTo().assertIsDisplayed()
        compose.onNode(inCategory("Food", "group_expense_1")).assertDoesNotExist()
    }
}
