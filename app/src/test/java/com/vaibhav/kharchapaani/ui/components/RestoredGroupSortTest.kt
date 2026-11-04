package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.repository.GeminiRepository
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.settings.GroupSortPreferences
import com.vaibhav.kharchapaani.domain.GroupSort
import com.vaibhav.kharchapaani.ui.screens.SplitsScreen
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.UUID

/** A durable restore updates a mounted screen, using actual Room groups and the real VM owner. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h2000dp")
class RestoredGroupSortTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val preferences = app.getSharedPreferences("group_list_preferences", Context.MODE_PRIVATE)
    private val main = StandardTestDispatcher()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private lateinit var vm: MainViewModel
    private var saved: Map<String, *>? = null
    private var installed = false

    @Before fun setup(): Unit = runBlocking {
        saved = preferences.all.toMap()
        assertTrue(preferences.edit().clear().commit())
        Dispatchers.setMain(main); installed = true
        WorkManagerTestInitHelper.initializeTestWorkManager(app,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        db.groupDao().insertGroup(GroupEntity(id = 31, name = "Zulu", createdAt = 1, updatedAt = 200))
        db.groupDao().insertGroup(GroupEntity(id = 32, name = "Alpha", createdAt = 2, updatedAt = 100))
        vm = MainViewModel(app, db, UUID.randomUUID().toString(), geminiRepo = GeminiRepository(configured = false), isLocal = true)
        store.put("main", vm)
        main.scheduler.runCurrent()
    }

    private fun order(): List<String> = compose.onAllNodes(SemanticsMatcher("group cards") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("group_card_") == true
    }).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }.map { it.config[SemanticsProperties.TestTag] }

    private fun awaitOrder(vararg ids: Long) {
        val expected = ids.map { "group_card_$it" }
        compose.waitUntil(10_000) {
            ShadowLooper.idleMainLooper()
            order() == expected
        }
        assertEquals(expected, order())
    }

    @Test fun `durably restoring a sort choice reorders real groups without remounting the screen`() {
        val groups = runBlocking { LedgerRepository(db).groupsWithDetails.first() }
        compose.setContent {
            SplitFlowTheme {
                SplitsScreen(personBalances = emptyList(), groups = groups,
                    onOpenAddFriend = {}, onDeleteFriend = {}, onOpenCreateGroup = {}, onJoinGroup = {},
                    onSelectGroup = {}, onDeleteGroup = {}, onOpenSimplifyDebts = {}, onOpenIouDialog = {},
                    onSettleUp = {}, onViewHistory = {})
            }
        }
        compose.onNodeWithTag("splits_tab_groups").performScrollTo().performClick()
        awaitOrder(31, 32)
        // Same public durable path used by complete restore; no synthetic recomposition/mount toggle.
        runBlocking { GroupSortPreferences(app).writeDurably(GroupSort.Name) }
        awaitOrder(32, 31)
        compose.runOnIdle { GroupSortPreferences(app).write(GroupSort.LatestActivity) }
        awaitOrder(31, 32)
    }

    @After fun close(): Unit = runBlocking {
        try {
            val scopeJob = if (::vm.isInitialized) vm.viewModelScope.coroutineContext[Job] else null
            store.clear()
            withTimeout(10_000) {
                while (scopeJob != null && !scopeJob.isCompleted) {
                    main.scheduler.runCurrent(); ShadowLooper.idleMainLooper(); delay(10)
                }
            }
            if (::vm.isInitialized) {
                val closingScope = CoroutineScope(SupervisorJob() + main)
                try {
                    val closing = closingScope.async { vm.awaitSessionClosingWork() }
                    withTimeout(10_000) {
                        while (!closing.isCompleted) { main.scheduler.runCurrent(); ShadowLooper.idleMainLooper(); delay(10) }
                    }
                    closing.await()
                } finally { closingScope.cancel() }
            }
        } finally {
            try {
                if (::db.isInitialized) db.close()
                saved?.let { values ->
                    val edit = preferences.edit().clear()
                    values.forEach { (key, value) -> if (value is String) edit.putString(key, value) }
                    assertTrue(edit.commit())
                }
            } finally { if (installed) Dispatchers.resetMain() }
        }
    }
}
