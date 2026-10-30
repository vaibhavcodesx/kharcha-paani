package com.vaibhav.kharchapaani

import android.app.Application
import com.vaibhav.kharchapaani.domain.SplitTargets
import com.vaibhav.kharchapaani.ui.format.displayNotice
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
class SplitNoticeStringResourcesTest {
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
    @Test fun `resource split notices retain every existing sentence and user name`() {
        val names = listOf("Asha's 50%", "Rohan", "Priya")
        for (unknownCount in 0..3) for (leftCount in 0..3) {
            val target = SplitTargets.Target(unknownNames = names.take(unknownCount), ambiguousNames = listOf("Sam's 25%"))
            val left = names.take(leftCount)
            assertEquals(SplitTargets.notice(target, left, "Goa's 50% trip"), target.displayNotice(app, left, "Goa's 50% trip"))
        }
        assertNull(SplitTargets.Target().displayNotice(app))
        assertTrue(vm.people.value.any { it.id == 2L })
    }
}
