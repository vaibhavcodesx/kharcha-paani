package com.vaibhav.kharchapaani.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The user actions that write something, each of which runs at most once at a time. */
enum class Submit { SaveEntry, ImportStatement, RecordSettlement, RecordIou, CreateGroup, AddFriend, AddSubscription }

/**
 * One run at a time per action: a second tap while the first save is still writing does nothing.
 * A double tap logged the dinner twice, and on Create group made two shared groups on the server.
 * [inFlight] lets a sheet show its button as busy meanwhile.
 */
class Submissions(private val scope: CoroutineScope) {

    private val running = MutableStateFlow(emptySet<Submit>())
    val inFlight: StateFlow<Set<Submit>> = running.asStateFlow()

    /** Runs [block] unless [action] is already running; null when it was refused. */
    fun launch(action: Submit, block: suspend CoroutineScope.() -> Unit): Job? {
        var started = false
        running.update { current ->
            started = action !in current
            if (started) current + action else current
        }
        if (!started) return null
        return scope.launch {
            try {
                block()
            } finally {
                running.update { it - action }
            }
        }
    }
}
