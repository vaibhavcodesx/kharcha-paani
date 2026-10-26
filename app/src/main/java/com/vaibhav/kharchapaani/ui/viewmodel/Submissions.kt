package com.vaibhav.kharchapaani.ui.viewmodel

import android.database.sqlite.SQLiteFullException
import com.vaibhav.kharchapaani.diagnostics.CrashReporting
import kotlinx.coroutines.CancellationException
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
 *
 * It's also the safety net (plan T1.8): whatever an action didn't handle itself (a full disk, a
 * ledger closed by signing out mid-save, a bug) ends in a message to the person through [onFailure]
 * and a report, instead of taking the app down. Cancellation still passes through.
 */
class Submissions(
    private val scope: CoroutineScope,
    private val onFailure: (message: String) -> Unit = {},
) {

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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CrashReporting.recordProblem("$action failed", e)
                onFailure(failureMessage(e))
            } finally {
                running.update { it - action }
            }
        }
    }

    companion object {
        /** What the person is told when a save fails in a way the action itself didn't expect. */
        fun failureMessage(e: Exception): String = when (e) {
            is SQLiteFullException -> "Couldn't save. Your phone may be out of space."
            // The ledger closed under the save: signing out, or switching accounts, mid-save.
            is IllegalStateException -> "Couldn't save just now. Try again."
            else -> "Something went wrong and that wasn't saved. Try again."
        }
    }
}
