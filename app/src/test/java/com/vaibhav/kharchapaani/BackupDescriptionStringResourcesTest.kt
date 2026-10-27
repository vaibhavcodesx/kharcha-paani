package com.vaibhav.kharchapaani

import android.app.Application
import com.vaibhav.kharchapaani.domain.*
import com.vaibhav.kharchapaani.data.repository.LocalBackupRepository
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
class BackupDescriptionStringResourcesTest {
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
    @Test fun `backup descriptions preserve invalid user data and report actual additive restore`(): Unit = runBlocking {
        val invalid = LocalBackup(exportedAt = 1, appVersion = "1", transactions = listOf(
            BackupTransaction(type = "expense", amount = -1.0, category = "Food", merchant = "Dinner's 50%",
                paymentMethod = "UPI", date = 1, createdAt = 1)))
        val result = LocalBackup.decode(app, LocalBackup.encode(invalid)) as BackupReadResult.Invalid
        assertEquals("an expense (Dinner's 50%) has no proper amount", result.problem)
        val valid = LocalBackup(exportedAt = 1, appVersion = "1", people = listOf(
            BackupPerson("Asha's 50%", "#70C1FF", createdAt = 1)))
        assertEquals("1 friend", valid.summary(app))
        val repo = LocalBackupRepository(db)
        assertEquals("Restored 1 item.", repo.restore(valid).message(app))
        assertEquals("Everything in that backup was already here. Nothing changed.", repo.restore(valid).message(app))
        assertEquals(2, db.personDao().getAllPeopleDirect().size)
    }
}
