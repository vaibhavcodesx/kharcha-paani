package com.vaibhav.kharchapaani.ui.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.itemsplit.FileItemSplitStore
import com.vaibhav.kharchapaani.data.itemsplit.ItemSplitDraft
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.domain.SplitClaim
import com.vaibhav.kharchapaani.domain.SplitItem
import com.vaibhav.kharchapaani.domain.SplitPerson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Split by items through the real view model and a real ledger: the picks become the entry's split,
 * the saved expense moves each friend's balance by what they had, and the split ends with its entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ItemSplitEntryTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val main = UnconfinedTestDispatcher()
    private val store = ViewModelStore()
    private var mainDispatcherInstalled = false
    private val screen = CoroutineScope(Dispatchers.Unconfined + Job())
    private val draftStore = FileItemSplitStore.forLedger(application, LedgerDatabases.databaseName(userId))

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        mainDispatcherInstalled = true
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        draftStore.clear()
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        runBlocking { db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF")) }
    }

    /** The view model as the app shell holds it, with the flows the shell collects. */
    private fun start() {
        viewModel = MainViewModel(application, db, userId)
        store.put("main", viewModel)
        screen.launch { viewModel.people.collect {} }
        screen.launch { viewModel.groups.collect {} }
        screen.launch { viewModel.rawGroups.collect {} }
        screen.launch { viewModel.personBalances.collect {} }
        screen.launch { viewModel.pendingItemSplit.collect {} }
        until { viewModel.people.value.any { it.id == 1L } }
    }

    private fun until(condition: () -> Boolean) = runBlocking {
        withTimeout(10_000) {
            while (!condition()) {
                main.scheduler.runCurrent()
                delay(20)
            }
        }
    }

    @After
    fun tearDown(): Unit = runBlocking {
        screen.cancel()
        try {
            if (::viewModel.isInitialized) {
                val job = viewModel.viewModelScope.coroutineContext[Job]!!
                store.clear()
                withTimeout(10_000) {
                    while (!job.isCompleted) {
                        main.scheduler.runCurrent()
                        if (!job.isCompleted) delay(20)
                    }
                }
            }
        } finally {
            draftStore.clear()
            if (::db.isInitialized) db.close()
            if (mainDispatcherInstalled) Dispatchers.resetMain()
        }
    }

    /** ₹330 at Toit: Rohan's biryani ₹200, a naan ₹100 that Priya and I shared, ₹30 of tax. */
    private fun splitTheDinner() {
        viewModel.openManualAdd()
        viewModel.updateAmount("330")
        viewModel.updateMerchant("Toit")
        viewModel.openItemSplit("Vaibhav Chauhan")
        val split = viewModel.itemSplit
        split.saveItem(null, "Biryani", 1, 20_000)
        split.saveItem(null, "Naan", 2, 10_000)
        split.addPerson("Rohan", 1L)
        split.toggle(item("Biryani"))
        // Priya joined from the link: a name, and nobody in this ledger yet.
        split.addPerson("Priya")
        split.toggle(item("Naan"))
        split.setActive(draft().people.first { it.isHost }.id)
        split.toggle(item("Naan"))
    }

    private fun draft(): ItemSplitDraft = checkNotNull(viewModel.itemSplit.state.value.draft)
    private fun item(name: String): String = draft().items.first { it.name == name }.id

    @Test
    fun `the picks become the entry's split, and saving it moves each balance by what they had`() {
        start()
        splitTheDinner()
        assertEquals("friends see a first name, not the full one", "Vaibhav", draft().hostName)
        assertEquals("Toit", draft().title)

        viewModel.useItemSplit()
        until { viewModel.splitMode.value == SplitMode.EXACT && !viewModel.itemSplit.state.value.isOpen }

        val priya = runBlocking { db.personDao().getAllPeopleDirect() }.single { it.name == "Priya" }
        val shares = viewModel.shares.value.filter { it.isSelected }.associate { it.personId to it.amount }
        assertTrue(viewModel.isSplit.value)
        // Naan 50/50; ₹30 of tax follows the items: 5, 20 and 5.
        assertEquals(mapOf(-1L to 55.0, 1L to 220.0, priya.id to 55.0), shares)
        assertEquals("330", viewModel.amount.value)

        viewModel.saveCurrentTransaction(askForGroup = false)
        until { !viewModel.isConfirmSheetOpen.value }
        until { viewModel.personBalances.value.count { it.netBalance != 0.0 } == 2 }

        val balances = viewModel.personBalances.value.associate { it.person.name to it.netBalance }
        assertEquals("Rohan had the biryani", 220.0, balances.getValue("Rohan"), 0.001)
        assertEquals("Priya had half the naan", 55.0, balances.getValue("Priya"), 0.001)
        val saved = runBlocking { db.transactionDao().getAllTransactionsDirect() }.single()
        assertEquals(330.0, saved.amount, 0.001)
        assertEquals("Toit", saved.merchant)

        assertNull("saved: the split has done its job", viewModel.itemSplit.state.value.draft)
        until { viewModel.pendingItemSplit.value == null }
    }

    @Test
    fun `closing the entry without saving ends its split`() {
        start()
        splitTheDinner()
        viewModel.itemSplit.close()

        viewModel.dismissConfirmSheet()

        assertNull(viewModel.itemSplit.state.value.draft)
        assertFalse(viewModel.isConfirmSheetOpen.value)
    }

    @Test
    fun `income has no split by items`() {
        start()
        viewModel.openManualAdd("income")
        viewModel.openItemSplit("Vaibhav")

        assertNull(viewModel.itemSplit.state.value.draft)
        assertFalse(viewModel.itemSplit.state.value.isOpen)
    }

    @Test
    fun `a split left when the app was closed is offered on Home and opens back up with its entry`() {
        // What the last run of the app left behind: a bill mid-split.
        draftStore.save(
            ItemSplitDraft(
                splitId = "00000000-0000-4000-8000-000000000001",
                hostName = "Vaibhav",
                title = "Toit",
                billTotalPaise = 33_000,
                items = listOf(SplitItem("biryani", "Biryani", 1, 20_000)),
                people = listOf(SplitPerson("me", "Vaibhav", isHost = true, ledgerId = -1), SplitPerson("rohan", "Rohan", ledgerId = 1)),
                claims = listOf(SplitClaim("biryani", "rohan")),
                activePersonId = "me",
                startedAt = System.currentTimeMillis(),
            ),
        )
        start()
        until { viewModel.pendingItemSplit.value == "Toit" }

        viewModel.resumeItemSplit("Vaibhav")

        assertTrue(viewModel.isConfirmSheetOpen.value)
        assertEquals("330", viewModel.amount.value)
        assertEquals("Toit", viewModel.merchant.value)
        assertTrue(viewModel.itemSplit.state.value.isOpen)
        assertEquals("the picks are as they were left", 1, draft().claims.size)
        until { viewModel.pendingItemSplit.value == null }
    }

    @Test
    fun `discarding it from Home forgets it`() {
        draftStore.save(ItemSplitDraft(splitId = "00000000-0000-4000-8000-000000000002", hostName = "Vaibhav", title = "", startedAt = System.currentTimeMillis()))
        start()
        until { viewModel.pendingItemSplit.value == "" }

        viewModel.discardPendingItemSplit()

        until { viewModel.pendingItemSplit.value == null }
        until { draftStore.load() == null }
        assertNull(viewModel.itemSplit.state.value.draft)
    }
}
