package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.entity.GroupEntity
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementEntity
import com.vaibhav.kharchapaani.data.db.entity.SettlementStatus
import com.vaibhav.kharchapaani.data.model.ActivityItem
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.domain.ActivityFeed
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import com.vaibhav.kharchapaani.ui.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Room -> the real feed gatherer -> the IOU card, including an explicit unknown recorder. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
// Native font metrics are required for the line-wrap/overflow assertions below.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class IouDetailsTest {
    @get:Rule
    val compose = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val store = ViewModelStore()
    private lateinit var db: AppDatabase
    private var answers = 0
    private var removals = 0
    private val note = "Asha's 50% groceries for the flat, including rice, vegetables and the cleaning supplies. " +
        "Rohan covered the extra snacks for the weekend too. Keep this entire note visible, " +
        "including this last sentence about the shared kitchen supplies."

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application, Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.personDao().insertPerson(PersonEntity(id = 2, name = "Rohan", avatarColor = "#70C1FF"))
            db.personDao().insertPerson(PersonEntity(id = 3, name = "Ankit", avatarColor = "#B5EAD7"))
            db.groupDao().insertGroup(GroupEntity(id = 7, name = "Flat"))
        }
        store.put("main", MainViewModel(application, db, "11111111-2222-3333-4444-555555555555"))
    }

    @After
    fun tearDown() {
        store.clear()
        db.close()
        Dispatchers.resetMain()
    }

    private fun show(recorder: Long? = 3, to: Long = -1, status: SettlementStatus = SettlementStatus.CONFIRMED) {
        val item = runBlocking {
            db.settlementDao().insertSettlement(SettlementEntity(
                id = 5, fromPersonId = 2, toPersonId = to, amount = 250.0, date = 1_792_925_400_000L,
                note = note, isIou = true, groupId = 7, recordedByPersonId = recorder, status = status,
            ))
            val repository = LedgerRepository(db)
            ActivityFeed.build(application,
                repository.fullTransactions.first(), repository.allSettlements.first(),
                repository.allPeople.first(), repository.allGroups.first(), "", ActivityFeed.ALL,
            ).filterIsInstance<ActivityItem.Settlement>().single()
        }
        compose.setContent {
            SplitFlowTheme {
                SettlementItemCard(item, onRetract = { removals++ }, onAnswer = { answers++ })
            }
        }
    }

    @Test
    fun `tapping an IOU reveals the entire note without truncation plus date and recorder`() {
        show()
        compose.onNodeWithTag("settlement_item_5").performClick()

        compose.onNodeWithTag("iou_note_5").assert(hasText(note))
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("iou_note_5").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue("the long note must wrap across lines (width=${layouts.single().size.width})", layouts.single().lineCount > 1)
        assertFalse("the note must not be ellipsized or clipped", layouts.single().hasVisualOverflow)
        compose.onNodeWithTag("iou_date_5").assert(hasText("2026", substring = true))
        compose.onNodeWithText("Recorded by Ankit").assertExists()
        assertEquals(0, removals)

        compose.onNodeWithTag("settlement_item_5").performClick()
        compose.onNodeWithTag("iou_note_5").assertDoesNotExist()
    }

    @Test
    fun `an entry with no saved recorder names nobody rather than guessing`() {
        show(recorder = null)
        compose.onNodeWithTag("settlement_item_5").performClick()

        compose.onNodeWithTag("iou_note_5").assertExists()
        compose.onNodeWithText("Recorded by", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Recorded by Rohan").assertDoesNotExist()
        compose.onNodeWithText("Recorded by you").assertDoesNotExist()
    }

    @Test
    fun `a groupmates IOU can be read without gaining a delete action`() {
        show(recorder = 2, to = 3)
        compose.onNodeWithTag("settlement_item_5").performClick()

        compose.onNodeWithTag("iou_note_5").assertExists()
        compose.onNodeWithText("More options").assertDoesNotExist()
        assertEquals(0, removals)
    }

    @Test
    fun `an IOU waiting for my answer keeps its confirmation action after opening the note`() {
        show(recorder = 2, status = SettlementStatus.PENDING)
        compose.onNodeWithTag("settlement_item_5").performClick()
        assertEquals(0, answers)
        compose.onNodeWithTag("iou_note_5").assertExists()
        compose.onNodeWithTag("iou_answer_5").performClick()
        assertEquals(1, answers)
    }
}
