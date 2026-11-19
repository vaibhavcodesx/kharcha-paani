package com.vaibhav.kharchapaani

import android.app.Application
import android.content.Context
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.db.entity.TransactionEntity
import com.vaibhav.kharchapaani.data.remote.SupabaseClientProvider
import com.vaibhav.kharchapaani.data.settings.GuestSession
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import com.vaibhav.kharchapaani.ui.session.AccountSessionHolder
import com.vaibhav.kharchapaani.ui.session.AccountSessionState
import com.vaibhav.kharchapaani.ui.session.AccountSession
import com.vaibhav.kharchapaani.ui.session.CloudAuthOwner
import com.vaibhav.kharchapaani.ui.session.SessionClosingWork
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

/** Real MainActivity + both real account ViewModels + a persisted Room ledger on a cold local launch. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class GuestActivityLaunchTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var guest: GuestSession
    private var previousMode: Map<String, *> = emptyMap<String, Any?>()
    private val jobs = mutableListOf<Job>()
    private val sessions = mutableListOf<AccountSession>()
    private val closingWork = mutableListOf<SessionClosingWork>()
    private val prefs get() = app.getSharedPreferences(LedgerMode.PREFS, Context.MODE_PRIVATE)

    private val localChoice = object : ExternalResource() {
        override fun before() = runBlocking {
            previousMode = prefs.all.toMap()
            assertTrue(prefs.edit().clear().commit())
            SupabaseClientProvider.closeExisting()
            LedgerDatabases.close()
            WorkManagerTestInitHelper.initializeTestWorkManager(app,
                Configuration.Builder().setExecutor(SynchronousExecutor()).build())
            guest = LedgerMode(app).useLocal()
            val db = LedgerDatabases.openLocal(app, guest)
            db.transactionDao().insertTransaction(TransactionEntity(type = "expense", amount = 125.50,
                category = "Food", merchant = "Guest lunch", paymentMethod = "Cash", date = System.currentTimeMillis()))
            LedgerDatabases.close()
        }

        override fun after() = runBlocking {
            try {
                val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                try {
                    val closing = cleanupScope.async {
                        sessions.forEach { it.awaitClosed() }
                        closingWork.forEach { it.awaitSessionClosingWork() }
                    }
                    val deadline = System.nanoTime() + 10_000_000_000L
                    while ((jobs.any { !it.isCompleted } || !closing.isCompleted) && System.nanoTime() < deadline) {
                        shadowOf(Looper.getMainLooper()).idle()
                    }
                    check(jobs.all { it.isCompleted } && closing.isCompleted) { "Activity ViewModels did not finish cleanup" }
                    closing.await()
                } finally { cleanupScope.cancel() }
                LedgerDatabases.close()
                SupabaseClientProvider.closeExisting()
                if (::guest.isInitialized) app.deleteDatabase(LedgerDatabases.localDatabaseName(guest))
            } finally {
                val edit = prefs.edit().clear()
                previousMode.forEach { (key, value) -> if (value is String) edit.putString(key, value) }
                assertTrue(edit.commit())
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(localChoice).around(compose)

    @Test fun `cold local Activity opens the actual saved ledger without constructing the cloud client`() = runBlocking {
        try {
            compose.waitUntil(10_000) {
                compose.onAllNodesWithTag("capture_ai_status").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Guest lunch").assertExists()
            compose.onNodeWithTag("capture_ai_status").assertTextEquals("AI is off · quick add stays on this phone  See what AI adds")
            val holder = ViewModelProvider(compose.activity)[AccountSessionHolder::class.java]
            val session = (holder.state.value as AccountSessionState.Ready).session
            assertTrue(session.isLocal)
            assertEquals(guest.id, session.userId)
            assertEquals(125.50, session.database.transactionDao().getAllTransactionsDirect().single().amount, 0.0)
            assertFalse(app.getDatabasePath(LedgerDatabases.databaseName(guest.id)).exists())
            val instance = SupabaseClientProvider::class.java.getDeclaredField("instance").apply { isAccessible = true }
            assertNull("Local Activity startup must not even construct Supabase", instance.get(null))
            verifyHiddenEditorDoesNotRecomposeHome(ViewModelProvider(session)[MainViewModel::class.java])
        } finally {
            // Capture scopes even on a failed assertion, before the Activity rule destroys stores.
            val holder = ViewModelProvider(compose.activity)[AccountSessionHolder::class.java]
            val session = (holder.state.value as? AccountSessionState.Ready)?.session
            if (session != null) {
                sessions += session
                jobs += session.scopeJobs
            }
            holder.viewModelScope.coroutineContext[Job]?.let(jobs::add)
            closingWork += holder
            // Known activity owners only; this never asks CloudAuthOwner for an auth ViewModel.
            val cloudOwner = ViewModelProvider(compose.activity)[CloudAuthOwner::class.java]
            cloudOwner.viewModelScope.coroutineContext[Job]?.let(jobs::add)
        }
    }

    @OptIn(ExperimentalComposeRuntimeApi::class)
    private fun verifyHiddenEditorDoesNotRecomposeHome(viewModel: MainViewModel) {
        val passes = AtomicInteger()
        val observers = mutableMapOf<ObservableComposition, CompositionObserverHandle>()
        val registrations = compose.runOnIdle {
            Recomposer.runningRecomposers.value.mapNotNull { recomposer ->
                recomposer.observe(object : CompositionRegistrationObserver {
                    override fun onCompositionRegistered(composition: ObservableComposition) {
                        observers[composition] = composition.setObserver(object : CompositionObserver {
                            override fun onBeginComposition(composition: ObservableComposition) { passes.incrementAndGet() }
                            override fun onEndComposition(composition: ObservableComposition) = Unit
                            override fun onScopeEnter(scope: RecomposeScope) = Unit
                            override fun onScopeExit(scope: RecomposeScope) = Unit
                            override fun onScopeDisposed(scope: RecomposeScope) = Unit
                            override fun onReadInScope(scope: RecomposeScope, value: Any) = Unit
                            override fun onScopeInvalidated(scope: RecomposeScope, value: Any?) = Unit
                        })
                    }

                    override fun onCompositionUnregistered(composition: ObservableComposition) {
                        observers.remove(composition)?.dispose()
                    }
                })
            }
        }
        try {
            assertTrue("the Home composition was observed", registrations.isNotEmpty() && observers.isNotEmpty())
            passes.set(0)
            repeat(4) { index ->
                compose.runOnIdle { viewModel.updateMerchant("Hidden draft $index") }
                compose.waitForIdle()
            }
            val count = passes.get()
            println("Home composition passes for 4 updates to the closed editor: $count")
            assertEquals("a closed editor should not invalidate Home", 0, count)
        } finally {
            compose.runOnIdle {
                registrations.forEach { it.dispose() }
                observers.values.forEach { it.dispose() }
            }
        }
    }
}
