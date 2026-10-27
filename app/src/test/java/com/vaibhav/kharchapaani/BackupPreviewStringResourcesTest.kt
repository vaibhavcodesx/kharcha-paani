package com.vaibhav.kharchapaani

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.ui.components.RestoreBackupDialog
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
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
class BackupPreviewStringResourcesTest {
    @get:Rule val compose = createComposeRule()
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
    @Test fun `preview keeps counts and only restores after confirmation`(): Unit = runBlocking {
        val backup = LocalBackup(exportedAt = 0, appVersion = "1.5% test's",
            people = listOf(BackupPerson("Asha's 50%", "#70C1FF", createdAt = 1),
                BackupPerson("Ankit", "#70C1FF", createdAt = 2)))
        compose.setContent { SplitFlowTheme {
            RestoreBackupDialog(backup, onRestore = { screen.launch { LocalBackupRepository(db).restore(backup) } },
                onDismiss = {}, zone = java.time.ZoneId.of("UTC"))
        } }
        compose.onNodeWithText("2 friends.").assertExists()
        compose.onNodeWithText("Taken 1 Jan 1970, 12:00 AM, from version 1.5% test's.").assertExists()
        compose.onNodeWithText("Only what isn't on this phone already is added. Nothing here is changed or removed.").assertExists()
        assertEquals(1, db.personDao().getAllPeopleDirect().size)
        compose.onNodeWithTag("confirm_restore_button").performClick()
        until { vm.people.value.size == 3 }
        assertTrue(db.personDao().getAllPeopleDirect().any { it.name == "Asha's 50%" })
    }
    @Test fun `settings only preview retains its existing sentence`(): Unit = runBlocking {
        compose.setContent { SplitFlowTheme {
            RestoreBackupDialog(LocalBackup(exportedAt = 0, appVersion = "1"), {}, {})
        } }
        compose.onNodeWithText("Settings only.").assertExists()
        compose.onNodeWithText("Restore this backup?").assertExists()
        compose.onNodeWithText("Cancel").assertExists()
    }
}
