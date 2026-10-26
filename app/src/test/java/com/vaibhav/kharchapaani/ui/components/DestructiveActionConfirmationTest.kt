package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.domain.ActivityFeed
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real stored records and the production gatherer/deleter around the Compose confirmation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class DestructiveActionConfirmationTest {
    @get:Rule
    val compose = createComposeRule()
    private val dispatcher = StandardTestDispatcher()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var repository: LedgerRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = LedgerRepository(db)
        runBlocking {
            db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
            db.groupDao().insertGroup(GroupEntity(id = 7, name = "Flat"))
            db.transactionDao().insertTransaction(TransactionEntity(
                id = 1, type = "expense", amount = 500.0, category = "Food", merchant = "Groceries",
                paymentMethod = "Cash", date = 1, groupId = 7,
            ))
            db.settlementDao().insertSettlement(SettlementEntity(
                id = 5, fromPersonId = 2, toPersonId = -1, amount = 250.0, groupId = 7,
            ))
        }
        // Keep launch-path construction in this UI regression, as well as AppShellLaunchTest.
        store.put("main", MainViewModel(application, db, "11111111-2222-3333-4444-555555555555"))
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun settlementExists() = runBlocking { db.groupSyncDao().settlementById(5) != null }

    private fun showSettlement(isIou: Boolean) {
        val item = runBlocking {
            val existing = db.groupSyncDao().settlementById(5)!!
            db.groupSyncDao().updateSettlement(existing.copy(isIou = isIou))
            ActivityFeed.build(
                repository.fullTransactions.first(), repository.allSettlements.first(),
                repository.allPeople.first(), repository.allGroups.first(), "", ActivityFeed.ALL,
            ).filterIsInstance<ActivityItem.Settlement>().single()
        }
        compose.setContent {
            SplitFlowTheme {
                SettlementItemCard(item, onRetract = { runBlocking { repository.deleteSettlement(5) } })
            }
        }
    }

    private fun requestSettlementRemoval(label: String) {
        // Tapping an IOU toggles its details, so a second request (after Cancel) finds them open
        // already; tapping again would close them.
        if (compose.onAllNodesWithText("More options").fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithTag("settlement_item_5").performClick()
        }
        // Expanded IOUs expose the same removal menu through an explicit options button.
        if (compose.onAllNodesWithText("More options").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("More options").performClick()
        }
        compose.onNodeWithText(label).performClick()
    }

    private fun checkSettlementConfirmation(isIou: Boolean, label: String) {
        showSettlement(isIou)
        requestSettlementRemoval(label)
        assertEquals("choosing the menu item must not remove the stored record", true, settlementExists())
        compose.onNodeWithTag("remove_settlement_dialog").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(true, settlementExists())
        requestSettlementRemoval(label)
        compose.onNodeWithTag("confirm_remove_settlement").performClick()
        assertEquals(false, settlementExists())
    }

    @Test
    fun `retracting a settlement waits for confirmation and Cancel keeps the record`() {
        checkSettlementConfirmation(isIou = false, label = "Retract settlement")
    }

    @Test
    fun `deleting an IOU waits for confirmation and Cancel keeps the record`() {
        checkSettlementConfirmation(isIou = true, label = "Delete IOU")
    }

    private fun showGroup() {
        val group = runBlocking { repository.groupsWithDetails.first().single() }
        compose.setContent {
            SplitFlowTheme {
                TripCard(group, onOpen = {}, onDelete = { runBlocking { repository.deleteGroup(7) } })
            }
        }
    }

    private fun requestGroupRemoval() {
        compose.onNodeWithTag("group_card_7").performTouchInput { longClick() }
        compose.onNodeWithText("Delete Flat").performClick()
    }

    private fun assertGroupIntact() = runBlocking {
        assertNotNull("requesting deletion must not remove the group", db.groupDao().getGroupByIdDirect(7))
        assertEquals(7L, db.transactionDao().getTransactionByIdDirect(1)!!.groupId)
        assertEquals(7L, db.groupSyncDao().settlementById(5)!!.groupId)
    }

    @Test
    fun `group menu waits for confirmation and confirmed deletion preserves its records`() {
        showGroup()
        requestGroupRemoval()
        assertGroupIntact()
        compose.onNodeWithTag("delete_group_dialog").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertGroupIntact()
        requestGroupRemoval()
        compose.onNodeWithTag("confirm_delete_group").performClick()
        runBlocking {
            assertNull(db.groupDao().getGroupByIdDirect(7))
            assertNull(db.transactionDao().getTransactionByIdDirect(1)!!.groupId)
            assertNull(db.groupSyncDao().settlementById(5)!!.groupId)
        }
    }

    @Test
    fun `the accessibility delete action also waits for confirmation`() {
        showGroup()
        val action = compose.onNodeWithTag("group_card_7").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions].single { it.label == "Delete Flat" }
        compose.runOnIdle { action.action() }
        assertGroupIntact()
        compose.onNodeWithTag("delete_group_dialog").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        assertGroupIntact()
    }
}
