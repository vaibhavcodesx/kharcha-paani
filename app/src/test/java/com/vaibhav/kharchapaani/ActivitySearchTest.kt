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
    private val screen = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    private val emissions = mutableListOf<List<ActivityItem>>()
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
        screen.launch { vm.activityFeed.collect { emissions.add(it) } }
        until { vm.activityFeed.value.size == 5 && vm.activityFeed.value.filterIsInstance<ActivityItem.Settlement>().single().title(app).contains("Rohan") }
    }

    private suspend fun until(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }

    private fun search(query: String): Set<String> = runBlocking {
        val initialCount = emissions.size
        vm.setSearchQuery(query)
        withTimeout(10000) {
            val start = System.currentTimeMillis()
            while (emissions.size == initialCount) {
                dispatcher.scheduler.runCurrent()
                delay(10)
                if (System.currentTimeMillis() - start > 1000 && vm.activityFeed.value.isNotEmpty()) break
            }
        }
        dispatcher.scheduler.runCurrent()
        vm.activityFeed.value.map { it.key }.toSet()
    }

    @After fun tearDown() = runBlocking {
        screen.cancel()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear(); until { job.isCompleted }
        db.close(); Dispatchers.resetMain()
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

    @Test fun `friend and amount searches still respect the selected filter`() {
        vm.setSelectedFilter(ActivityFeed.INCOME)
        assertTrue(search("Rohan").isEmpty())
        assertEquals(setOf("tx_2"), search("123456.78"))
        vm.setSelectedFilter(ActivityFeed.SPLITS)
        assertEquals(setOf("tx_1"), search("2469"))
        vm.setSelectedFilter(ActivityFeed.EXPENSES)
        assertTrue(search("2469").isEmpty())
    }

    @Test fun `merchant category note and clearing search still work`() {
        assertEquals(setOf("tx_2"), search("Salary"))
        assertEquals(setOf("tx_3"), search("Travel"))
        assertEquals(setOf("tx_4"), search("Morning chai"))
        assertEquals(5, search("").size)
    }

    @Test fun `search query is debounced by 150ms before feed updates`() = runBlocking {
        val beforeCount = emissions.size
        vm.setSearchQuery("Salary")
        dispatcher.scheduler.runCurrent()
        // Immediately after setting search query, feed is NOT yet filtered
        assertEquals(5, vm.activityFeed.value.size)
        assertEquals("No immediate emission within debounce window", beforeCount, emissions.size)

        // After the debounce duration, feed reflects the search result
        until { vm.activityFeed.value.size == 1 && emissions.size > beforeCount }
        assertEquals(setOf("tx_2"), vm.activityFeed.value.map { it.key }.toSet())
    }

    @Test fun `clearing search query updates immediately with zero debounce`() = runBlocking {
        search("Salary")
        assertEquals(setOf("tx_2"), vm.activityFeed.value.map { it.key }.toSet())

        val beforeCount = emissions.size
        val clearStartTime = System.currentTimeMillis()
        vm.setSearchQuery("")
        // Empty query has 0 ms debounce: observe immediate emission
        until { vm.activityFeed.value.size == 5 && emissions.size > beforeCount }
        val elapsed = System.currentTimeMillis() - clearStartTime
        assertTrue("Clearing search must emit without waiting for debounce ($elapsed ms < 150 ms)", elapsed < 150)
        assertEquals(5, vm.activityFeed.value.size)
    }

    @Test fun `rapid keystrokes within debounce window build feed only once after typing pauses`() = runBlocking {
        // Distinct intermediate queries that match completely different items:
        // "Tea" matches Morning chai (tx_4)
        // "Taxi" matches Taxi (tx_3)
        // "Salary" matches Salary (tx_2)
        val initialCount = emissions.size

        vm.setSearchQuery("Tea")
        delay(50)
        vm.setSearchQuery("Taxi")
        delay(50)
        vm.setSearchQuery("Salary")
        dispatcher.scheduler.runCurrent()

        // Keystrokes were <150ms apart: feed remains un-updated at initial state
        assertEquals(5, vm.activityFeed.value.size)
        assertEquals("No intermediate emissions during keystroke stream", initialCount, emissions.size)

        // Wait for debounce silence
        until { vm.activityFeed.value.size == 1 && emissions.size > initialCount }

        // Exactly one new emission occurred after typing paused
        val newEmissions = emissions.subList(initialCount, emissions.size)
        assertEquals("Feed should build and emit exactly once for the final query", 1, newEmissions.size)
        assertEquals(setOf("tx_2"), newEmissions.single().map { it.key }.toSet())
        // Prove neither intermediate query ("Tea" -> tx_4, "Taxi" -> tx_3) ever emitted
        assertFalse(newEmissions.any { list -> list.any { it.key == "tx_4" || it.key == "tx_3" } })
    }
}
