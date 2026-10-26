package com.vaibhav.kharchapaani.updates

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FlexibleUpdateTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private lateinit var manager: FakeAppUpdateManager
    private lateinit var controller: FlexibleUpdateController
    private val ready = mutableListOf<Boolean>()
    private val activity = Robolectric.buildActivity(ComponentActivity::class.java).create()
    private val launcher = activity.get().registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {}

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(application, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java).allowMainThreadQueries().build()
        vm = MainViewModel(application, db, "11111111-2222-3333-4444-555555555555")
        store.put("main", vm)
        activity.start().resume()
        manager = FakeAppUpdateManager(application)
        controller = FlexibleUpdateController(manager, launcher, ready::add)
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun offer() { manager.setUpdateAvailable(999); controller.resume(); idle() }
    private fun download() { offer(); manager.userAcceptsUpdate(); manager.downloadStarts(); manager.downloadCompletes(); idle() }

    @After
    fun tearDown() {
        controller.stop()
        activity.pause().stop().destroy()
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    @Test
    fun `an available update offers flexible consent and never an immediate flow`() {
        offer()
        assertTrue(manager.isConfirmationDialogVisible)
        assertFalse(manager.isImmediateFlowVisible)
        assertFalse(manager.isInstallSplashScreenVisible)
    }

    @Test
    fun `immediate only updates do not interrupt the app`() {
        manager.setUpdateAvailable(999, AppUpdateType.IMMEDIATE)
        controller.resume(); idle()
        assertFalse(manager.isConfirmationDialogVisible)
        assertFalse(manager.isImmediateFlowVisible)
    }

    @Test
    fun `declining an offer does not prompt again on resume`() {
        offer()
        assertTrue(manager.isConfirmationDialogVisible)
        manager.userRejectsUpdate()
        controller.resume(); idle()
        assertFalse(manager.isConfirmationDialogVisible)
    }

    @Test
    fun `a download waits for the explicit restart action`() {
        download()
        assertTrue(ready.lastOrNull() == true)
        assertFalse(manager.isInstallSplashScreenVisible)
        controller.complete(); idle()
        assertTrue(manager.isInstallSplashScreenVisible)
    }

    @Test
    fun `returning to a downloaded update restores the restart notice`() {
        download()
        controller.stop()
        ready.clear()
        controller = FlexibleUpdateController(manager, launcher, ready::add, wasOffered = true)
        controller.resume(); idle()
        assertTrue(ready.lastOrNull() == true)
        assertFalse(manager.isInstallSplashScreenVisible)
    }

    @Test
    fun `Play errors leave the real expense draft usable`() {
        manager.setInstallErrorCode(InstallErrorCode.ERROR_INTERNAL_ERROR)
        controller.resume(); idle()
        assertFalse(manager.isConfirmationDialogVisible)
        vm.openManualAdd()
        vm.updateAmount("50")
        assertTrue(vm.isConfirmSheetOpen.value)
        assertEquals("50", vm.amount.value)
        assertFalse(ready.contains(true))
    }
}
