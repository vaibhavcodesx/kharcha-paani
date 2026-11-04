package com.vaibhav.kharchapaani.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vaibhav.kharchapaani.data.db.entity.PersonEntity
import com.vaibhav.kharchapaani.data.model.PersonLedgerItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PersonLedgerRecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `remember person id prevents creating new flows on recomposition`() {
        var flowCreationCount = 0
        fun getPersonLedgerFlow(personId: Long): Flow<List<PersonLedgerItem>> {
            flowCreationCount++
            return flowOf(emptyList())
        }

        var personState by mutableStateOf(PersonEntity(id = 1L, name = "Aaru"))
        var unrelatedRecompositionTrigger by mutableStateOf(0)

        compose.setContent {
            val person = personState
            // Matches MainActivity pattern: remember(person.id) { viewModel.getPersonLedgerFlow(person.id) }
            val ledgerFlow = remember(person.id) { getPersonLedgerFlow(person.id) }
            val items by ledgerFlow.collectAsState(initial = emptyList())

            // Read the trigger to force recomposition
            val trigger = unrelatedRecompositionTrigger
            if (trigger >= 0) {
                // Read items
                items.size
            }
        }

        compose.waitForIdle()
        assertEquals(1, flowCreationCount)

        // Trigger recomposition without changing person.id
        unrelatedRecompositionTrigger++
        compose.waitForIdle()
        assertEquals("Recomposition with same person ID must not create a new flow", 1, flowCreationCount)

        unrelatedRecompositionTrigger++
        compose.waitForIdle()
        assertEquals("Further recompositions with same person ID must not create a new flow", 1, flowCreationCount)

        // Change person.id
        personState = PersonEntity(id = 2L, name = "Rohan")
        compose.waitForIdle()
        assertEquals("Changing person ID must create a new flow for the new person", 2, flowCreationCount)
    }
}
