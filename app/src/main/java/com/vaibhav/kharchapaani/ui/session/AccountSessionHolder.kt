package com.vaibhav.kharchapaani.ui.session

import com.vaibhav.kharchapaani.diagnostics.CrashReporting
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.vaibhav.kharchapaani.data.settings.GuestSession
import com.vaibhav.kharchapaani.data.settings.LedgerMode
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.data.sync.SyncScheduler
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The signed-in account's ledger database plus a [ViewModelStore] for everything that reads it.
 * Account-scoped ViewModels live in [viewModelStore], so ending the session clears them before
 * their database is closed.
 */
class AccountSession(val userId: String, val database: AppDatabase, val isLocal: Boolean = false) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
    private val registered = linkedSetOf<ViewModel>()
    private val jobs = mutableListOf<Job>()
    private val closingWork = mutableListOf<SessionClosingWork>()

    /** Register each account ViewModel when created; no inspection of ViewModelStore internals. */
    fun register(viewModel: ViewModel) {
        if (!registered.add(viewModel)) return
        viewModel.viewModelScope.coroutineContext[Job]?.let(jobs::add)
        (viewModel as? SessionClosingWork)?.let(closingWork::add)
    }

    /** Captured scope lifetimes, including after the store has been cleared. */
    val scopeJobs: List<Job> get() = jobs.toList()

    internal suspend fun awaitClosed() {
        scopeJobs.joinAll()
        closingWork.forEach { it.awaitSessionClosingWork() }
    }

    /** Invoked by the database owner barrier, never by joining the holder's own open job. */
    internal suspend fun beforeDatabaseClose(onRetired: () -> Unit = {}) {
        withContext(Dispatchers.Main.immediate) {
            onRetired()
            viewModelStore.clear()
        }
        awaitClosed()
    }

}

sealed interface AccountSessionState {
    data object None : AccountSessionState
    data object Opening : AccountSessionState
    data class Ready(val session: AccountSession) : AccountSessionState
    data class Failed(val userId: String, val isLocal: Boolean = false) : AccountSessionState
}

/**
 * Activity-scoped, so the session survives configuration changes but not a switch of account.
 */
class AccountSessionHolder(application: Application) : AndroidViewModel(application), SessionClosingWork {

    private val _state = MutableStateFlow<AccountSessionState>(AccountSessionState.None)
    val state: StateFlow<AccountSessionState> = _state.asStateFlow()

    private var activeUserId: String? = null
    private var activeIsLocal = false
    private val retiringSessions = mutableListOf<AccountSession>()
    private var openJob: Job? = null

    /** [userId] is the signed-in account, or null once signed out. */
    fun onAccountChanged(userId: String?) = changeSession(userId, isLocal = false)

    /** Choosing local mode is durable before its ledger is opened. */
    fun onLocalMode(guest: GuestSession = LedgerMode(getApplication()).useLocal()) =
        changeSession(guest.id, isLocal = true)

    private fun changeSession(userId: String?, isLocal: Boolean) {
        if (userId == activeUserId && isLocal == activeIsLocal) return
        // Stop the previous account's background sync; its queued rows stay on disk for next time.
        // Its notifications and shared files go: they belong to a ledger no longer on screen.
        activeUserId?.let {
            if (!activeIsLocal) SyncScheduler.cancelFor(getApplication(), it)
            AccountLeftovers.clear(getApplication())
        }
        activeUserId = userId
        activeIsLocal = isLocal
        val previousOpen = openJob
        previousOpen?.cancel()
        endSession()

        openJob = viewModelScope.launch {
            previousOpen?.join()
            // clear() cancels scopes; await their cleanup before the shared database is closed.
            awaitSessionClosingWork()
            if (userId == null) {
                LedgerDatabases.close()
                _state.value = AccountSessionState.None
            } else {
                _state.value = AccountSessionState.Opening
                _state.value = try {
                    val mode = LedgerMode(getApplication())
                    val isCurrent = {
                        activeUserId == userId && activeIsLocal == isLocal &&
                            (if (isLocal) mode.guestOrNull()?.id == userId else mode.choice == LedgerMode.Choice.CLOUD)
                    }
                    lateinit var session: AccountSession
                    LedgerDatabases.openOwned(getApplication(), userId, isLocal, isCurrent = isCurrent) { database ->
                        // The factory runs on Main while the database mutex still excludes closers.
                        session = AccountSession(userId, database, isLocal)
                        val owned = session
                        LedgerDatabases.CloseOwner {
                            owned.beforeDatabaseClose {
                                if ((_state.value as? AccountSessionState.Ready)?.session === owned) {
                                    // A background opener may retire this session before the next UI frame.
                                    activeUserId = null
                                    _state.value = AccountSessionState.None
                                }
                            }
                        }
                    }
                    if (!isLocal) SyncScheduler.schedulePeriodicSync(getApplication(), userId)
                    AccountSessionState.Ready(session)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't open the ledger for the signed-in account", e)
                    CrashReporting.recordProblem("Ledger didn't open for the signed-in account", e)
                    AccountSessionState.Failed(userId, isLocal)
                }
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun retryOpening() {
        val failed = _state.value as? AccountSessionState.Failed ?: return
        activeUserId = null
        changeSession(failed.userId, failed.isLocal)
    }

    private fun endSession() {
        (_state.value as? AccountSessionState.Ready)?.session?.let { session ->
            retiringSessions += session
            session.viewModelStore.clear()
        }
        _state.value = AccountSessionState.None
    }

    /** Also usable by an owner that clears this holder before explicitly closing its database. */
    override suspend fun awaitSessionClosingWork() {
        val sessions = retiringSessions.toList()
        sessions.forEach { it.awaitClosed() }
        retiringSessions.removeAll(sessions.toSet())
    }

    override fun onCleared() {
        // The database stays open for the process (the widget may use it); only ViewModels end.
        endSession()
    }

    private companion object {
        const val TAG = "AccountSessionHolder"
    }
}
