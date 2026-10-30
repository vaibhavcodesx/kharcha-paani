package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsSelected
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
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
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
class GroupSettlementsViewTest {
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
            db.personDao().insertPerson(PersonEntity(id = 3, name = "Priya", avatarColor = "#B5EAD7"))
            db.groupDao().insertGroupMembers(listOf(-1L, 2L, 3L).map { GroupMemberEntity(groupId = 7, personId = it) })
            db.groupDao().insertGroupMembers(listOf(-1L, 2L).map { GroupMemberEntity(groupId = 8, personId = it) })
            for ((id, status) in listOf(10L to SettlementStatus.CONFIRMED, 11L to SettlementStatus.PENDING, 12L to SettlementStatus.REJECTED)) {
                db.settlementDao().insertSettlement(SettlementEntity(id = id, fromPersonId = 2, toPersonId = 3, amount = 100.0, date = id, groupId = 7, status = status))
            }
            db.settlementDao().insertSettlement(SettlementEntity(id = 13, fromPersonId = -1, toPersonId = 2, amount = 50.0, date = 13, groupId = 7, isIou = true))
            db.settlementDao().insertSettlement(SettlementEntity(id = 14, fromPersonId = -1, toPersonId = 2, amount = 50.0, date = 14, groupId = 8))
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
                GroupDetailContent(groupDetails = details.value, onAddExpense = {},
                    onEditTransaction = vm::openEditTransaction, aiAvailable = false)
            }
        }
        return { details.value = groups.single { it.group.id == 8L } }
    }

    private fun settlements() {
        compose.onNodeWithTag("group_history_settlements").performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick)
    }

    @Test
    fun `settlements view shows every status and both people without other groups or expenses`() {
        show()
        settlements()
        compose.onNodeWithTag("group_history_settlements").assertIsSelected()
        compose.onNodeWithText("Settlements · 4").assertExists()
        for (id in listOf(10, 11, 12, 13)) {
            compose.onNodeWithTag("group_settlement_$id").performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithTag("group_settlement_14").assertDoesNotExist()
        compose.onNodeWithTag("group_expense_1").assertDoesNotExist()
        compose.onNode(hasTestTag("group_settlement_10") and hasText("Confirmed", substring = true)).assertExists()
        compose.onNode(hasTestTag("group_settlement_11") and hasText("Waiting for confirmation", substring = true)).assertExists()
        compose.onNode(hasTestTag("group_settlement_12") and hasText("Not confirmed", substring = true)).assertExists()
        compose.onNode(hasTestTag("group_settlement_10") and hasText("Rohan paid Priya")).assertExists()
        compose.onNode(hasTestTag("group_settlement_13") and hasText("You lent Rohan")).assertExists()
        compose.onNodeWithTag("group_history_all").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag("group_expense_1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `archived history can be filtered and another group starts with all activity`() {
        val switchGroup = show(archived = true)
        settlements()
        compose.onNodeWithTag("group_settlement_10").performScrollTo().assertHasNoClickAction()
        compose.runOnIdle { switchGroup() }
        compose.onNodeWithTag("group_history_all").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("group_settlement_10").assertDoesNotExist()
        compose.onNodeWithTag("group_settlement_14").performScrollTo().assertIsDisplayed()
    }
}
