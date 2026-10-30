package com.vaibhav.kharchapaani

import android.app.Application
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import com.vaibhav.kharchapaani.widget.WidgetSummary
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import com.vaibhav.kharchapaani.widget.QuickAddWidgetProvider
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
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
class WidgetStringResourcesTest {
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
    @Test fun `widgets retain copy and complete amounts from a real saved entry`(): Unit = runBlocking {
        vm.openManualAdd("expense"); vm.updateAmount("1234.50"); vm.updateMerchant("Dinner's 50%")
        vm.saveCurrentTransaction(false)
        until { vm.submitting.value.isEmpty() }
        val totals = LedgerRepository(db).monthlyAnalytics.first()
        val summary = WidgetSummary.of(app, totals.totalExpense, totals.totalIncome, 0.0, todaySpent = 1234.50)
        val card = SplitFlowWidgetProvider.render(app, summary).apply(app, FrameLayout(app))
        assertEquals("Spent this month: ₹1,234.50", card.findViewById<ImageView>(R.id.widget_month_spent_figure).contentDescription.toString())
        assertEquals("All square", card.findViewById<TextView>(R.id.widget_net_owed_badge).text.toString())
        val bar = QuickAddWidgetProvider.render(app, summary).apply(app, FrameLayout(app))
        assertEquals("What did you spend on?", bar.findViewById<TextView>(R.id.quick_hint).text.toString())
        assertEquals("₹1,234.50", bar.findViewById<TextView>(R.id.quick_today).text.toString())
        assertEquals("Log an expense", bar.contentDescription.toString())
    }
}
