package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonLedgerRecompositionTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val userId = "11111111-2222-3333-4444-555555555555"
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        viewModel = MainViewModel(application, db, userId)
        store.put("main", viewModel)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        try {
            if (::viewModel.isInitialized) {
                val job = viewModel.viewModelScope.coroutineContext[Job]!!
                store.clear()
                withTimeout(10_000) {
                    while (!job.isCompleted) {
                        delay(20)
                    }
                }
                assertTrue("Fixture must stop MainViewModel before Room closes", job.isCompleted)
            }
        } finally {
            if (::db.isInitialized) db.close()
        }
    }

    @Test
    fun `repository creates fresh flow query on every call without remember`() = runBlocking {
        db.personDao().insertPerson(PersonEntity(id = 10, name = "Aman", avatarColor = "#70C1FF"))

        // Without remember, consecutive calls create brand-new Flow instances and rebuild query pipelines
        val flow1 = viewModel.getPersonLedgerFlow(10)
        val flow2 = viewModel.getPersonLedgerFlow(10)
        assertNotSame("Repository returns a fresh query Flow on each invocation", flow1, flow2)

        // Verify contents of the real Flow backed by Room
        val items = flow1.first()
        assertTrue("Initial ledger items flow evaluates without errors", items.isEmpty())
    }

    @Test
    fun `remember person id and viewModel preserves ledger flow across unrelated recomposition`() {
        // 1. Seed two real persons and a split transaction in Room
        val person1 = PersonEntity(id = 10, name = "Aman", avatarColor = "#70C1FF")
        val person2 = PersonEntity(id = 20, name = "Rohan", avatarColor = "#FF9070")
        db.personDao().insertPersons(listOf(person1, person2))
        db.transactionDao().insertTransaction(
            TransactionEntity(
                id = 100,
                type = "expense",
                amount = 400.0,
                category = "Food",
                merchant = "Biryani",
                paymentMethod = "UPI",
                date = System.currentTimeMillis(),
                isSplit = true,
            )
        )
        db.splitDao().insertSplit(SplitEntity(id = 1, transactionId = 100, totalAmount = 400.0, paidByPersonId = -1))
        db.splitDao().insertSplitShares(listOf(
            SplitShareEntity(splitId = 1, personId = -1, shareAmount = 200.0),
            SplitShareEntity(splitId = 1, personId = 10, shareAmount = 200.0),
        ))

        var currentPerson by mutableStateOf(person1)
        var unrelatedTrigger by mutableStateOf(0)
        var flowEvaluations = 0

        compose.setContent {
            val person = currentPerson
            // Exact production pattern in MainActivity: remember(person.id, viewModel)
            val ledgerFlow = remember(person.id, viewModel) {
                flowEvaluations++
                viewModel.getPersonLedgerFlow(person.id)
            }
            val ledgerItems by ledgerFlow.collectAsState(initial = emptyList())

            PersonLedgerSheet(
                person = person,
                ledgerItems = ledgerItems,
                onSettleUp = {},
                onDismiss = {},
            )

            // Read unrelated state to trigger recomposition
            val dummy = unrelatedTrigger
            if (dummy < 0) println(dummy)
        }

        compose.waitForIdle()
        assertEquals("Initial flow obtained once", 1, flowEvaluations)
        compose.onNodeWithText("Aman").assertIsDisplayed()

        // 2. Trigger unrelated recomposition
        unrelatedTrigger++
        compose.waitForIdle()
        assertEquals("Unrelated recomposition must NOT re-evaluate ledger flow", 1, flowEvaluations)
        compose.onNodeWithText("Aman").assertIsDisplayed()

        unrelatedTrigger++
        compose.waitForIdle()
        assertEquals("Subsequent recompositions retain the same remembered flow", 1, flowEvaluations)

        // 3. Select another person
        currentPerson = person2
        compose.waitForIdle()
        assertEquals("Selecting another person evaluates new flow for that person", 2, flowEvaluations)
        compose.onNodeWithText("Rohan").assertIsDisplayed()
    }
}
