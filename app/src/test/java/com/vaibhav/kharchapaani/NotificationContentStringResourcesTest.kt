package com.vaibhav.kharchapaani

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.vaibhav.kharchapaani.reminder.SettlementReminders
import com.vaibhav.kharchapaani.domain.NotificationPrefs
import com.vaibhav.kharchapaani.domain.QuietHours
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.util.NotificationHelper
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
class NotificationContentStringResourcesTest {
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
    @Test fun `actual payment notification preserves exact copy and public redaction`(): Unit = runBlocking {
        val now = 1_800_000_000_000L
        db.settlementDao().insertSettlement(SettlementEntity(id = 1, fromPersonId = 2, toPersonId = -1,
            amount = 125.50, status = SettlementStatus.PENDING, recordedByPersonId = 2, createdAt = now))
        val manager = app.getSystemService(NotificationManager::class.java)
        try {
            assertEquals(1, SettlementReminders.remind(app, db, "11111111-2222-3333-4444-555555555555", now,
                prefs = NotificationPrefs(quietHours = QuietHours(enabled = false))))
            val notification = manager.activeNotifications.single().notification
            assertEquals("Rohan says they paid you ₹125.50", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertEquals("Confirm it, or say it isn't right. It doesn't count until you do.", notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
            assertEquals("A payment is waiting for your answer", notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            assertEquals("Payments to confirm", manager.getNotificationChannel(NotificationHelper.CHANNEL_SETTLEMENTS).name.toString())
        } finally { manager.cancelAll() }
    }
}
