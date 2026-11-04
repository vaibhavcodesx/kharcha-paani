package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.model.FullTransaction
import com.vaibhav.kharchapaani.data.model.GroupWithDetails
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class GroupDetailLazyTest {

    @get:Rule
    val compose = createComposeRule()

    private val priya = PersonEntity(id = 2, name = "Priya", avatarColor = "#70C1FF")
    private val rohan = PersonEntity(id = 3, name = "Rohan", avatarColor = "#B5EAD7")

    private fun expense(id: Long, amount: Double, paidBy: Long) = FullTransaction(
        TransactionEntity(
            id = id,
            type = "expense",
            amount = amount,
            category = "Food",
            merchant = "Cafe $id",
            paymentMethod = "UPI",
            date = 1700000000000L + (id * 1000L),
            groupId = 7,
            isSplit = true,
        ),
        split = SplitEntity(id = id, transactionId = id, totalAmount = amount, paidByPersonId = paidBy),
        shares = listOf(
            SplitShareEntity(splitId = id, personId = -1L, shareAmount = amount / 2),
            SplitShareEntity(splitId = id, personId = 2L, shareAmount = amount / 2),
        ),
    )

    @Test
    fun `long feed list renders with lazy column and scrolls to item by key`() {
        var addedExpenseGroupId: Long? = null
        var editedExpense: FullTransaction? = null
        val expenses = (1..60).map { i -> expense(i.toLong(), 200.0, paidBy = -1L) }
        val details = GroupWithDetails(
            group = GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️", syncState = SyncState.SYNCED),
            members = listOf(priya, rohan),
            totalExpenses = 12000.0,
            myPaidAmount = 12000.0,
            myShare = 6000.0,
            netBalanceForMe = 6000.0,
            transactionsCount = expenses.size,
            transactions = expenses,
            settlements = emptyList(),
        )

        compose.setContent {
            SplitFlowTheme {
                GroupDetailContent(
                    groupDetails = details,
                    onAddExpense = { addedExpenseGroupId = it },
                    onEditTransaction = { editedExpense = it },
                    onSettleUpWith = {},
                    onExportGroup = {},
                )
            }
        }

        // Top group header and figures exist
        compose.onNodeWithText("Goa trip").assertExists()

        // GroupFeed.of sorts newest-first, so Cafe 60 is at the top of the feed; scroll to it
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("tx_60")
        compose.onNodeWithText("Cafe 60").assertExists()

        // Scroll to the oldest expense (tx_1) using performScrollToKey
        compose.onNode(hasScrollToKeyAction()).performScrollToKey("tx_1")
        // Assert oldest row's displayed content and edit action
        compose.onNodeWithText("Cafe 1").assertExists()
        compose.onNodeWithTag("group_expense_1").performClick()
        assertEquals(1L, editedExpense?.transaction?.id)

        // Floating add button in action bar works
        compose.onNodeWithTag("add_group_expense_button").performClick()
        assertEquals(7L, addedExpenseGroupId)
    }

    @Test
    fun `empty feed renders empty state message inside lazy column`() {
        val details = GroupWithDetails(
            group = GroupEntity(id = 7, name = "Goa trip", emoji = "🏖️", syncState = SyncState.SYNCED),
            members = listOf(priya, rohan),
            totalExpenses = 0.0,
            myPaidAmount = 0.0,
            myShare = 0.0,
            netBalanceForMe = 0.0,
            transactionsCount = 0,
            transactions = emptyList(),
            settlements = emptyList(),
        )

        compose.setContent {
            SplitFlowTheme {
                GroupDetailContent(
                    groupDetails = details,
                    onAddExpense = {},
                    onEditTransaction = {},
                )
            }
        }

        compose.onNodeWithText("Goa trip").assertExists()
        compose.onNodeWithText("Activity · 0").assertExists()
        compose.onNodeWithText("Nothing logged here yet. Add the first expense below.").assertExists()
    }

    @Test
    fun `real room and viewmodel modal gathers balances and wires actions in app shell`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        val db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        val store = ViewModelStore()
        val userId = "11111111-2222-3333-4444-555555555555"
        val vm = MainViewModel(application, db, userId)
        store.put("main", vm)

        var addedExpenseGroupId: Long? = null
        var editedTx: FullTransaction? = null
        var settledPersonId: Long? = null
        var exportedGroupId: Long? = null

        try {
            // Seed database: people, group, group members, and split transaction
            runBlocking {
                db.personDao().insertPerson(priya)
                db.personDao().insertPerson(rohan)
                val group = GroupEntity(id = 7L, name = "Goa trip", emoji = "🏖️", syncState = SyncState.SYNCED)
                db.groupDao().insertGroup(group)
                db.groupDao().insertGroupMembers(listOf(
                    GroupMemberEntity(groupId = 7L, personId = -1L),
                    GroupMemberEntity(groupId = 7L, personId = 2L),
                    GroupMemberEntity(groupId = 7L, personId = 3L),
                ))
                // Insert ₹1,200 expense paid by Me (-1L), split 3 ways (₹400 each)
                db.transactionDao().insertTransaction(
                    TransactionEntity(
                        id = 10L,
                        type = "expense",
                        amount = 1200.0,
                        category = "Food",
                        merchant = "Beach Shack",
                        paymentMethod = "UPI",
                        date = 1700000000000L,
                        groupId = 7L,
                        isSplit = true,
                        syncState = SyncState.SYNCED,
                    )
                )
                db.splitDao().insertSplit(
                    SplitEntity(id = 10L, transactionId = 10L, totalAmount = 1200.0, paidByPersonId = -1L, splitType = "equal")
                )
                db.splitDao().insertSplitShares(listOf(
                    SplitShareEntity(splitId = 10L, personId = -1L, shareAmount = 400.0),
                    SplitShareEntity(splitId = 10L, personId = 2L, shareAmount = 400.0),
                    SplitShareEntity(splitId = 10L, personId = 3L, shareAmount = 400.0),
                ))

                // Wait until ViewModel's groups flow gathers seeded transactions, shares and figures from Room
                val goaDetails = vm.groups.first { list ->
                    val g = list.firstOrNull { it.group.id == 7L }
                    g != null && g.transactions.isNotEmpty() && g.myShare == 400.0 && g.totalExpenses == 1200.0
                }.single { it.group.id == 7L }
                vm.selectGroupForDetail(goaDetails)
            }

            compose.setContent {
                val selectedGroup by vm.selectedGroupForDetail.collectAsState()
                val people by vm.people.collectAsState()
                SplitFlowTheme {
                    GroupDetailSheet(
                        groupDetails = selectedGroup,
                        people = people,
                        onAddExpense = { addedExpenseGroupId = it },
                        onEditTransaction = { editedTx = it },
                        onSettleUpWith = { settledPersonId = it },
                        onExportGroup = { exportedGroupId = it },
                        onDismiss = { vm.dismissGroupDetail() },
                    )
                }
            }

            // Assert gathered balances from Room
            compose.onNodeWithText("Goa trip").assertExists()
            // Total group expense: SPENT ₹1,200
            compose.onNode(hasText("SPENT") and hasText("₹1,200")).assertExists()
            // My share: SHARE ₹400
            compose.onNode(hasText("SHARE") and hasText("₹400")).assertExists()

            // Verify action wiring
            // 1. Add expense button triggers onAddExpense
            compose.onNodeWithTag("add_group_expense_button").performClick()
            assertEquals(7L, addedExpenseGroupId)

            // 2. Scroll to and tap settle up button in debts section (Priya owes Me ₹400 -> "settle_2_-1")
            compose.onNode(hasScrollToKeyAction()).performScrollToKey("debts")
            compose.onNodeWithTag("settle_2_-1").performClick()
            assertEquals(2L, settledPersonId)

            // 3. Scroll to feed and tap expense row
            compose.onNode(hasScrollToKeyAction()).performScrollToKey("tx_10")
            compose.onNodeWithText("Beach Shack").assertExists()
            compose.onNodeWithTag("group_expense_10").performClick()
            assertEquals(10L, editedTx?.transaction?.id)

            // 4. Export button triggers onExportGroup
            compose.onNode(hasScrollToKeyAction()).performScrollToKey("feed_header")
            compose.onNodeWithTag("export_group_button").performClick()
            assertEquals(7L, exportedGroupId)

            // 5. Dismissal
            vm.dismissGroupDetail()
            compose.waitForIdle()
            compose.onNodeWithText("Goa trip").assertDoesNotExist()
        } finally {
            runBlocking {
                val job = vm.viewModelScope.coroutineContext[Job]!!
                store.clear()
                val deadline = System.currentTimeMillis() + 5000
                while (!job.isCompleted && System.currentTimeMillis() < deadline) {
                    ShadowLooper.idleMainLooper()
                }
                db.close()
            }
        }
    }
}
