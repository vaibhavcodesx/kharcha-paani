package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.vaibhav.kharchapaani.ui.screens.SplitsScreen
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
@Config(sdk = [36], qualifiers = "w411dp-h2000dp")
class GroupSortingTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel

    @Before
    fun setUp() {
        application.getSharedPreferences("group_list_preferences", 0).edit().clear().commit()
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
            db.groupDao().insertGroup(GroupEntity(id = 7, name = "Trip", createdAt = 10, updatedAt = 300))
            db.groupDao().insertGroup(GroupEntity(id = 8, name = "Flat", createdAt = 20, updatedAt = 200))
            db.groupDao().insertGroup(GroupEntity(id = 9, name = "Annex", createdAt = 30, updatedAt = 100))
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
            merchant = merchant, paymentMethod = "Cash", date = id, createdAt = id, updatedAt = 0, groupId = group, isSplit = true,
        ))
        db.splitDao().insertSplit(SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = if (group == 8L) 2 else -1))
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

    private val mounted = mutableStateOf(true)

    private fun show() {
        val groups = runBlocking { LedgerRepository(db).groupsWithDetails.first() }
        compose.setContent {
            SplitFlowTheme {
                if (mounted.value) SplitsScreen(
                    personBalances = emptyList(), groups = groups,
                    onOpenAddFriend = {}, onDeleteFriend = {}, onOpenCreateGroup = {}, onJoinGroup = {},
                    onSelectGroup = {}, onDeleteGroup = {}, onOpenSimplifyDebts = {}, onOpenIouDialog = {},
                    onSettleUp = {}, onViewHistory = {},
                )
            }
        }
        groupsTab()
    }

    private fun groupsTab() {
        compose.onNodeWithTag("splits_tab_groups").performScrollTo().performClick()
    }

    private fun sort(label: String) {
        compose.onNodeWithTag("group_sort_button").performScrollTo().performClick()
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun assertOrder(vararg ids: Long) {
        val rows = compose.onAllNodes(SemanticsMatcher("group cards") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("group_card_") == true
        }).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        assertEquals(ids.map { "group_card_$it" }, rows.map { it.config[SemanticsProperties.TestTag] })
    }

    @Test
    fun `group sort options order real balances names and activity`() {
        show()
        // Opening the picker also proves the default mode is available.
        sort("Latest activity")
        assertOrder(7, 8, 9)
        sort("Name")
        assertOrder(9, 8, 7)
        sort("Owed to you")
        assertOrder(7, 9, 8)
        sort("You owe")
        assertOrder(8, 9, 7)
        sort("Newest")
        assertOrder(9, 8, 7)
    }

    @Test
    fun `the chosen sort survives leaving and recreating the screen`() {
        show()
        sort("Name")
        assertOrder(9, 8, 7)
        compose.runOnIdle { mounted.value = false }
        compose.runOnIdle { mounted.value = true }
        groupsTab()
        compose.onNodeWithTag("group_sort_button").assertExists()
        assertOrder(9, 8, 7)
    }
}
