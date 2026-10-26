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
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.GroupMemberEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitEntity
import com.vaibhav.kharchapaani.data.db.entity.SplitShareEntity
import com.vaibhav.kharchapaani.data.db.entity.SyncState
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Room history -> MainViewModel's draft -> saved shared expense. No network or fake history. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RecentSplitGroupTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val screen = CoroutineScope(SupervisorJob() + dispatcher)
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private val userId = "11111111-2222-3333-4444-555555555555"

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        db.personDao().insertPerson(PersonEntity(id = 1, name = "Rohan", avatarColor = "#70C1FF", profileId = "22222222-2222-3333-4444-555555555555"))
        db.personDao().insertPerson(PersonEntity(id = 2, name = "Ankit", avatarColor = "#B5EAD7", profileId = "33333333-2222-3333-4444-555555555555"))
        db.personDao().insertPerson(PersonEntity(id = 3, name = "Neha", avatarColor = "#D5A6BD"))
        for (id in listOf(10L, 20L, 30L, 40L, 50L)) {
            db.groupDao().insertGroup(GroupEntity(
                id = id, name = "Trip $id", syncState = SyncState.SYNCED,
                createdAt = id, archivedAt = if (id == 30L) 1L else null,
            ))
            val members = if (id == 40L) listOf(-1L, 1L) else listOf(-1L, 1L, 2L)
            db.groupDao().insertGroupMembers(members.map { GroupMemberEntity(groupId = id, personId = it) })
        }
        addHistory(1, groupId = 10, friend = 2, addedAt = 100, date = 500)
        // A newly added, backdated expense still makes Trip 20 the most recently used group.
        addHistory(2, groupId = 20, friend = 2, addedAt = 200, date = 1)
        addHistory(3, groupId = 30, friend = 2, addedAt = 300, date = 600)
        addHistory(4, groupId = 40, friend = 1, addedAt = 400, date = 700)
        // Legacy malformed income/split data must not count as shared expense history.
        addHistory(5, groupId = 10, friend = 2, addedAt = 10000, date = 800, type = "income")
        vm = MainViewModel(application, db, userId)
        store.put("main", vm)
        screen.launch { vm.people.collect {} }
        screen.launch { vm.groups.collect {} }
        screen.launch { vm.rawGroups.collect {} }
        until { vm.people.value.size == 3 && vm.groups.value.size == 5 && vm.rawGroups.value.size == 5 }
    }

    private suspend fun addHistory(id: Long, groupId: Long, friend: Long, addedAt: Long, date: Long, type: String = "expense") {
        db.transactionDao().insertTransaction(TransactionEntity(
            id = id, type = type, amount = 500.0, category = "Food", merchant = "History $id",
            paymentMethod = "Cash", date = date, createdAt = addedAt, groupId = groupId, isSplit = true,
        ))
        db.splitDao().insertSplit(SplitEntity(id = id, transactionId = id, totalAmount = 500.0, paidByPersonId = friend))
        db.splitDao().insertSplitShares(listOf(
            SplitShareEntity(splitId = id, personId = -1, shareAmount = 250.0),
            SplitShareEntity(splitId = id, personId = friend, shareAmount = 250.0),
        ))
    }

    private suspend fun until(condition: suspend () -> Boolean) = withTimeout(10000) {
        while (!condition()) {
            dispatcher.scheduler.runCurrent()
            delay(10)
        }
    }

    @After
    fun tearDown() = runBlocking {
        screen.cancel()
        val job = vm.viewModelScope.coroutineContext[Job]!!
        store.clear()
        // Wait for cancellation before closing Room, including queries currently on Dispatchers.IO.
        withTimeout(10000) {
            while (!job.isCompleted) {
                dispatcher.scheduler.runCurrent()
                delay(10)
            }
        }
        db.close()
        Dispatchers.resetMain()
    }

    private fun startWithAnkit() {
        vm.openManualAdd()
        vm.setIsSplit(true) // The existing first-friend shortcut selects Rohan.
        vm.togglePersonShare(1)
        vm.togglePersonShare(2)
    }

    @Test
    fun `selecting a friend defaults to the last active group they actually split in and saves there`() = runBlocking {
        startWithAnkit()
        assertEquals(20L, vm.groupId.value)
        assertEquals(setOf(-1L, 2L), vm.shares.value.filter { it.isSelected }.map { it.personId }.toSet())
        vm.updateAmount("500")
        vm.updateMerchant("New dinner")
        vm.saveCurrentTransaction()
        until { db.transactionDao().getAllTransactionsDirect().any { it.merchant == "New dinner" } }
        val saved = db.transactionDao().getAllTransactionsDirect().single { it.merchant == "New dinner" }
        assertEquals(20L, saved.groupId)
        val split = db.splitDao().getSplitByTransactionIdDirect(saved.id)!!
        assertEquals(500.0, db.splitDao().getSharesForSplitDirect(split.id).sumOf { it.shareAmount }, 0.001)
    }

    @Test
    fun `an explicit group is kept when the person selection changes`() {
        vm.openManualAdd()
        vm.updateGroupId(10)
        vm.setIsSplit(true)
        vm.togglePersonShare(1)
        vm.togglePersonShare(2)
        assertEquals(10L, vm.groupId.value)
    }

    @Test
    fun `choosing no group explicitly is not undone by picking a friend`() {
        vm.openManualAdd()
        vm.updateGroupId(null)
        vm.setIsSplit(true)
        vm.togglePersonShare(1)
        vm.togglePersonShare(2)
        assertNull(vm.groupId.value)
    }

    @Test
    fun `editing an existing expense never moves it to a more recent group`() = runBlocking {
        val existing = LedgerRepository(db).fullTransactions.first().single { it.transaction.id == 1L }
        vm.openEditTransaction(existing)
        vm.togglePersonShare(2)
        vm.togglePersonShare(2)
        assertEquals(10L, vm.groupId.value)
    }

    @Test
    fun `starting inside a group keeps that group`() {
        vm.openManualAddForGroup(10)
        vm.togglePersonShare(1)
        assertEquals(10L, vm.groupId.value)
    }

    @Test
    fun `a fresh draft can suggest a group after the previous draft chose none`() {
        vm.openManualAdd()
        vm.updateGroupId(null)
        startWithAnkit()
        assertEquals(20L, vm.groupId.value)
    }

    @Test
    fun `a person with no shared history gets no unrelated group`() {
        vm.openManualAdd()
        vm.setIsSplit(true)
        vm.togglePersonShare(1)
        vm.togglePersonShare(3)
        assertNull(vm.groupId.value)
    }
}
