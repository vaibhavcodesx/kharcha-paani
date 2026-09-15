package com.vaibhav.kharchapaani.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingDeletesTest {

    @Test
    fun `a staged delete hides the row without touching the ledger`() = runTest {
        val deleted = mutableListOf<Long>()
        val pending = PendingDeletes(backgroundScope) { deleted += it }

        pending.hide(7)
        runCurrent()

        assertEquals(setOf(7L), pending.ids.value)
        assertEquals(emptyList<Long>(), deleted)
    }

    @Test
    fun `undo brings the row back and nothing is ever deleted`() = runTest {
        val deleted = mutableListOf<Long>()
        val pending = PendingDeletes(backgroundScope) { deleted += it }

        pending.hide(7)
        pending.undo(7)
        pending.commit(7) // the snackbar reports dismissal after its action ran
        pending.commitAll()
        runCurrent()

        assertEquals(emptySet<Long>(), pending.ids.value)
        assertEquals(emptyList<Long>(), deleted)
    }

    @Test
    fun `commit deletes once, however many times it's asked`() = runTest {
        val deleted = mutableListOf<Long>()
        val pending = PendingDeletes(backgroundScope) { deleted += it }

        pending.hide(7)
        pending.commit(7)
        pending.commit(7)
        pending.commitAll()
        runCurrent()

        assertEquals(listOf(7L), deleted)
    }

    @Test
    fun `the row stays hidden until the ledger has actually deleted it`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val pending = PendingDeletes(backgroundScope) { gate.await() }

        pending.hide(7)
        pending.commit(7)
        runCurrent()
        assertEquals(setOf(7L), pending.ids.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(emptySet<Long>(), pending.ids.value)
    }

    @Test
    fun `a delete that fails brings the row back`() = runTest {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, e -> errors += e },
        )
        val pending = PendingDeletes(scope) { error("database closed") }

        pending.hide(7)
        pending.commit(7)
        runCurrent()

        assertEquals(emptySet<Long>(), pending.ids.value)
        assertEquals(1, errors.size)
    }

    @Test
    fun `leaving the app commits everything still waiting`() = runTest {
        val deleted = mutableListOf<Long>()
        val pending = PendingDeletes(backgroundScope) { deleted += it }

        pending.hide(7)
        pending.hide(8)
        pending.undo(8)
        pending.hide(9)
        pending.commitAll()
        runCurrent()

        assertEquals(listOf(7L, 9L), deleted.sorted())
    }
}
