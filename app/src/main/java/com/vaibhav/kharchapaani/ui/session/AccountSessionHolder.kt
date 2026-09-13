package com.vaibhav.kharchapaani.ui.session

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import com.vaibhav.kharchapaani.data.db.AppDatabase
import com.vaibhav.kharchapaani.data.db.LedgerDatabases
import com.vaibhav.kharchapaani.widget.SplitFlowWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The signed-in account's ledger database plus a [ViewModelStore] for everything that reads it.
 * Account-scoped ViewModels live in [viewModelStore], so ending the session clears them before
 * their database is closed.
 */
class AccountSession(val userId: String, val database: AppDatabase) : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

sealed interface AccountSessionState {
    data object None : AccountSessionState
    data object Opening : AccountSessionState
    data class Ready(val session: AccountSession) : AccountSessionState
    data class Failed(val userId: String) : AccountSessionState
}

/**
 * Activity-scoped, so the session survives configuration changes but not a switch of account.
 */
class AccountSessionHolder(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<AccountSessionState>(AccountSessionState.None)
    val state: StateFlow<AccountSessionState> = _state.asStateFlow()

    private var activeUserId: String? = null
    private var openJob: Job? = null

    /** [userId] is the signed-in account, or null once signed out. */
    fun onAccountChanged(userId: String?) {
        if (userId == activeUserId) return
        activeUserId = userId
        openJob?.cancel()
        endSession()

        openJob = viewModelScope.launch {
            if (userId == null) {
                LedgerDatabases.close()
                _state.value = AccountSessionState.None
            } else {
                _state.value = AccountSessionState.Opening
                _state.value = try {
                    AccountSessionState.Ready(AccountSession(userId, LedgerDatabases.open(getApplication(), userId)))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Couldn't open the ledger for the signed-in account", e)
                    AccountSessionState.Failed(userId)
                }
            }
            SplitFlowWidgetProvider.notifyWidgetUpdate(getApplication())
        }
    }

    fun retryOpening() {
        val userId = (_state.value as? AccountSessionState.Failed)?.userId ?: return
        activeUserId = null
        onAccountChanged(userId)
    }

    private fun endSession() {
        (_state.value as? AccountSessionState.Ready)?.session?.viewModelStore?.clear()
        _state.value = AccountSessionState.None
    }

    override fun onCleared() {
        // The database stays open for the process (the widget may use it); only ViewModels end.
        endSession()
    }

    private companion object {
        const val TAG = "AccountSessionHolder"
    }
}
