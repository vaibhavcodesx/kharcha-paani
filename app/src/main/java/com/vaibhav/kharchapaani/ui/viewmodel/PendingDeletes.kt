package com.vaibhav.kharchapaani.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Deletes that wait out their Undo snackbar before touching the ledger. A row is hidden the moment
 * it's swiped away, and only deleted once the snackbar goes without Undo, so an undo never has to
 * re-create a row (and its sync state) that was already queued for deletion on the server.
 *
 * [scope] must outlive the screen: whatever is still waiting when the app closes is committed.
 */
class PendingDeletes(
    private val scope: CoroutineScope,
    private val delete: suspend (Long) -> Unit,
) {
    private val _ids = MutableStateFlow<Set<Long>>(emptySet())
    /** Rows to leave out of the feed. */
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    private val waiting = mutableSetOf<Long>()

    fun hide(id: Long) {
        synchronized(waiting) { waiting += id }
        _ids.update { it + id }
    }

    fun undo(id: Long) {
        val wasWaiting = synchronized(waiting) { waiting.remove(id) }
        if (wasWaiting) _ids.update { it - id }
    }

    fun commit(id: Long) {
        val wasWaiting = synchronized(waiting) { waiting.remove(id) }
        if (!wasWaiting) return
        scope.launch {
            // Stays hidden until the ledger has deleted it, so it doesn't flash back in between. A
            // refused delete (an archived group) brings it back.
            try {
                delete(id)
            } finally {
                _ids.update { it - id }
            }
        }
    }

    fun commitAll() {
        val all = synchronized(waiting) { waiting.toList() }
        all.forEach(::commit)
    }
}

/** The snackbar for a delete that can still be undone. */
data class UndoPrompt(val txId: Long, val message: String)
