package com.vaibhav.kharchapaani

import android.app.Application
import com.vaibhav.kharchapaani.ui.groups.SharedGroupsController
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.sync.GroupMembershipRemote
import com.vaibhav.kharchapaani.data.sync.GroupInvite
import com.vaibhav.kharchapaani.data.sync.InvitePreview
import java.io.IOException
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
class SharedGroupNoticeStringResourcesTest {
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
    @Test fun `offline group preview keeps its recovery message and writes nothing`(): Unit = runBlocking {
        val remote = object : GroupMembershipRemote {
            override suspend fun createInvite(groupRemoteId: String): GroupInvite = error("Not used")
            override suspend fun activeInvites(groupRemoteId: String): List<GroupInvite> = error("Not used")
            override suspend fun revokeInvite(code: String): Unit = error("Not used")
            override suspend fun previewInvite(code: String): InvitePreview? = throw IOException("offline")
            override suspend fun joinGroup(code: String): String = error("Not used")
            override suspend fun leaveGroup(groupRemoteId: String, selfProfileId: String): Unit = error("Not used")
        }
        val controller = SharedGroupsController(screen, LedgerRepository(db), remote,
            "11111111-2222-3333-4444-555555555555", requestSync = {},
            resolveText = { id, args -> app.getString(id, *args) })
        controller.openJoin("AB2CD3EF")
        until { controller.state.value.join?.error != null }
        assertEquals("You're offline. Connect and try again.", controller.state.value.join?.error)
        assertTrue(db.groupDao().getAllGroupsDirect().isEmpty())
    }
}
