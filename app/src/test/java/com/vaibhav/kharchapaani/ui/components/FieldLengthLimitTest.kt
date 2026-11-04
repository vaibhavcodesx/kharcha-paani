package com.vaibhav.kharchapaani.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.repository.LedgerRepository
import com.vaibhav.kharchapaani.data.repository.MAX_LOCAL_NAME_LENGTH
import com.vaibhav.kharchapaani.ui.theme.SplitFlowTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * REL L9: Verifies that UI input fields enforce maxLength constraints matching the server-side
 * database limits in supabase/migrations/ (1..64 for local_name, <=200 for settlement reject note).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FieldLengthLimitTest {

    @get:Rule
    val compose = createComposeRule()

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: AppDatabase
    private lateinit var ledgerRepo: LedgerRepository
    private var mainDispatcherInstalled = false

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mainDispatcherInstalled = true
        WorkManagerTestInitHelper.initializeTestWorkManager(
            application,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        db = Room.inMemoryDatabaseBuilder(application, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        ledgerRepo = LedgerRepository(db, onLocalTransactionChange = {}, selfProfileId = "self-test-uuid")
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (mainDispatcherInstalled) Dispatchers.resetMain()
    }

    @Test
    fun `SheetTextInput ignores typing beyond maxLength`() {
        var textValue = ""
        compose.setContent {
            SplitFlowTheme {
                var text by remember { mutableStateOf("") }
                Row {
                    SheetTextInput(
                        value = text,
                        onValueChange = {
                            text = it
                            textValue = it
                        },
                        placeholder = "Type here",
                        maxLength = 5,
                        fieldTestTag = "capped_input",
                    )
                }
            }
        }

        compose.onNodeWithTag("capped_input").performTextInput("12345")
        assertEquals("12345", textValue)

        compose.onNodeWithTag("capped_input").performTextInput("6")
        assertEquals("12345", textValue)
    }

    @Test
    fun `SheetTextInput clamps pasted input to maxLength`() {
        var textValue = ""
        compose.setContent {
            SplitFlowTheme {
                var text by remember { mutableStateOf("") }
                Row {
                    SheetTextInput(
                        value = text,
                        onValueChange = {
                            text = it
                            textValue = it
                        },
                        placeholder = "Paste here",
                        maxLength = 5,
                        fieldTestTag = "paste_input",
                    )
                }
            }
        }

        compose.onNodeWithTag("paste_input").performTextInput("abcdefghij")
        assertEquals("abcde", textValue)
    }

    @Test
    fun `AddFriendSheet caps friend name to 64 characters`() {
        var submittedName = ""
        compose.setContent {
            SplitFlowTheme {
                AddFriendContent(
                    taken = emptySet(),
                    onAdd = { name, _ -> submittedName = name },
                )
            }
        }

        val longName = "A".repeat(80)
        compose.onNodeWithTag("add_friend_name_input").performTextInput(longName)
        compose.onNodeWithTag("confirm_add_friend_button").performClick()

        assertEquals(64, submittedName.length)
        assertEquals("A".repeat(64), submittedName)
    }

    @Test
    fun `LedgerRepository addPerson caps name to 64 characters`() = runBlocking {
        val longName = "B".repeat(100)
        val id = ledgerRepo.addPerson(longName, "#70C1FF")

        val person = db.personDao().getAllPeopleDirect().single { it.id == id }
        assertEquals(MAX_LOCAL_NAME_LENGTH, person.name.length)
        assertEquals(64, person.name.length)
        assertEquals("B".repeat(64), person.name)
    }
}
