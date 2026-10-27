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
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.SplitMode
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EntryStringResourcesTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val screen = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel

    @Before fun setUp(): Unit = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(app, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
        vm = MainViewModel(app, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        screen.launch { vm.people.collect {} }
        until { vm.people.value.any { it.id == 2L } }
    }
    private suspend fun until(condition: () -> Boolean) = withTimeout(10000) {
        while (!condition()) { dispatcher.scheduler.runCurrent(); delay(10) }
    }
    @After fun tearDown(): Unit = runBlocking {
        screen.cancel()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear(); until { job.isCompleted }
        db.close(); Dispatchers.resetMain()
    }
    @Test fun `resource validation preserves the amount message and does not format user data`(): Unit = runBlocking {
        vm.openManualAdd("expense"); vm.updateAmount("100"); vm.updateMerchant("Dinner's 50% offer")
        vm.updateNote("Rohan's 50% share")
        vm.setIsSplit(true); vm.setSplitMode(SplitMode.EXACT)
        for (id in listOf(-1L, 2L)) {
            if (!vm.shares.value.first { it.personId == id }.isSelected) vm.togglePersonShare(id)
        }
        vm.updateExactShareAmount(-1, 30.0); vm.updateExactShareAmount(2, 50.0)
        vm.saveCurrentTransaction(false)
        assertEquals("₹20 still to assign", vm.statusMessage.value)
        assertTrue(db.transactionDao().getAllTransactions().first().isEmpty())
        vm.updateExactShareAmount(2, 70.0); vm.saveCurrentTransaction(false)
        until { vm.submitting.value.isEmpty() }
        val saved = db.transactionDao().getAllTransactions().first().single()
        assertEquals(100.0, saved.amount, 0.0)
        assertEquals("Dinner's 50% offer", saved.merchant)
        assertEquals("Rohan's 50% share", saved.note)
    }
}
