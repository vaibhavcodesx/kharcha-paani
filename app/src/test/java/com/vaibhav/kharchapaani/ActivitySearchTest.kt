package com.vaibhav.kharchapaani

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.*
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.domain.ActivityFeed
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivitySearchTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val screen = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = ViewModelStore()
    private val feedEmissions = Channel<List<ActivityItem>>(Channel.BUFFERED)
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel

    @Before fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        db.personDao().insertPerson(PersonEntity(id = 3, name = "Meera", avatarColor = "#FF9070", profileId = "meera"))
        db.transactionDao().insertTransactions(listOf(
            TransactionEntity(id = 1, type = "expense", amount = 2469.0, category = "Food", merchant = "Lunch", paymentMethod = "Cash", isSplit = true),
            TransactionEntity(id = 2, type = "income", amount = 123456.78, category = "Income (Client)", merchant = "Salary", paymentMethod = "Cash"),
            TransactionEntity(id = 3, type = "expense", amount = 100.0, category = "Travel", merchant = "Taxi", paymentMethod = "Cash", isSplit = true, ownerProfileId = "meera"),
            TransactionEntity(id = 4, type = "expense", amount = 25.0, category = "Food", merchant = "Tea", note = "Morning chai", paymentMethod = "Cash"),
        ))
        db.splitDao().insertSplit(SplitEntity(id = 1, transactionId = 1, totalAmount = 2469.0, paidByPersonId = -1))
        db.splitDao().insertSplitShares(listOf(SplitShareEntity(splitId = 1, personId = -1, shareAmount = 1234.5),
            SplitShareEntity(splitId = 1, personId = 2, shareAmount = 1234.5)))
        db.splitDao().insertSplit(SplitEntity(id = 3, transactionId = 3, totalAmount = 100.0, paidByPersonId = 3))
        db.splitDao().insertSplitShares(listOf(SplitShareEntity(splitId = 3, personId = -1, shareAmount = 50.0),
            SplitShareEntity(splitId = 3, personId = 3, shareAmount = 50.0, guestName = "Ankit")))
        db.settlementDao().insertSettlement(SettlementEntity(id = 1, fromPersonId = 2, toPersonId = -1, amount = 1234.5))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        screen.launch { vm.activityFeed.collect { feedEmissions.send(it) } }

        // Initial emission with 5 items
        val initial = withTimeout(10_000) { feedEmissions.receive() }
        assertEquals(5, initial.size)
        assertTrue(initial.filterIsInstance<ActivityItem.Settlement>().single().title(app).contains("Rohan"))
    }

    private fun search(query: String): Set<String> = runBlocking {
        vm.setSearchQuery(query)
        val items = withTimeout(10_000) { feedEmissions.receive() }
        items.map { it.key }.toSet()
    }

    @After fun tearDown() = runBlocking {
        screen.cancel()
        feedEmissions.close()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        withTimeout(10_000) {
            while (!job.isCompleted) {
                delay(20)
            }
        }
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `names find only related shares payers owners and named guests`() {
        assertEquals(setOf("tx_1", "settlement_1"), search("ROHAN"))
        assertEquals(setOf("tx_3"), search("Meera"))
        assertEquals(setOf("tx_3"), search("Ankit"))
        assertTrue(search("Someone absent").isEmpty())
    }

    @Test fun `amounts accept rupees Indian grouping paisa and the shown personal share`() {
        for (query in listOf("1234.5", "₹1,234.50", "-₹1,234.50"))
            assertEquals(query, setOf("tx_1", "settlement_1"), search(query))
        assertEquals(setOf("tx_1"), search("2,469"))
        assertEquals(setOf("tx_2"), search("₹1,23,456.78"))
    }

    @Test fun `friend and amount searches still respect the selected filter`() = runBlocking {
        vm.setSelectedFilter(ActivityFeed.INCOME)
        withTimeout(10_000) { feedEmissions.receive() }
        assertTrue(search("Rohan").isEmpty())
        assertEquals(setOf("tx_2"), search("123456.78"))

        vm.setSelectedFilter(ActivityFeed.SPLITS)
        withTimeout(10_000) { feedEmissions.receive() }
        assertEquals(setOf("tx_1"), search("2469"))

        vm.setSelectedFilter(ActivityFeed.EXPENSES)
        withTimeout(10_000) { feedEmissions.receive() }
        assertTrue(search("2469").isEmpty())
    }

    @Test fun `merchant category note and clearing search still work`() {
        assertEquals(setOf("tx_2"), search("Salary"))
        assertEquals(setOf("tx_3"), search("Travel"))
        assertEquals(setOf("tx_4"), search("Morning chai"))
        assertEquals(5, search("").size)
    }

    @Test fun `search query is debounced by 150ms before feed updates`() = runBlocking {
        val start = System.currentTimeMillis()
        vm.setSearchQuery("Salary")

        // Immediately after setting query, no emission has arrived yet
        delay(50)
        assertTrue("Feed must not update before 150ms debounce window", feedEmissions.tryReceive().isFailure)

        // Debounced emission arrives after >= 150ms
        val items = withTimeout(10_000) { feedEmissions.receive() }
        val elapsed = System.currentTimeMillis() - start
        assertTrue("Emission should wait for at least debounce window ($elapsed >= 140)", elapsed >= 140)
        assertEquals(setOf("tx_2"), items.map { it.key }.toSet())
    }

    @Test fun `clearing search query updates immediately with zero debounce`() = runBlocking {
        assertEquals(setOf("tx_2"), search("Salary"))

        val clearStartTime = System.currentTimeMillis()
        vm.setSearchQuery("")
        val cleared = withTimeout(10_000) { feedEmissions.receive() }
        val elapsed = System.currentTimeMillis() - clearStartTime
        assertTrue("Empty search query must emit before the 150ms debounce threshold ($elapsed ms < 150 ms)", elapsed < 150)
        assertEquals(5, cleared.size)
    }

    @Test fun `rapid keystrokes within debounce window build feed only once after typing pauses`() = runBlocking {
        // Distinct keystrokes within 50ms (< 150ms debounce)
        vm.setSearchQuery("Tea")
        delay(50)
        vm.setSearchQuery("Taxi")
        delay(50)
        vm.setSearchQuery("Salary")

        // No emission should have arrived yet during typing
        assertTrue("No emission during rapid typing", feedEmissions.tryReceive().isFailure)

        // Only after typing pauses does the debounced emission arrive
        val finalFeed = withTimeout(10_000) { feedEmissions.receive() }
        assertEquals(setOf("tx_2"), finalFeed.map { it.key }.toSet())
        assertTrue("No extra intermediate emissions", feedEmissions.tryReceive().isFailure)
    }
}
